//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.mixin.without.schematicpreview;

import com.autyism.ale.mixin.malilib.WidgetContainerAccess;
import com.autyism.ale.preview.Previews;
import com.autyism.ale.replace.MaterialReplacer;
import com.autyism.ale.replace.ReplaceBlockScreen;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 材料列表每一行在“忽略”按钮左边加一个“替换”按钮：把投影里这种材料的方块全部换成另一种方块。
 * 只在没装 Schematic Preview 时应用（它有自己的替换按钮）。
 */
@Mixin(value = WidgetMaterialListEntry.class, remap = false)
public abstract class MaterialEntryReplaceMixin {
    @Shadow
    private MaterialListBase materialList;
    @Shadow
    private MaterialListEntry entry;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ale$addReplaceButton(CallbackInfo ci) {
        if (this.entry == null || this.materialList == null || !Previews.replaceActive()) return;
        if (MaterialReplacer.targetOf(this.materialList) == null || MaterialReplacer.blockOf(this.entry.getStack()) == null && this.entry.getStack().isEmpty()) return;
        WidgetContainerAccess self = (WidgetContainerAccess) this;
        WidgetBase row = (WidgetBase) (Object) this;
        ButtonBase anchor = null;
        for (WidgetBase w : self.ale$getSubWidgets()) {
            if (w instanceof ButtonBase b && (anchor == null || b.getX() < anchor.getX())) anchor = b;
        }
        int height = anchor != null ? anchor.getHeight() : 20;
        int y = anchor != null ? anchor.getY() : row.getY() + 1;
        ButtonGeneric replace = new ButtonGeneric(0, y, -1, height, StringUtils.translate("autyism-le.gui.button.replace"));
        int right = anchor != null ? anchor.getX() - 1 : row.getX() + row.getWidth();
        replace.setPosition(right - 1 - replace.getWidth(), y);
        replace.setHoverStrings(StringUtils.translate("autyism-le.gui.button.replace.hover"));
        MaterialListBase list = this.materialList;
        ItemStack stack = this.entry.getStack().copy();
        self.ale$addButton(replace, (b, mouseButton) -> GuiBase.openGui(new ReplaceBlockScreen(list, stack, com.autyism.ale.preview.GuiCompat.screen())));
    }
}
//?}
