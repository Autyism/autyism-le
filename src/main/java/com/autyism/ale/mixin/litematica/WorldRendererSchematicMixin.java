package com.autyism.ale.mixin.litematica;

import com.autyism.ale.render.TranslucentEntityRender;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
//? if >=1.21.9
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 5b：Litematica 提交投影实体 / 方块实体时，把提交队列换成半透明代理。
 */
@Mixin(value = WorldRendererSchematic.class, remap = false)
public abstract class WorldRendererSchematicMixin {
    //? if >=1.21.9 {
    @ModifyVariable(method = "renderEntities", at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector ale$wrapEntityQueue(SubmitNodeCollector queue) {
        if (!TranslucentEntityRender.enabledForEntities()) return queue;
        TranslucentEntityRender.ACTIVE.set(true);
        return TranslucentEntityRender.wrap(queue);
    }
    //?} else {
    /*// 1.21.8 及更早没有提交队列，Litematica 直接把投影实体画进缓冲：交给实体渲染器的缓冲换成半透明代理
    @Inject(method = "renderEntities", at = @At("HEAD"))
    private void ale$beginEntities(CallbackInfo ci) {
        if (TranslucentEntityRender.enabledForEntities()) TranslucentEntityRender.ACTIVE.set(true);
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderEntities", index = 6, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private net.minecraft.client.renderer.MultiBufferSource ale$wrapEntityBuffers(net.minecraft.client.renderer.MultiBufferSource buffers) {
        return TranslucentEntityRender.enabledForEntities() ? TranslucentEntityRender.wrap(buffers) : buffers;
    }
    *///?}

    @Inject(method = "renderEntities", at = @At("RETURN"))
    private void ale$endEntities(CallbackInfo ci) {
        TranslucentEntityRender.ACTIVE.set(false);
    }

    //? if >=1.21.9 {
    @ModifyVariable(method = "renderBlockEntities", at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector ale$wrapBlockEntityQueue(SubmitNodeCollector queue) {
        if (!TranslucentEntityRender.enabledForBlockEntities()) return queue;
        TranslucentEntityRender.ACTIVE.set(true);
        return TranslucentEntityRender.wrap(queue);
    }
    //?} else {
    /*@Inject(method = "renderBlockEntities", at = @At("HEAD"))
    private void ale$beginBlockEntities(CallbackInfo ci) {
        if (TranslucentEntityRender.enabledForBlockEntities()) TranslucentEntityRender.ACTIVE.set(true);
    }

    // 两处方块实体绘制（区块里的、不剔除的）都换
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderBlockEntities", index = 3, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V"))
    private net.minecraft.client.renderer.MultiBufferSource ale$wrapBlockEntityBuffers(net.minecraft.client.renderer.MultiBufferSource buffers) {
        return TranslucentEntityRender.enabledForBlockEntities() ? TranslucentEntityRender.wrap(buffers) : buffers;
    }
    *///?}

    @Inject(method = "renderBlockEntities", at = @At("RETURN"))
    private void ale$endBlockEntities(CallbackInfo ci) {
        TranslucentEntityRender.ACTIVE.set(false);
    }

    //? if >=1.21.11 <26.1 {
    /** 透过玻璃显示投影时：错误标记的渲染通道用原版画半透明层之前的深度（见 OverlayDepth），水和半透明方块挡不住它 */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "drawOverlayInternal",
            at = @At(value = "INVOKE", remap = true,
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"))
    private com.mojang.blaze3d.systems.RenderPass ale$overlayDepth(com.mojang.blaze3d.systems.CommandEncoder encoder,
            java.util.function.Supplier<String> label, com.mojang.blaze3d.textures.GpuTextureView color, java.util.OptionalInt clearColor,
            @org.jetbrains.annotations.Nullable com.mojang.blaze3d.textures.GpuTextureView depth, java.util.OptionalDouble clearDepth,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<com.mojang.blaze3d.systems.RenderPass> original) {
        return original.call(encoder, label, color, clearColor, com.autyism.ale.render.OverlayDepth.depthFor(depth), clearDepth);
    }
    //?}
}
