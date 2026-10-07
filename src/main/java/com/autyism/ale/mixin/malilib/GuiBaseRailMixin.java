package com.autyism.ale.mixin.malilib;

import com.autyism.ale.gui.ButtonRail;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 按钮行放不下 / 互相压住时变成可左右滑动的按钮栏（见 ButtonRail） */
@Mixin(value = GuiBase.class, remap = false)
public abstract class GuiBaseRailMixin {
    @Inject(method = "initGui", at = @At("HEAD"))
    private void ale$railInit(CallbackInfo ci) {
        ButtonRail.onInit((GuiBase) (Object) this);
    }

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("HEAD"))
    *///?} else
    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void ale$railLayout(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ButtonRail.beforeRender((GuiBase) (Object) this);
    }

    @Inject(method = "drawButtons", at = @At("TAIL"))
    private void ale$railArrows(GuiContext ctx, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ButtonRail.draw((GuiBase) (Object) this, ctx);
    }

    @Inject(method = "onMouseScrolled", at = @At("HEAD"), cancellable = true)
    private void ale$railScroll(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        double amount = verticalAmount != 0 ? verticalAmount : -horizontalAmount;
        if (ButtonRail.onScroll((GuiBase) (Object) this, mouseX, mouseY, amount)) cir.setReturnValue(true);
    }

    @Inject(method = "onMouseClicked", at = @At("HEAD"), cancellable = true)
    private void ale$railClick(MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ButtonRail.onClick((GuiBase) (Object) this, click.x(), click.y())) cir.setReturnValue(true);
    }
}
