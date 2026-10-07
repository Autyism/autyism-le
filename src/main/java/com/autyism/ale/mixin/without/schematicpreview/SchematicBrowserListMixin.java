//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.mixin.without.schematicpreview;

import com.autyism.ale.browser.SchematicBrowserWidget;
import com.autyism.ale.mixin.malilib.GuiListBaseAccess;
import com.autyism.ale.preview.Previews;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Litematica 的投影浏览器（读取、管理、保存投影的界面）换用 ALE 的列表：显示方式、缩略图、3D 预览和图标。
 * 只在没装 Schematic Preview 时应用（见 AleMixinPlugin 的 without 规则）。
 */
@Mixin(value = GuiSchematicBrowserBase.class, remap = false)
public abstract class SchematicBrowserListMixin {
    @SuppressWarnings("unchecked")
    @Inject(method = "createListWidget(II)Lfi/dy/masa/litematica/gui/widgets/WidgetSchematicBrowser;", at = @At("HEAD"), cancellable = true)
    private void ale$previewBrowser(int listX, int listY, CallbackInfoReturnable<WidgetSchematicBrowser> cir) {
        if (!Previews.browserActive()) return;
        GuiSchematicBrowserBase gui = (GuiSchematicBrowserBase) (Object) this;
        ISelectionListener<WidgetFileBrowserBase.DirectoryEntry> listener =
                (ISelectionListener<WidgetFileBrowserBase.DirectoryEntry>) ((GuiListBaseAccess) gui).ale$getSelectionListener();
        cir.setReturnValue(new SchematicBrowserWidget(listX, listY, 100, 100, gui, listener));
    }
}
//?}
