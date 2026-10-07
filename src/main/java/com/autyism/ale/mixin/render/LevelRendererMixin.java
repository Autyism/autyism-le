package com.autyism.ale.mixin.render;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.GlassRenderState;
import com.mojang.blaze3d.textures.GpuSampler;
import fi.dy.masa.litematica.render.LitematicaRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 12：在原版绘制半透明层（玻璃）之前，先画投影的半透明方块和错误标记。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private GpuSampler chunkLayerSampler;

    //? if >=26.3 {
    /*@Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.renderer.LevelTargetBundle targets;
    *///?}

    //? if >=26.2 {
    /*@Inject(method = "render", at = @At("HEAD"))
    *///?} else
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ale$newFrame(CallbackInfo ci) {
        GlassRenderState.newFrame();
    }

    //? if >=26.3 {
    /*// 26.3：主渲染拆成 executeSolid / executeClassicTransparency / executeOit。
    // 顺序无关透明（改进的透明度）时挂在 executeOit 开头，Litematica 也在这里画；经典透明见下面的 ale$classicTransparency
    @Inject(method = "executeOit", at = @At("HEAD"))
    *///?} elif >=26.1 {
    /*// 26.1+：addMainPass 里的主渲染 lambda（不混淆，名字就是 lambda$addMainPass$0）
    @Inject(method = "lambda$addMainPass$0", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
                    ordinal = 1))
    *///?} else {
    // method_62214 是 addMainPass 里的主渲染 lambda（开发环境与正式环境同名）
    @Inject(method = "method_62214", remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
                    ordinal = 1))
    //?}
    private void ale$drawSchematicBeforeTranslucent(CallbackInfo ci) {
        if (!AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) return;
        ale$drawEarly();
    }

    //? if >=26.3 {
    /*// 26.3 经典透明：原版在同一个渲染通道里先画实心、再画半透明（玻璃），中间不能另开通道。
    // 所以先结束这个通道，投影的半透明方块和标记用 Litematica 自己的通道画完，再照原版的设置开一个新通道，交给原版画半透明。
    // 原版随后还会再关一次原来的通道，close 只生效一次，不会出错
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "lambda$addMainPass$0", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void ale$classicTransparency(LevelRenderer self, net.minecraft.client.renderer.chunk.ChunkSectionsToRender chunks,
                                         net.minecraft.client.renderer.feature.FeatureRenderDispatcher.PreparedFrame frame,
                                         com.mojang.renderpearl.api.commands.RenderPass pass,
                                         com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        if (!AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) {
            original.call(self, chunks, frame, pass);
            return;
        }
        pass.close();
        ale$drawEarly();
        com.mojang.blaze3d.pipeline.RenderTarget target = this.targets.main.get();
        try (com.mojang.renderpearl.api.commands.RenderPass translucent = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Main translucent", target.getColorTextureView(), java.util.Optional.empty(),
                        target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
            com.mojang.blaze3d.systems.RenderSystem.bindDefaultUniforms(translucent);
            original.call(self, chunks, frame, translucent);
        }
    }
    *///?}

    /** 投影的半透明方块和标记先画（之后原版才画玻璃），并记下来，让 Litematica 本帧稍后的同样绘制跳过 */
    private void ale$drawEarly() {
        GlassRenderState.drawingEarly = true;
        try {
            var profiler = Profiler.get();
            profiler.push("ale_schematic_before_translucent");
            // 26.3 顺序无关透明时 Litematica 的钩子可能先跑，已经在玻璃之前画过了，不再画第二遍
            if (!GlassRenderState.translucentDrawnThisFrame) ale$drawTranslucentLayer();
            GlassRenderState.translucentDrawnEarly = true;
            LitematicaRenderer.getInstance().renderSchematicOverlays(ale$camera(), profiler);
            GlassRenderState.overlaysDrawnEarly = true;
            profiler.pop();
        } finally {
            GlassRenderState.drawingEarly = false;
        }
    }

    //? if >=26.3 {
    /*private void ale$drawTranslucentLayer() {
        LitematicaRenderer.getInstance().piecewiseDrawBlockLayerGroup(this.targets.main.get(), ChunkSectionLayerGroup.TRANSLUCENT);
    }
    *///?} else {
    private void ale$drawTranslucentLayer() {
        LitematicaRenderer.getInstance().piecewiseDrawBlockLayerGroup(ChunkSectionLayerGroup.TRANSLUCENT, this.chunkLayerSampler);
    }
    //?}

    //? if >=26.2 {
    /*// 26.2 起 GameRenderer 不再公开主摄像机
    private static net.minecraft.client.Camera ale$camera() {
        return ((GameRendererCameraAccessor) Minecraft.getInstance().gameRenderer).ale$mainCamera();
    }
    *///?} else {
    private static net.minecraft.client.Camera ale$camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }
    //?}
}
