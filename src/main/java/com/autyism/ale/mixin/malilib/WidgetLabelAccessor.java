package com.autyism.ale.mixin.malilib;

import fi.dy.masa.malilib.gui.widgets.WidgetLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = WidgetLabel.class, remap = false)
public interface WidgetLabelAccessor {
    @Accessor("labels")
    List<String> ale$getLabels();

    @Accessor("centered")
    boolean ale$isCentered();
}
