package com.autyism.ale.mixin.malilib;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = GuiBase.class, remap = false)
public interface GuiBaseAccessor {
    @Accessor("buttons")
    List<ButtonBase> ale$getButtons();

    @Accessor("widgets")
    List<WidgetBase> ale$getWidgets();

    @Accessor("textFields")
    List<TextFieldWrapper<? extends GuiTextFieldGeneric>> ale$getTextFields();
}
