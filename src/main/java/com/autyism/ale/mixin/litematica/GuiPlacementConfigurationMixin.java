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

    /**
     * 找一个不和现有按钮重叠的位置：优先“子区域”标签右侧那一行；放不下就把重命名输入框缩短，放在重命名按钮右边；
     * 再不行放在左下角。
     */
    private int[] ale$findSpot(GuiBase gui, int needed) {
        java.util.List<fi.dy.masa.malilib.gui.button.ButtonBase> buttons = ((com.autyism.ale.mixin.malilib.GuiBaseAccessor) gui).ale$getButtons();
        String label = StringUtils.translate("litematica.gui.label.schematic_placement.sub_regions", this.placement.getSubRegionCount());
        int x = 14 + gui.getStringWidth(label) + 10;
        int y = 44;
        if (ale$isFree(buttons, x, y, needed, 20)) return new int[]{x, y};
        // 顶部一行：缩短重命名输入框，把“重命名”按钮左移
        int fieldX = 12, fieldWidth = this.textFieldRename.getWidth();
        if (fieldWidth - needed - 2 >= 100) {
            fi.dy.masa.malilib.gui.button.ButtonBase rename = null;
            for (var b : buttons) {
                if (b.getY() == 22 && b.getX() == fieldX + fieldWidth + 4) rename = b;
            }
            if (rename != null) {
                int shift = needed + 2;
                this.textFieldRename.setWidth(fieldWidth - shift);
                rename.setPosition(rename.getX() - shift, 22);
                return new int[]{rename.getX() + rename.getWidth() + 2, 22};
            }
        }
        return new int[]{10, gui.getScreenHeight() - 44};
    }

    private static boolean ale$isFree(java.util.List<fi.dy.masa.malilib.gui.button.ButtonBase> buttons, int x, int y, int w, int h) {
        for (var b : buttons) {
            if (x < b.getX() + b.getWidth() && x + w > b.getX() && y < b.getY() + b.getHeight() && y + h > b.getY()) return false;
        }
        return true;
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$addSaveButtons(CallbackInfo ci) {
        if (!AleConfigs.Generic.SAVE_EDIT_BUTTONS.getBooleanValue()) return;
        GuiBase gui = (GuiBase) (Object) this;
        boolean modified = this.placement.getSchematic().getMetadata().wasModifiedSinceSaved();
        ButtonGeneric save = new ButtonGeneric(0, 0, -1, false,
                modified ? "autyism-le.gui.button.save_edits_modified" : "autyism-le.gui.button.save_edits");
        ButtonGeneric saveAs = new ButtonGeneric(0, 0, -1, false, "autyism-le.gui.button.save_as");
        int needed = save.getWidth() + 2 + saveAs.getWidth();
        int[] pos = ale$findSpot(gui, needed);
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
