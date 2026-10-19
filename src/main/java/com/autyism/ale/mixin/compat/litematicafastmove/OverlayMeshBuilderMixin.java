package com.autyism.ale.mixin.compat.litematicafastmove;

//? if >=1.21.11 <26.1 {
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.OrientationMarker;
import com.autyism.ale.render.WaterloggedMarker;
import com.mojang.blaze3d.vertex.BufferBuilder;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;
import fi.dy.masa.litematica.util.OverlayType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
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
 * 状态不对时的 D / W 字母同样写进这里的覆盖层（规则同 ChunkRendererSchematicVboMixin）。
 * F（需要填充的容器）要读投影里容器的内容，这里拿不到，这些投影上不画。
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

    // TAIL：只在这一格的覆盖层真的画了的时候（这种覆盖层开着、没有被剔除），和 Litematica 的 renderOverlay 一样
    @Inject(method = "addOverlay", at = @At("TAIL"), require = 0)
    private void ale$letters(BlockPos pos, BlockState stateSchematic, BlockState stateClient, boolean missing, CallbackInfo ci) {
        if (this.getOverlayType(stateSchematic, stateClient) != OverlayType.WRONG_STATE) return;
        List<float[][]> glyphs = new ArrayList<>(2);
        int[] colors = new int[2];
        if (AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, stateClient)) {
            colors[glyphs.size()] = AleConfigs.Generic.ORIENTATION_MARKER_COLOR.getIntegerValue();
            glyphs.add(OrientationMarker.STROKES);
        }
        if (AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.waterloggedDiffers(stateSchematic, stateClient)) {
            colors[glyphs.size()] = AleConfigs.Generic.WATERLOGGED_MARKER_COLOR.getIntegerValue();
            glyphs.add(WaterloggedMarker.STROKES);
        }
        if (glyphs.isEmpty()) return;
        if (AleConfigs.Generic.MARKER_PRIORITY.getBooleanValue() && glyphs.size() > 1) {
            glyphs.subList(1, glyphs.size()).clear();
        }
        // 它的网格按 16×16×16 的区段存，顶点用区段内的坐标
        BlockPos rel = new BlockPos(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        WaterloggedMarker.emitRow(this.buffers.apply(OverlayRenderType.QUAD), rel, glyphs, colors);
    }
    //?}
}
