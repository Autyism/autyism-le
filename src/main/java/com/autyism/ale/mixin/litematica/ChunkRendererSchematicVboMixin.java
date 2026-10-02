package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.WaterloggedMarker;
import com.mojang.blaze3d.vertex.BufferBuilder;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
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
    private BufferBuilder preRenderOverlay(ChunkRenderDispatcherBuffers pack, OverlayRenderType type) {
        throw new AssertionError();
    }

    @Shadow
    protected abstract BlockPos.MutableBlockPos getChunkRelativePosition(BlockPos pos);

    @Inject(method = "renderOverlay", at = @At("TAIL"))
    private void ale$waterloggedMarker(OverlayType type, BlockPos pos, BlockState stateSchematic, boolean missing,
                                       ChunkRenderDataSchematic data, ChunkMeshDataSchematic meshData,
                                       ChunkRenderDispatcherBuffers pack, CallbackInfo ci) {
        if (type != OverlayType.WRONG_STATE || !AleConfigs.Generic.WATERLOGGED_MARKER.getBooleanValue()) return;
        BlockState client = this.clientWorldView.getBlockState(pos);
        if (!WaterloggedMarker.onlyWaterloggedDiffers(stateSchematic, client)) return;
        BufferBuilder buffer = this.preRenderOverlay(pack, OverlayRenderType.QUAD);
        if (!data.isOverlayTypeStarted(OverlayRenderType.QUAD)) {
            ((ChunkRenderDataSchematicInvoker) data).ale$setOverlayTypeStarted(OverlayRenderType.QUAD);
        }
        WaterloggedMarker.emit(buffer, this.getChunkRelativePosition(pos).immutable(),
                AleConfigs.Generic.WATERLOGGED_MARKER_COLOR.getIntegerValue());
    }
}
