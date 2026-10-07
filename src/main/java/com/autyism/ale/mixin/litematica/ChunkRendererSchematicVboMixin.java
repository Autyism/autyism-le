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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 6：只差含水时，在黄色错误状态标记上再画蓝色的 W。
 */
@Mixin(value = ChunkRendererSchematicVbo.class, remap = false)
public abstract class ChunkRendererSchematicVboMixin {
    @Shadow
    protected ChunkCacheSchematic clientWorldView;

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
        int color;
        float[][] letter;
        if (AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, client)) {
            // 朝向不对：红色 D（比只差含水更重要，两者都有时画 D）
            color = AleConfigs.Generic.ORIENTATION_MARKER_COLOR.getIntegerValue();
            letter = OrientationMarker.STROKES;
        } else if (AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.onlyWaterloggedDiffers(stateSchematic, client)) {
            color = AleConfigs.Generic.WATERLOGGED_MARKER_COLOR.getIntegerValue();
            letter = WaterloggedMarker.STROKES;
        } else {
            return;
        }
        //? if >=1.21.11 {
        BufferBuilder buffer = this.preRenderOverlay(pack, OverlayRenderType.QUAD);
        //?} else
        //BufferBuilder buffer = this.preRenderOverlay(OverlayRenderType.QUAD, allocators);
        if (!data.isOverlayTypeStarted(OverlayRenderType.QUAD)) {
            ((ChunkRenderDataSchematicInvoker) data).ale$setOverlayTypeStarted(OverlayRenderType.QUAD);
        }
        WaterloggedMarker.emit(buffer, this.getChunkRelativePosition(pos).immutable(), color, letter);
    }
}
