package com.autyism.ale.browser;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/** 投影浏览器的显示方式（左上角的按钮按这个顺序循环） */
public enum BrowserLayout implements IConfigOptionListEntry {
    LIST("list", 0),
    PREVIEW_LIST("preview_list", 0),
    GRID_5("grid_5", 5),
    GRID_4("grid_4", 4),
    GRID_3("grid_3", 3);

    private final String id;
    /** 网格每行几个；0 = 列表 */
    public final int columns;

    BrowserLayout(String id, int columns) {
        this.id = id;
        this.columns = columns;
    }

    public boolean isGrid() {
        return this.columns > 0;
    }

    @Override
    public String getStringValue() {
        return this.id;
    }

    @Override
    public String getDisplayName() {
        return StringUtils.translate("autyism-le.browser_layout." + this.id);
    }

    @Override
    public BrowserLayout cycle(boolean forward) {
        BrowserLayout[] values = values();
        int i = (this.ordinal() + (forward ? 1 : values.length - 1)) % values.length;
        return values[i];
    }

    @Override
    public BrowserLayout fromString(String value) {
        for (BrowserLayout l : values()) {
            if (l.id.equalsIgnoreCase(value)) return l;
        }
        return LIST;
    }
}
