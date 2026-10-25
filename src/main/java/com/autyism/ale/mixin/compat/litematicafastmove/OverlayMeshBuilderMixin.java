package com.autyism.ale.mixin.compat.litematicafastmove;

//? if >=1.21.11 <26.1 {
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.OrientationMarker;
import com.autyism.ale.render.WaterloggedMarker;
import com.mojang.blaze3d.vertex.BufferBuilder;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;
import fi.dy.masa.litematica.util.OverlayType;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * 装了 Litefastica（快速移动投影）时：它接管的投影由它自己的网格来画，覆盖层也在这里生成，不经过 Litematica 的区块渲染。
 * 状态不对时的 D / W 字母和需要填充的容器的 F 同样写进这里的覆盖层（规则同 ChunkRendererSchematicVboMixin）。
 * F 要的投影容器内容从投影文件里读（见 FastMoveFill）。
 * 它更新后改了名就不注入（require = 0），只是少了字母，不会出错。
 */
@Mixin(targets = "ml.mypals.litematicafastmove.mesh.OverlayMeshBuilder", remap = false)
public abstract class OverlayMeshBuilderMixin {
    //? if >=1.21.11 <26.1 {
    @Shadow
    @Final
    private Function<OverlayRenderType, BufferBuilder> buffers;

    @Shadow
    abstract OverlayType getOverlayType(BlockState stateSchematic, BlockState stateClient);

    @Shadow
    private static Color4f getOverlayColor(OverlayType type) {
        throw new AssertionError();
    }

    @Shadow
    private boolean shouldCullOverlayPos(BlockPos pos, BlockState stateSchematic, BlockState stateClient) {
        throw new AssertionError();
    }

    // 每一格都会经过这里（包括状态没错、不画覆盖层的格子），所以挂在所有的 return 上
    @Inject(method = "addOverlay", at = @At("RETURN"), require = 0)
    private void ale$letters(BlockPos pos, BlockState stateSchematic, BlockState stateClient, boolean missing, CallbackInfo ci) {
        // 它的网格按 16×16×16 的区段存，顶点用区段内的坐标
        BlockPos rel = new BlockPos(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        OverlayType type = this.getOverlayType(stateSchematic, stateClient);
        // 黄色的状态错误覆盖层真的画了（这种覆盖层开着、没有被剔除）时才画 D / W，和 Litematica 的 renderOverlay 一样
        if (type == OverlayType.WRONG_STATE && getOverlayColor(type) != null && !this.shouldCullOverlayPos(pos, stateSchematic, stateClient)) {
            List<float[][]> glyphs = new ArrayList<>(3);
            int[] colors = new int[3];
            if (AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, stateClient)) {
                colors[glyphs.size()] = AleConfigs.Generic.ORIENTATION_MARKER_COLOR.getIntegerValue();
                glyphs.add(OrientationMarker.STROKES);
            }
            if (AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.waterloggedDiffers(stateSchematic, stateClient)) {
                colors[glyphs.size()] = AleConfigs.Generic.WATERLOGGED_MARKER_COLOR.getIntegerValue();
                glyphs.add(WaterloggedMarker.STROKES);
            }
            if (!glyphs.isEmpty()) {
                // 需要填充的容器同时状态也不对时，F 和 D / W 一起并排画
                if (this.ale$needsFilling(pos, stateSchematic)) {
                    colors[glyphs.size()] = AleConfigs.Generic.FILL_MARKER_COLOR.getIntegerValue();
                    glyphs.add(com.autyism.ale.render.FillMarker.STROKES);
                }
                if (AleConfigs.Generic.MARKER_PRIORITY.getBooleanValue() && glyphs.size() > 1) {
                    glyphs.subList(1, glyphs.size()).clear();
                }
                WaterloggedMarker.emitRow(this.buffers.apply(OverlayRenderType.QUAD), rel, glyphs, colors);
                return;
            }
        }
        this.ale$fillMarker(pos, rel, stateSchematic);
    }

