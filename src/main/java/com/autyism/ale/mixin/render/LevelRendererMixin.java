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
    /*// 26.3：主渲染拆成 executeSolid / executeClassicTransparency / executeOit，前两段在同一个渲染通道里，中间不能另开通道。
    // 先挂在 executeOit 开头（顺序无关透明时 Litematica 也在这里画）；经典透明模式下的做法要实测后再定
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
        GlassRenderState.drawingEarly = true;
        try {
            var profiler = Profiler.get();
            profiler.push("ale_schematic_before_translucent");
            ale$drawTranslucentLayer();
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
