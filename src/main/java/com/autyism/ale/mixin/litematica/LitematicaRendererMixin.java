package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.GlassRenderState;
import com.mojang.blaze3d.textures.GpuSampler;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 12：本帧已经由 ALE 在玻璃之前画过的部分，Litematica 原本的绘制调用跳过（避免画两遍）。
 */
@Mixin(value = LitematicaRenderer.class, remap = false)
public abstract class LitematicaRendererMixin {
    @Inject(method = "piecewiseDrawBlockLayerGroup", at = @At("HEAD"), cancellable = true)
    // 26.3 起 Litematica 改成 (渲染目标, 方块层组)
    //? if >=26.3 {
    /*private void ale$skipLateTranslucent(com.mojang.blaze3d.pipeline.RenderTarget target, ChunkSectionLayerGroup group, CallbackInfo ci) {
    *///?} else
    private void ale$skipLateTranslucent(ChunkSectionLayerGroup group, @Nullable GpuSampler sampler, CallbackInfo ci) {
        if (group == ChunkSectionLayerGroup.TRANSLUCENT && !GlassRenderState.drawingEarly && GlassRenderState.translucentDrawnEarly
                && AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSchematicOverlays", at = @At("HEAD"), cancellable = true)
    private void ale$skipLateOverlays(Camera camera, ProfilerFiller profiler, CallbackInfo ci) {
        if (!GlassRenderState.drawingEarly && GlassRenderState.overlaysDrawnEarly
                && AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) {
            ci.cancel();
        }
    }
}
