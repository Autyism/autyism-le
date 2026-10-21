package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.OrientationMarker;
import com.autyism.ale.render.WaterloggedMarker;
import com.mojang.blaze3d.vertex.BufferBuilder;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
// 1.21.11 起 Litematica 的区块网格数据和缓冲改了结构，之前是 BufferAllocatorCache
//? if >=1.21.11 {
import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
//?} else
//import fi.dy.masa.litematica.render.schematic.BufferAllocatorCache;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;
import fi.dy.masa.litematica.util.OverlayType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 需求 6：含水不对时在黄色错误状态标记上再画蓝色的 W，朝向不对时画红色的 D；别的属性也不对时照样画。
 * 投影里装着东西的容器画绿色的 F（需要填充）。几个字母同时要画时：默认只画最重要的一个（D > W > F，“标记只显示一个”），关掉后并排画。
 * 字母和 Litematica 的覆盖层一起写进区块的渲染数据，只在区块重建时计算一次。
 */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboMixin {
    @Shadow
    protected ChunkCacheSchematic clientWorldView;

    @Shadow
    protected ChunkCacheSchematic schematicWorldView;

    /** 这一格的字母已经在 renderOverlay 里画过（各个重建线程各记各的） */
    @Unique
    private static final ThreadLocal<long[]> ALE_MARKED = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE});

    @Shadow
    //? if >=1.21.11 {
    private BufferBuilder preRenderOverlay(ChunkRenderDispatcherBuffers pack, OverlayRenderType type) {
    //?} else
    //private BufferBuilder preRenderOverlay(OverlayRenderType type, BufferAllocatorCache allocators) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract BlockPos.MutableBlockPos getChunkRelativePosition(BlockPos pos);

    @Inject(method = "renderOverlay", at = @At("TAIL"))
    //? if >=1.21.11 {
    private void ale$waterloggedMarker(OverlayType type, BlockPos pos, BlockState stateSchematic, boolean missing,
                                       ChunkRenderDataSchematic data, ChunkMeshDataSchematic meshData,
                                       ChunkRenderDispatcherBuffers pack, CallbackInfo ci) {
    //?} else {
    /*private void ale$waterloggedMarker(OverlayType type, BlockPos pos, BlockState stateSchematic, boolean missing,
                                       ChunkRenderDataSchematic data, BufferAllocatorCache allocators, CallbackInfo ci) {
    *///?}
        if (type != OverlayType.WRONG_STATE) return;
        BlockState client = this.clientWorldView.getBlockState(pos);
        // 每个不对的属性各自判断，别的属性也不对时照样画：朝向不对画红色 D，含水不对画蓝色 W
        List<float[][]> glyphs = new ArrayList<>(3);
        int[] colors = new int[3];
        if (AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, client)) {
            colors[glyphs.size()] = AleConfigs.Generic.ORIENTATION_MARKER_COLOR.getIntegerValue();
            glyphs.add(OrientationMarker.STROKES);
        }
        if (AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.waterloggedDiffers(stateSchematic, client)) {
            colors[glyphs.size()] = AleConfigs.Generic.WATERLOGGED_MARKER_COLOR.getIntegerValue();
            glyphs.add(WaterloggedMarker.STROKES);
        }
        if (glyphs.isEmpty()) return;
        // 需要填充的容器同时状态也不对时，F 和 D / W 一起并排画
        if (ale$needsFilling(pos, stateSchematic)) {
            colors[glyphs.size()] = AleConfigs.Generic.FILL_MARKER_COLOR.getIntegerValue();
            glyphs.add(com.autyism.ale.render.FillMarker.STROKES);
        }
        ALE_MARKED.get()[0] = pos.asLong();
        // “标记只显示一个”：列表的顺序就是重要程度（D、W、F），只留第一个
        if (AleConfigs.Generic.MARKER_PRIORITY.getBooleanValue() && glyphs.size() > 1) {
            glyphs.subList(1, glyphs.size()).clear();
        }
        //? if >=1.21.11 {
        BufferBuilder buffer = this.preRenderOverlay(pack, OverlayRenderType.QUAD);
        //?} else
        //BufferBuilder buffer = this.preRenderOverlay(OverlayRenderType.QUAD, allocators);
        if (!data.isOverlayTypeStarted(OverlayRenderType.QUAD)) {
            ((ChunkRenderDataSchematicInvoker) data).ale$setOverlayTypeStarted(OverlayRenderType.QUAD);
        }
        WaterloggedMarker.emitRow(buffer, this.getChunkRelativePosition(pos).immutable(), glyphs, colors);
    }

    // 其他格子（状态没错、缺失、错误方块……）：投影里装着东西的容器单独画 F。26.1 起 Litematica 这个方法的参数换了
    //? if >=26.1 {
    /*@Inject(method = "renderBlocksAndOverlay", at = @At("RETURN"))
    private void ale$fillMarker(fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic blockRenderer,
                                fi.dy.masa.litematica.render.schematic.FluidModelRendererSchematic fluidRenderer, BlockPos pos,
                                ChunkRenderDataSchematic data, ChunkMeshDataSchematic chunkMeshData, ChunkRenderDispatcherBuffers pack,
                                fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic output, net.minecraft.world.phys.Vec3 offset,
                                net.minecraft.client.renderer.chunk.VisGraph visGraph, CallbackInfo ci) {
        this.ale$drawFillMarker(pos, data, pack);
    }
    *///?} elif >=1.21.11 {
    @Inject(method = "renderBlocksAndOverlay", at = @At("RETURN"))
    private void ale$fillMarker(BlockPos pos, ChunkRenderDataSchematic data, ChunkMeshDataSchematic chunkMeshData,
                                ChunkRenderDispatcherBuffers pack, java.util.Set<net.minecraft.client.renderer.chunk.ChunkSectionLayer> usedBlockLayers,
                                java.util.Set<net.minecraft.client.renderer.rendertype.RenderType> usedRenderLayers,
                                com.mojang.blaze3d.vertex.PoseStack matrixStack, net.minecraft.client.renderer.chunk.VisGraph visGraph, CallbackInfo ci) {
        this.ale$drawFillMarker(pos, data, pack);
    }
    //?} elif >=1.21.6 {
    /*@Inject(method = "renderBlocksAndOverlay", at = @At("RETURN"))
    private void ale$fillMarker(BlockPos pos, ChunkRenderDataSchematic data, BufferAllocatorCache allocators,
                                java.util.Set<net.minecraft.client.renderer.chunk.ChunkSectionLayer> usedBlockLayers,
                                java.util.Set<net.minecraft.client.renderer.rendertype.RenderType> usedRenderLayers,
                                com.mojang.blaze3d.vertex.PoseStack matrixStack, CallbackInfo ci) {
        this.ale$drawFillMarker(pos, data, allocators);
    }
    *///?} else {
    /*@Inject(method = "renderBlocksAndOverlay", at = @At("RETURN"))
    private void ale$fillMarker(BlockPos pos, ChunkRenderDataSchematic data, BufferAllocatorCache allocators,
                                java.util.Set<net.minecraft.client.renderer.rendertype.RenderType> usedLayers,
                                com.mojang.blaze3d.vertex.PoseStack matrixStack, CallbackInfo ci) {
        this.ale$drawFillMarker(pos, data, allocators);
    }
    *///?}

    // 画字母的那一层（四边形覆盖层）的缓冲
    //? if >=1.21.11 {
    @Unique
    private BufferBuilder ale$quads(ChunkRenderDispatcherBuffers pack) {
        return this.preRenderOverlay(pack, OverlayRenderType.QUAD);
    }
    //?} else {
    /*@Unique
    private BufferBuilder ale$quads(BufferAllocatorCache allocators) {
        return this.preRenderOverlay(OverlayRenderType.QUAD, allocators);
    }
    *///?}

    @Unique
    //? if >=1.21.11 {
    private void ale$drawFillMarker(BlockPos pos, ChunkRenderDataSchematic data, ChunkRenderDispatcherBuffers pack) {
    //?} else
    //private void ale$drawFillMarker(BlockPos pos, ChunkRenderDataSchematic data, BufferAllocatorCache pack) {
        long[] marked = ALE_MARKED.get();
        if (marked[0] == pos.asLong()) {
            marked[0] = Long.MIN_VALUE;
            return;
        }
        if (!fi.dy.masa.litematica.config.Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue()) return;
        BlockState stateSchematic = this.schematicWorldView.getBlockState(pos);
        // 大箱子：两半看成一个长方体，只在中间画一个 F（两半都没有 D / W 时；有的话各画各的，免得叠在一起）。
        // 只由另一半在东边 / 南边的那一半来画
        if (stateSchematic.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && stateSchematic.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            net.minecraft.core.Direction toOther = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(stateSchematic);
            BlockPos other = pos.relative(toOther);
            BlockState otherSchematic = this.schematicWorldView.getBlockState(other);
            if (otherSchematic.getBlock() == stateSchematic.getBlock() && !ale$hasLetters(other, otherSchematic)) {
                // 两半都要算（顺便都记成要检查的容器）
                boolean need = ale$needsFilling(pos, stateSchematic) | ale$needsFilling(other, otherSchematic);
                if (!need || (toOther != net.minecraft.core.Direction.EAST && toOther != net.minecraft.core.Direction.SOUTH)) return;
                BufferBuilder buffer = this.ale$quads(pack);
                if (!data.isOverlayTypeStarted(OverlayRenderType.QUAD)) {
                    ((ChunkRenderDataSchematicInvoker) data).ale$setOverlayTypeStarted(OverlayRenderType.QUAD);
                }
                WaterloggedMarker.emitDouble(buffer, this.getChunkRelativePosition(pos).immutable(),
                        AleConfigs.Generic.FILL_MARKER_COLOR.getIntegerValue(), com.autyism.ale.render.FillMarker.STROKES, toOther);
                return;
            }
        }
        if (!ale$needsFilling(pos, stateSchematic)) return;
        BufferBuilder buffer = this.ale$quads(pack);
        if (!data.isOverlayTypeStarted(OverlayRenderType.QUAD)) {
            ((ChunkRenderDataSchematicInvoker) data).ale$setOverlayTypeStarted(OverlayRenderType.QUAD);
        }
        WaterloggedMarker.emit(buffer, this.getChunkRelativePosition(pos).immutable(),
                AleConfigs.Generic.FILL_MARKER_COLOR.getIntegerValue(), com.autyism.ale.render.FillMarker.STROKES);
    }

    /** 这一格会画 D 或 W（世界里是同一种方块、但朝向或含水不对） */
    @Unique
    private boolean ale$hasLetters(BlockPos pos, BlockState stateSchematic) {
        BlockState client = this.clientWorldView.getBlockState(pos);
        if (client.getBlock() != stateSchematic.getBlock() || client == stateSchematic) return false;
        return (AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, client))
                || (AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.waterloggedDiffers(stateSchematic, client));
    }

    /** 投影里这一格是装着东西、要填充的容器（世界里已经确认内容一样的不算） */
    @Unique
    private boolean ale$needsFilling(BlockPos pos, BlockState stateSchematic) {
        if (!AleConfigs.Generic.FILL_MARKER.getBooleanValue() || !stateSchematic.hasBlockEntity()) return false;
        if (!com.autyism.ale.render.FillMarker.needsFilling(this.schematicWorldView.getBlockEntity(pos))) return false;
        if (com.autyism.ale.verifier.ContainerVerifier.isKnownToMatch(pos)) return false;
        // 能读到世界里的内容时，主线程会去查它填好了没有（填好了 F 自己消失）
        com.autyism.ale.verifier.ContainerVerifier.noteFillCandidate(pos);
        return true;
    }
}
