package com.autyism.ale.config;

import com.autyism.ale.AleMod;
import fi.dy.masa.malilib.gui.GuiConfigsBase;

import java.util.List;

public class AleConfigGui extends GuiConfigsBase {
    public AleConfigGui() {
        super(10, 50, AleMod.MOD_ID, null, "autyism-le.gui.title.configs");
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(AleConfigs.Generic.OPTIONS);
    }
}
