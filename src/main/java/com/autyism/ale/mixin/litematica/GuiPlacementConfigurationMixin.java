package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.schematic.SchematicEditSaver;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 4：在投影放置设置界面加入“保存修改”（覆盖原文件）和“另存为…”按钮。
 */
@Mixin(value = GuiPlacementConfiguration.class, remap = false)
public abstract class GuiPlacementConfigurationMixin {
    @Shadow
    @Final
    public SchematicPlacement placement;

    @Shadow
    public fi.dy.masa.malilib.gui.GuiTextFieldGeneric textFieldRename;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$addSaveButtons(CallbackInfo ci) {
        if (!AleConfigs.Generic.SAVE_EDIT_BUTTONS.getBooleanValue()) return;
        GuiBase gui = (GuiBase) (Object) this;
        boolean modified = this.placement.getSchematic().getMetadata().wasModifiedSinceSaved();
        ButtonGeneric save = new ButtonGeneric(0, 0, -1, false,
                modified ? "autyism-le.gui.button.save_edits_modified" : "autyism-le.gui.button.save_edits");
        ButtonGeneric saveAs = new ButtonGeneric(0, 0, -1, false, "autyism-le.gui.button.save_as");
        // 放在最下面那一排按钮的末尾；放不下时那一排会变成可左右滑动的按钮栏（ButtonRail）
        int[] pos = com.autyism.ale.gui.ButtonRail.endOfBottomRow(gui);
        int x = pos[0];
        int y = pos[1];
        save.setPosition(x, y);
        save.setHoverStrings(StringUtils.translate("autyism-le.gui.button.save_edits.hover"));
        gui.addButton(save, (button, mouseButton) -> {
            SchematicEditSaver.saveOverwrite(this.placement);
            gui.initGui();
        });
        x += save.getWidth() + 2;
        saveAs.setPosition(x, y);
        saveAs.setHoverStrings(StringUtils.translate("autyism-le.gui.button.save_as.hover"));
        gui.addButton(saveAs, (button, mouseButton) -> {
            SchematicPlacement p = this.placement;
            GuiTextInput input = new GuiTextInput(256, "autyism-le.gui.title.save_as", SchematicEditSaver.defaultSaveAsName(p), gui,
                    (fi.dy.masa.malilib.interfaces.IStringConsumerFeedback) name -> SchematicEditSaver.saveAs(p, name, GuiBase.isShiftDown()));
            GuiBase.openGui(input);
        });
    }
}
