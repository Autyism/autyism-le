package com.autyism.ale.config;

import com.autyism.ale.AleMod;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

import java.util.List;

public class AleConfigGui extends GuiConfigsBase {
    /** 设置分页：通用 / 投影浏览器 / 3D 预览 */
    public enum Tab {
        GENERIC("autyism-le.gui.tab.generic"),
        BROWSER("autyism-le.gui.tab.browser"),
        PREVIEW("autyism-le.gui.tab.preview");

        private final String key;

        Tab(String key) {
            this.key = key;
        }

        List<? extends IConfigBase> options() {
            return switch (this) {
                case GENERIC -> AleConfigs.Generic.OPTIONS;
                case BROWSER -> AleConfigs.Browser.OPTIONS;
                case PREVIEW -> AleConfigs.Preview.OPTIONS;
            };
        }
    }

    private static Tab tab = Tab.GENERIC;

    public AleConfigGui() {
        super(10, 50, AleMod.MOD_ID, null, "autyism-le.gui.title.configs");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();
        int x = 10;
        for (Tab t : Tab.values()) {
            ButtonGeneric button = new ButtonGeneric(x, 26, -1, 20, StringUtils.translate(t.key));
            button.setEnabled(t != tab);
            this.addButton(button, (b, mouseButton) -> {
                tab = t;
                this.reCreateListWidget();
                this.getListWidget().resetScrollbarPosition();
                this.initGui();
            });
            x += button.getWidth() + 2;
        }
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(tab.options());
    }
}
