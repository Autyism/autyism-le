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

    // ---------------- 需求 5a：流体与实体的信息对比 ----------------

    /** 方块信息检测也命中投影里的流体（水源、岩浆源） */
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderHoverInfo",
            at = @At(value = "INVOKE", remap = true, target = "Lfi/dy/masa/litematica/util/RayTraceUtils;getGenericTrace(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;DZZZ)Lfi/dy/masa/litematica/util/RayTraceUtils$RayTraceWrapper;"),
            index = 4)
    private boolean ale$targetFluids(boolean targetFluids) {
        return targetFluids || com.autyism.ale.config.AleConfigs.Generic.INFO_FLUIDS_ENTITIES.getBooleanValue();
    }

    /** 看向投影实体（比方块更近）时，显示实体的对比框，替代方块信息 */
    @Inject(method = "renderHoverInfo", at = @At("HEAD"), cancellable = true)
    private void ale$entityInfo(fi.dy.masa.malilib.render.GuiContext ctx, ProfilerFiller profiler, CallbackInfo ci) {
        if (!com.autyism.ale.config.AleConfigs.Generic.INFO_FLUIDS_ENTITIES.getBooleanValue()) return;
        if (!fi.dy.masa.litematica.config.Hotkeys.RENDER_INFO_OVERLAY.getKeybind().isKeybindHeld()) return;
        if (!fi.dy.masa.litematica.config.Configs.InfoOverlays.BLOCK_INFO_OVERLAY_ENABLED.getBooleanValue()) return;
        net.minecraft.world.entity.Entity target = com.autyism.ale.render.EntityInfoOverlay.findTargetedSchematicEntity();
        if (target == null) return;
        com.autyism.ale.render.EntityInfoOverlay.render(ctx, target);
        ci.cancel();
    }

    @Inject(method = "renderSchematicVerifierMismatches", at = @At("TAIL"))
    private void ale$renderContainerMismatches(Matrix4f posMatrix, Matrix4f projMatrix, ProfilerFiller profiler, CallbackInfo ci) {
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        if (placement == null || !placement.hasVerifier()) return;
        Set<BlockPos> positions = ContainerVerifier.getMismatches(placement.getSchematicVerifier());
        if (positions.isEmpty()) return;
        //? if >=26.2 {
        /*RenderContext ctx = new RenderContext(() -> "autyism-le:container_mismatches", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0);
        *///?} else
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
