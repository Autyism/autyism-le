package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.GlassRenderState;
//? if >=1.21.11
import com.mojang.blaze3d.textures.GpuSampler;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import net.minecraft.client.Camera;
//? if >=1.21.6
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
    // 1.21.5 还没有方块层组，Litematica 的半透明层是单独的方法
    //? if >=1.21.6 {
    @Inject(method = "piecewiseDrawBlockLayerGroup", at = @At("HEAD"), cancellable = true)
    //?} else
    //@Inject(method = "piecewiseRenderTranslucent", at = @At("HEAD"), cancellable = true)
    // 26.3 起 Litematica 改成 (渲染目标, 方块层组)；1.21.11 之前没有采样器参数
    //? if >=26.3 {
    /*private void ale$skipLateTranslucent(com.mojang.blaze3d.pipeline.RenderTarget target, ChunkSectionLayerGroup group, CallbackInfo ci) {
    *///?} elif >=1.21.11 {
    private void ale$skipLateTranslucent(ChunkSectionLayerGroup group, @Nullable GpuSampler sampler, CallbackInfo ci) {
    //?} elif >=1.21.6 {
    /*private void ale$skipLateTranslucent(ChunkSectionLayerGroup group, CallbackInfo ci) {
    *///?} else {
    /*private void ale$skipLateTranslucent(org.joml.Matrix4f modelView, org.joml.Matrix4f projection, ProfilerFiller profiler, CallbackInfo ci) {
    *///?}
        //? if >=1.21.6
        if (group != ChunkSectionLayerGroup.TRANSLUCENT) return;
        if (!GlassRenderState.drawingEarly && GlassRenderState.translucentDrawnEarly
                && AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) {
            ci.cancel();
            return;
        }
        GlassRenderState.translucentDrawnThisFrame = true;
    }

    @Inject(method = "renderSchematicOverlays", at = @At("HEAD"), cancellable = true)
    private void ale$skipLateOverlays(Camera camera, ProfilerFiller profiler, CallbackInfo ci) {
        if (!GlassRenderState.drawingEarly && GlassRenderState.overlaysDrawnEarly
                && AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) {
            ci.cancel();
        }
    }
}
