package com.autyism.ale.mixin.malilib;

import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** 往 MaLiLib 列表行（WidgetContainer）里加按钮 */
@Mixin(value = WidgetContainer.class, remap = false)
public interface WidgetContainerAccess {
    @Accessor("subWidgets")
    List<WidgetBase> ale$getSubWidgets();

    @Invoker("addButton")
    <T extends ButtonBase> T ale$addButton(T button, IButtonActionListener listener);
}
