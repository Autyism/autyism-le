package com.autyism.ale.mixin.render;

import com.autyism.ale.render.TranslucentEntityRender;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 需求 5b：提交投影实体期间，实体渲染器创建的不透明实体渲染类型换成半透明的 entityTranslucent。
 */
@Mixin(RenderTypes.class)
public abstract class RenderTypesMixin {
    private static void ale$swap(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (TranslucentEntityRender.ACTIVE.get()) {
            cir.setReturnValue(RenderTypes.entityTranslucent(texture));
        }
    }

    @Inject(method = "entitySolid", at = @At("HEAD"), cancellable = true)
    private static void ale$solid(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    //? if >=26.1 {
    /*// 26.1 起改了名：带背面剔除的叫 entityCutoutCull，不剔除的叫 entityCutout(…)，entitySmoothCutout 没有了
    @Inject(method = "entityCutoutCull", at = @At("HEAD"), cancellable = true)
    private static void ale$cutout(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entityCutout(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void ale$cutoutNoCull(Identifier texture, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entityCutout(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void ale$cutoutNoCull1(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entityCutoutZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void ale$cutoutZ(Identifier texture, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }
    *///?} else {
    @Inject(method = "entityCutout", at = @At("HEAD"), cancellable = true)
    private static void ale$cutout(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void ale$cutoutNoCull(Identifier texture, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entityCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void ale$cutoutNoCull1(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entityCutoutNoCullZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void ale$cutoutZ(Identifier texture, boolean outline, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }

    @Inject(method = "entitySmoothCutout", at = @At("HEAD"), cancellable = true)
    private static void ale$smooth(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        ale$swap(texture, cir);
    }
    //?}

    @Inject(method = "armorCutoutNoCull", at = @At("HEAD"), cancellable = true)
    private static void ale$armor(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        // 26.3 没有半透明盔甲类型了，用半透明实体类型
        //? if >=26.3 {
        /*if (TranslucentEntityRender.ACTIVE.get()) cir.setReturnValue(RenderTypes.entityTranslucent(texture));
        *///?} else
        if (TranslucentEntityRender.ACTIVE.get()) cir.setReturnValue(RenderTypes.armorTranslucent(texture));
    }
}
