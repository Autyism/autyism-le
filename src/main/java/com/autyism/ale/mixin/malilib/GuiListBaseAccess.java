package com.autyism.ale.mixin.malilib;

import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GuiListBase.class, remap = false)
public interface GuiListBaseAccess {
    @Invoker("getSelectionListener")
    ISelectionListener<?> ale$getSelectionListener();
}
