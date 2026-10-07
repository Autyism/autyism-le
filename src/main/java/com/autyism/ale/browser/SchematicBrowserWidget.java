//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.browser;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.preview.PreviewPanel;
import com.autyism.ale.preview.Previews;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicBrowser;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetDirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetDirectoryNavigation;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.nio.file.Path;
import java.util.List;

/**
 * Litematica 投影浏览器的替代列表：左上角一个按钮切换显示方式（列表 / 带预览的列表 / 每行 5、4、3 个的网格），
 * 条目之间的间距可调，右边的信息面板下方显示可以转动的 3D 预览。
 * 其余行为（选中、进入文件夹、搜索、键盘操作、信息文字）都沿用 Litematica 自己的。
 */
public class SchematicBrowserWidget extends WidgetSchematicBrowser {
    private static final int TOGGLE = 12;

    private final PreviewPanel panel = new PreviewPanel();
    private int toggleX, toggleY;
    private int lastMouseX = -1, lastMouseY = -1;
    /** 网格：每行几个、格子大小、总行数 */
    private int gridColumns = 1, tileWidth, tileHeight, gridRows, visibleRows;

    public SchematicBrowserWidget(int x, int y, int width, int height, GuiSchematicBrowserBase parent,
                                  @Nullable ISelectionListener<WidgetFileBrowserBase.DirectoryEntry> selectionListener) {
        super(x, y, width, height, parent, selectionListener);
    }

    private static BrowserLayout layout() {
        return AleConfigs.Browser.layout();
    }

    // ------------------------------------------------------------------ 切换按钮（放在目录导航栏最左边，导航栏右移让出位置）

    @Override
    protected void updateDirectoryNavigationWidget() {
        super.updateDirectoryNavigationWidget();
        WidgetDirectoryNavigation old = this.directoryNavigationWidget;
        if (old == null) return;
        int shift = TOGGLE + 3;
        this.toggleX = old.getX();
        this.toggleY = old.getY() + (old.getHeight() - TOGGLE) / 2;
        WidgetDirectoryNavigation nav = new WidgetDirectoryNavigation(old.getX() + shift, old.getY(), old.getWidth() - shift, old.getHeight(),
                this.currentDirectory, this.getRootDirectory(), this, this.iconProvider);
        this.directoryNavigationWidget = nav;
        if (this.widgetSearchBar == old) this.widgetSearchBar = nav;
    }

    private boolean isOverToggle(double mx, double my) {
        return mx >= this.toggleX && mx < this.toggleX + TOGGLE && my >= this.toggleY && my < this.toggleY + TOGGLE;
    }

    private void cycleLayout() {
        AleConfigs.Browser.LAYOUT.setOptionListValue(layout().cycle(true));
        AleConfigs.saveNow();
        this.scrollBar.setValue(0);
        this.reCreateListEntryWidgets();
    }

