package com.autyism.ale.mixin.malilib;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.gui.GuiBlockPicker;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiStringListEdit;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.IConfigGui;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 3：所有 MaLiLib 系列模组（Litematica、打印机、Tweakeroo……）里填方块/物品的列表编辑框，
 * 右上角加一个“选择方块…”按钮，打开带搜索、图标和名称的方块选择界面。
 */
@Mixin(value = GuiStringListEdit.class, remap = false)
public abstract class GuiStringListEditMixin {
    @Shadow
    @Final
    protected IConfigStringList config;
    @Shadow
    @Final
    protected IConfigGui configGui;
    @Shadow
    protected int dialogLeft;
    @Shadow
    protected int dialogTop;
    @Shadow
    protected int dialogWidth;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$addPickerButton(CallbackInfo ci) {
        if (!AleConfigs.Generic.BLOCK_PICKER.getBooleanValue()) return;
        GuiBlockPicker.Mode mode = GuiBlockPicker.detectMode(this.config);
        if (mode == null) return;
        GuiBase gui = (GuiBase) (Object) this;
        String key = mode == GuiBlockPicker.Mode.BLOCK ? "autyism-le.gui.button.pick_blocks" : "autyism-le.gui.button.pick_items";
        ButtonGeneric button = new ButtonGeneric(0, 0, -1, false, key);
        button.setPosition(this.dialogLeft + this.dialogWidth - button.getWidth() - 4, this.dialogTop + 2);
        button.setHoverStrings(StringUtils.translate("autyism-le.gui.button.pick_blocks.hover"));
        gui.addButton(button, (b, mouseButton) ->
                GuiBase.openGui(new GuiBlockPicker(this.config, this.configGui.getModId(), mode, gui)));
    }
}
