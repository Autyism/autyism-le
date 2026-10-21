package com.autyism.ale.mixin.render;

import com.autyism.ale.browser.InfoTextBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft 1.21.5：投影浏览器画信息面板时，记下每行字画在哪里（见 InfoTextBounds） */
@Mixin(GuiGraphics.class)
public abstract class InfoTextRecorderMixin {
    @Shadow
    @Final
    private PoseStack pose;

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"))
    private void ale$recordString(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (text != null) ale$record(x, y);
    }

    @Inject(method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I", at = @At("HEAD"))
    private void ale$recordSequence(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        ale$record(x, y);
    }

    @Unique
    private void ale$record(int x, int y) {
        Vector3f p = this.pose.last().pose().transformPosition(x, y, 0.0F, new Vector3f());
        InfoTextBounds.text(p.x, p.y);
    }
}
