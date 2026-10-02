package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifier;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.OverlayRenderer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.util.data.Color4f;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * 需求 9b：用洋红色框标出容器内容不一致的位置（与验证器的错误标记一起显示）。
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class OverlayRendererMixin {
    private static final Color4f ALE_CONTAINER_COLOR = Color4f.fromColor(0xFFFF30FF);

    @Inject(method = "renderSchematicVerifierMismatches", at = @At("TAIL"))
    private void ale$renderContainerMismatches(Matrix4f posMatrix, Matrix4f projMatrix, ProfilerFiller profiler, CallbackInfo ci) {
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        if (placement == null || !placement.hasVerifier()) return;
        Set<BlockPos> positions = ContainerVerifier.getMismatches(placement.getSchematicVerifier());
        if (positions.isEmpty()) return;
        RenderContext ctx = new RenderContext(() -> "autyism-le:container_mismatches", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
        BufferBuilder buffer = ctx.getBuilder();
        for (BlockPos pos : positions) {
            fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, ALE_CONTAINER_COLOR, 0.004, 3.0f, buffer);
        }
        try {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                ctx.draw(meshData, false, true);
                meshData.close();
            }
            ctx.reset();
            ctx.close();
        } catch (Exception ignored) {
        }
    }
}
