package com.autyism.ale.mixin.malilib;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = GuiBase.class, remap = false)
public interface GuiBaseAccessor {
    @Accessor("buttons")
    List<ButtonBase> ale$getButtons();
}
