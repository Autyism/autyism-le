package com.autyism.ale.mixin.malilib;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.gui.GuiBlockPicker;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.util.GuiUtils;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 方块 / 物品列表（Litematica、打印机等 MaLiLib 系模组的设置）点开时直接打开 ALE 的选择界面；
 * MaLiLib 原来的列表编辑框在选择界面右上角的按钮里。
 */
@Mixin(value = ConfigButtonStringList.class, remap = false)
public abstract class ConfigButtonStringListMixin {
    @Shadow
    @Final
    private IConfigStringList config;
    @Shadow
    @Final
    private IConfigGui configGui;

    @Inject(method = "onMouseClickedImpl", at = @At("HEAD"), cancellable = true)
    private void ale$openPicker(MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!AleConfigs.Generic.BLOCK_PICKER.getBooleanValue()) return;
        GuiBlockPicker.Mode mode = GuiBlockPicker.detectMode(this.config);
        if (mode == null) return;
        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        GuiBase.openGui(new GuiBlockPicker(this.config, this.configGui.getModId(), mode, GuiUtils.getCurrentScreen(), this.configGui));
        cir.setReturnValue(true);
    }
}