    /** 没有 D / W 的格子：投影里装着东西的容器单独画 F。大箱子两半都没有 D / W 时只在中间画一个 */
    @Unique
    private void ale$fillMarker(BlockPos pos, BlockPos rel, BlockState stateSchematic) {
        if (!AleConfigs.Generic.FILL_MARKER.getBooleanValue() || !stateSchematic.hasBlockEntity()) return;
        int color = AleConfigs.Generic.FILL_MARKER_COLOR.getIntegerValue();
        if (stateSchematic.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && stateSchematic.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            net.minecraft.core.Direction toOther = net.minecraft.world.level.block.ChestBlock.getConnectedDirection(stateSchematic);
            BlockPos other = pos.relative(toOther);
            BlockState otherSchematic = ale$schematicState(other);
            if (otherSchematic != null && otherSchematic.getBlock() == stateSchematic.getBlock() && !this.ale$hasLetters(other, otherSchematic)) {
                // 两半都要算（顺便都记成要检查的容器）
                boolean need = this.ale$needsFilling(pos, stateSchematic) | this.ale$needsFilling(other, otherSchematic);
                if (!need || (toOther != net.minecraft.core.Direction.EAST && toOther != net.minecraft.core.Direction.SOUTH)) return;
                WaterloggedMarker.emitDouble(this.buffers.apply(OverlayRenderType.QUAD), rel, color, com.autyism.ale.render.FillMarker.STROKES, toOther);
                return;
            }
        }
        if (!this.ale$needsFilling(pos, stateSchematic)) return;
        WaterloggedMarker.emit(this.buffers.apply(OverlayRenderType.QUAD), rel, color, com.autyism.ale.render.FillMarker.STROKES);
    }

    /** 投影里另一格的方块（它的投影视图，网格坐标） */
    @Unique
    private BlockState ale$schematicState(BlockPos pos) {
        com.autyism.ale.render.FastMoveFill.Build build = com.autyism.ale.render.FastMoveFill.build(this);
        return build == null ? null : build.view().getBlockState(pos);
    }

    /** 这一格会画 D 或 W（世界里是同一种方块、但朝向或含水不对；网格坐标） */
    @Unique
    private boolean ale$hasLetters(BlockPos pos, BlockState stateSchematic) {
        com.autyism.ale.render.FastMoveFill.Build build = com.autyism.ale.render.FastMoveFill.build(this);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (build == null || level == null) return false;
        BlockState client = level.getBlockState(build.toWorld(pos));
        if (client.getBlock() != stateSchematic.getBlock() || client == stateSchematic) return false;
        return (AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, client))
                || (AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.waterloggedDiffers(stateSchematic, client));
    }

    /** 投影里这一格是装着东西、要填充的容器（世界里已经确认内容一样的不算；网格坐标） */
    @Unique
    private boolean ale$needsFilling(BlockPos pos, BlockState stateSchematic) {
        if (!AleConfigs.Generic.FILL_MARKER.getBooleanValue() || !stateSchematic.hasBlockEntity()) return false;
        com.autyism.ale.render.FastMoveFill.Build build = com.autyism.ale.render.FastMoveFill.build(this);
        if (build == null) return false;
        BlockPos world = build.toWorld(pos);
        net.minecraft.world.level.block.entity.BlockEntity be = com.autyism.ale.render.FastMoveFill.schematicBlockEntity(build, pos, world, stateSchematic);
        if (!com.autyism.ale.render.FillMarker.needsFilling(be)) return false;
        if (com.autyism.ale.verifier.ContainerVerifier.isKnownToMatch(world)) return false;
        // 能读到世界里的内容时，主线程会去查它填好了没有（填好了 F 自己消失）
        com.autyism.ale.verifier.ContainerVerifier.noteFillCandidate(world, stateSchematic.getBlock(), (net.minecraft.world.Container) be);
        return true;
    }
    //?}
}
