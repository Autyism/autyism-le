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

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void ale$newFrame(CallbackInfo ci) {
        GlassRenderState.newFrame();
    }

    // method_62214 是 addMainPass 里的主渲染 lambda（开发环境与正式环境同名）
    @Inject(method = "method_62214", remap = false,
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
                    ordinal = 1))
    private void ale$drawSchematicBeforeTranslucent(CallbackInfo ci) {
        if (!AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) return;
        GlassRenderState.drawingEarly = true;
        try {
            var profiler = Profiler.get();
            profiler.push("ale_schematic_before_translucent");
            LitematicaRenderer.getInstance().piecewiseDrawBlockLayerGroup(ChunkSectionLayerGroup.TRANSLUCENT, this.chunkLayerSampler);
            GlassRenderState.translucentDrawnEarly = true;
            //? if >=26.2 {
            /*LitematicaRenderer.getInstance().renderSchematicOverlays(((com.autyism.ale.mixin.render.GameRendererCameraAccessor) Minecraft.getInstance().gameRenderer).ale$mainCamera(), profiler);
            *///?} else
            LitematicaRenderer.getInstance().renderSchematicOverlays(Minecraft.getInstance().gameRenderer.getMainCamera(), profiler);
            GlassRenderState.overlaysDrawnEarly = true;
            profiler.pop();
        } finally {
            GlassRenderState.drawingEarly = false;
        }
    }
}