    /** 切换按钮的图标：画出当前显示方式的样子 */
    private void drawToggle(GuiContext g, boolean hovered) {
        int x = this.toggleX, y = this.toggleY;
        int c = hovered ? 0xFFFFFFFF : 0xFFC8C8C8;
        g.fill(x, y, x + TOGGLE, y + TOGGLE, hovered ? 0x60FFFFFF : 0x40000000);
        RenderUtils.drawOutline(g, x, y, TOGGLE, TOGGLE, c);
        BrowserLayout l = layout();
        if (l == BrowserLayout.LIST) {
            for (int i = 0; i < 3; i++) g.fill(x + 2, y + 2 + i * 3, x + TOGGLE - 2, y + 3 + i * 3, c);
        } else if (l == BrowserLayout.PREVIEW_LIST) {
            for (int i = 0; i < 2; i++) {
                g.fill(x + 2, y + 2 + i * 4, x + 5, y + 5 + i * 4, c);
                g.fill(x + 6, y + 3 + i * 4, x + TOGGLE - 2, y + 4 + i * 4, c);
            }
        } else {
            int n = l == BrowserLayout.GRID_5 ? 4 : l == BrowserLayout.GRID_4 ? 3 : 2;
            int cell = (TOGGLE - 4) / n;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    g.fill(x + 2 + i * cell, y + 2 + j * cell, x + 2 + i * cell + cell - 1, y + 2 + j * cell + cell - 1, c);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 布局

    @Override
    protected int getBrowserEntryHeightFor(WidgetFileBrowserBase.DirectoryEntry entry) {
        return switch (layout()) {
            case LIST -> AleConfigs.Browser.LIST_ROW_HEIGHT.getIntegerValue();
            case PREVIEW_LIST -> AleConfigs.Browser.PREVIEW_ROW_HEIGHT.getIntegerValue();
            default -> Math.max(20, this.tileHeight);
        };
    }

    @Override
    protected WidgetDirectoryEntry createListEntryWidget(int x, int y, int listIndex, boolean isOdd, WidgetFileBrowserBase.DirectoryEntry entry) {
        BrowserLayout l = layout();
        int w = l.isGrid() ? this.tileWidth : this.browserEntryWidth;
        return new BrowserEntryWidget(x, y, w, this.getBrowserEntryHeightFor(entry), isOdd, entry, listIndex, this, this.iconProvider, this, l);
    }

    @Override
    protected void reCreateListEntryWidgets() {
        // 先让 Litematica 按自己的方式排一次，拿到第一行的起点，保证和原来的位置一致
        BrowserLayout l = layout();
        updateGridSize(l);
        super.reCreateListEntryWidgets();
        int startX = this.browserEntriesStartX, startY = this.browserEntriesStartY + this.browserEntriesOffsetY;
        if (!this.listWidgets.isEmpty()) {
            startX = this.listWidgets.get(0).getX();
            startY = this.listWidgets.get(0).getY();
        }
        this.listWidgets.clear();
        this.maxVisibleBrowserEntries = 0;
        int usableBottom = this.posY + this.browserHeight - this.browserPaddingY;
        int gapX = AleConfigs.Browser.GAP_X.getIntegerValue(), gapY = AleConfigs.Browser.GAP_Y.getIntegerValue();
        int count = this.listContents.size();
        if (l.isGrid()) {
            int first = this.scrollBar.getValue() * this.gridColumns;
            int y = startY;
            int rows = 0;
            int fullRows = 0;
            for (int index = first; index < count; ) {
                // 最后一行只露出一部分也画出来（裁掉超出的部分），让人看得出下面还有
                if (y >= usableBottom - 8) break;
                if (y + this.tileHeight <= usableBottom) fullRows++;
                for (int c = 0; c < this.gridColumns && index < count; c++, index++) {
                    int x = startX + c * (this.tileWidth + gapX);
                    WidgetFileBrowserBase.DirectoryEntry entry = this.listContents.get(index);
                    this.listWidgets.add(this.createListEntryWidget(x, y, index, (index & 1) != 0, entry));
                    this.maxVisibleBrowserEntries++;
                }
                rows++;
                y += this.tileHeight + gapY;
            }
            this.visibleRows = Math.max(1, fullRows);
            this.scrollBar.setMaxValue(Math.max(0, this.gridRows - this.visibleRows));
        } else {
            int y = startY;
            for (int index = this.scrollBar.getValue(); index < count; index++) {
                WidgetFileBrowserBase.DirectoryEntry entry = this.listContents.get(index);
                int h = this.getBrowserEntryHeightFor(entry);
                if (y + h > usableBottom) break;
                this.listWidgets.add(this.createListEntryWidget(startX, y, index, (index & 1) != 0, entry));
                this.maxVisibleBrowserEntries++;
                y += h + gapY;
            }
            this.scrollBar.setMaxValue(Math.max(0, count - this.maxVisibleBrowserEntries));
        }
    }

    private void updateGridSize(BrowserLayout l) {
        if (!l.isGrid()) return;
        int gapX = AleConfigs.Browser.GAP_X.getIntegerValue();
        this.gridColumns = l.columns;
        this.tileWidth = Math.max(16, (this.browserEntryWidth - gapX * (this.gridColumns - 1)) / this.gridColumns);
        this.tileHeight = Math.max(20, (int) Math.round(this.tileWidth * AleConfigs.Browser.TILE_HEIGHT_RATIO.getDoubleValue()));
        this.gridRows = (this.listContents.size() + this.gridColumns - 1) / this.gridColumns;
    }

    /** 网格里滚轮一格滚一行；键盘上下移动选中时让选中的那一格可见 */
    @Override
    protected void offsetSelectionOrScrollbar(int amount, boolean changeSelection) {
        if (!layout().isGrid()) {
            super.offsetSelectionOrScrollbar(amount, changeSelection);
            return;
        }
        if (!changeSelection) {
            this.scrollBar.offsetValue(Integer.signum(amount));
            this.reCreateListEntryWidgets();
            return;
        }
        super.offsetSelectionOrScrollbar(amount, true);
        int index = this.lastSelectedEntryIndex;
        if (index >= 0) {
            int row = index / Math.max(1, this.gridColumns);
            int top = this.scrollBar.getValue();
            if (row < top) this.scrollBar.setValue(row);
            else if (row >= top + this.visibleRows) this.scrollBar.setValue(row - this.visibleRows + 1);
            this.reCreateListEntryWidgets();
        }
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        Previews.thumbnails().beginFrame();
        super.drawContents(ctx, mouseX, mouseY, partialTicks);
        Previews.thumbnails().endFrame();
        boolean overToggle = isOverToggle(mouseX, mouseY);
        drawToggle(ctx, overToggle);
        if (overToggle) {
            com.autyism.ale.preview.PreviewInput.tooltip(ctx, mouseX, mouseY, StringUtils.translate("autyism-le.gui.hover.browser_layout",
                    layout().getDisplayName()));
        }
        this.panel.drawHoverText(ctx, mouseX, mouseY);
    }

    @Override
    protected void drawSelectedSchematicInfo(GuiContext ctx, @Nullable WidgetFileBrowserBase.DirectoryEntry entry) {
        Path file = entry != null && entry.getType() == WidgetFileBrowserBase.DirectoryEntryType.FILE ? entry.getFullPath() : null;
        // Litematica 自己的小预览图（存在投影文件里的截图）由 3D 预览代替：画信息时先把它拿开
        var image = file != null ? this.cachedPreviewImages.remove(file) : null;
        int x = this.posX + this.totalWidth - this.infoWidth;
        int y = this.posY;
        int h = Math.min(this.infoHeight, this.parent.getMaxInfoHeight());
        int textBottom;
        try {
            super.drawSelectedSchematicInfo(ctx, entry);
            textBottom = InfoTextBounds.bottom(ctx, x, y, this.infoWidth, h);
        } finally {
            if (image != null) this.cachedPreviewImages.put(file, image);
        }
        if (file == null || textBottom < 0) {
            this.panel.clear();
            return;
        }
        this.panel.draw(ctx, file, x, y, this.infoWidth, h, textBottom, this.lastMouseX, this.lastMouseY);
    }

    // ------------------------------------------------------------------ 输入

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && isOverToggle(click.x(), click.y())) {
            cycleLayout();
            return true;
        }
        if (this.panel.mouseClicked(click.x(), click.y(), click.button(), com.autyism.ale.preview.GuiCompat.screen())) return true;
        return super.onMouseClicked(click, doubleClick);
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        this.panel.mouseReleased();
        return super.onMouseReleased(click);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.panel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (this.panel.wantsKeys() && com.autyism.ale.preview.PreviewInput.isMovementKey(event.key())) return true;
        return super.onKeyTyped(event);
    }

    @Override
    public boolean onCharTyped(CharacterEvent event) {
        if (this.panel.wantsKeys()) return true;
        return super.onCharTyped(event);
    }

    /** 列表区域的下沿（网格最后一行超出的部分裁到这里） */
    int listBottom() {
        return this.posY + this.browserHeight - this.browserPaddingY;
    }

    /** 测试用：信息面板里的预览 */
    public PreviewPanel panel() {
        return this.panel;
    }

    /** 测试用：切换按钮的位置 {x, y, size} */
    public int[] toggleRect() {
        return new int[]{this.toggleX, this.toggleY, TOGGLE};
    }

    /** 测试用：当前的格子大小（宽、高） */
    public Vector2f tileSize() {
        return new Vector2f(this.tileWidth, this.tileHeight);
    }
}
//?}
