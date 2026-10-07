//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.config;

import com.autyism.ale.AleMod;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

/** ALE 的热键：目前只有“打开 ALE 设置” */
public final class AleHotkeys implements IKeybindProvider {
    private static final AleHotkeys INSTANCE = new AleHotkeys();
    private static boolean registered;

    private AleHotkeys() {
    }

    static void register() {
        if (registered) return;
        registered = true;
        InputEventHandler.getKeybindManager().registerKeybindProvider(INSTANCE);
        AleConfigs.Generic.OPEN_CONFIG.getKeybind().setCallback((action, key) -> {
            GuiBase.openGui(new AleConfigGui());
            return true;
        });
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (ConfigHotkey hotkey : AleConfigs.Generic.HOTKEYS) manager.addKeybindToMap(hotkey.getKeybind());
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(AleMod.MOD_NAME, "autyism-le.hotkeys.category.generic", AleConfigs.Generic.HOTKEYS);
    }
}
//?}
