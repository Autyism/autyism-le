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
 * 需求 6：含水不对时在黄色错误状态标记上再画蓝色的 W，朝向不对时画红色的 D；两个都不对时并排画，别的属性也不对时照样画。
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
        // 每个不对的属性各自判断，别的属性也不对时照样画：朝向不对画红色 D，含水不对画蓝色 W，两个都不对时并排画
        boolean wrongFacing = AleConfigs.Generic.ORIENTATION_MARKER.getBooleanValue() && OrientationMarker.orientationDiffers(stateSchematic, client);
        boolean wrongWater = AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue() && WaterloggedMarker.waterloggedDiffers(stateSchematic, client);
        if (!wrongFacing && !wrongWater) return;
        //? if >=1.21.11 {
        BufferBuilder buffer = this.preRenderOverlay(pack, OverlayRenderType.QUAD);
        //?} else
        //BufferBuilder buffer = this.preRenderOverlay(OverlayRenderType.QUAD, allocators);
        if (!data.isOverlayTypeStarted(OverlayRenderType.QUAD)) {
            ((ChunkRenderDataSchematicInvoker) data).ale$setOverlayTypeStarted(OverlayRenderType.QUAD);
        }
        BlockPos rel = this.getChunkRelativePosition(pos).immutable();
        int facingColor = AleConfigs.Generic.ORIENTATION_MARKER_COLOR.getIntegerValue();
        int waterColor = AleConfigs.Generic.WATERLOGGED_MARKER_COLOR.getIntegerValue();
        if (wrongFacing && wrongWater) {
            WaterloggedMarker.emitHalf(buffer, rel, facingColor, OrientationMarker.STROKES, true);
            WaterloggedMarker.emitHalf(buffer, rel, waterColor, WaterloggedMarker.STROKES, false);
        } else if (wrongFacing) {
            WaterloggedMarker.emit(buffer, rel, facingColor, OrientationMarker.STROKES);
        } else {
            WaterloggedMarker.emit(buffer, rel, waterColor, WaterloggedMarker.STROKES);
        }
    }
}
