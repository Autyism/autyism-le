package com.autyism.ale.browser;

import com.autyism.ale.preview.Previews;
import com.autyism.ale.preview.ThumbnailCache;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.interfaces.IDirectoryNavigator;
import fi.dy.masa.malilib.gui.interfaces.IFileBrowserIconProvider;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.widgets.WidgetDirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntryType;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * 浏览器里的一个条目，按显示方式画：普通列表（一行小图标 + 名字）、带预览的列表（左边一栏缩略图）、
 * 网格（上面图标 + 名字，下面缩略图）。文件夹和投影都可以右键图标换成别的物品。
 */
public class BrowserEntryWidget extends WidgetDirectoryEntry {
    private static final int COLOR_ODD = 0x20FFFFFF;
    private static final int COLOR_EVEN = 0x50FFFFFF;
    private static final int COLOR_HOVER = 0x70FFFFFF;
    private static final int COLOR_SELECTED_OUTLINE = 0xEEEEEEEE;
    private static final int BADGE = 12;

    private final SchematicBrowserWidget browser;
    private final BrowserLayout layout;
    /** 可以右键换图标的区域（界面坐标，宽高为 0 表示没有） */
    private int iconX, iconY, iconW, iconH;
    private int badgeX, badgeY;

    public BrowserEntryWidget(int x, int y, int width, int height, boolean isOdd, DirectoryEntry entry, int listIndex,
                              IDirectoryNavigator navigator, @Nullable IFileBrowserIconProvider iconProvider,
                              SchematicBrowserWidget browser, BrowserLayout layout) {
        super(x, y, width, height, isOdd, entry, listIndex, navigator, iconProvider);
        this.browser = browser;
        this.layout = layout;
    }

    private boolean isDirectory() {
        return this.entry.getType() == DirectoryEntryType.DIRECTORY;
    }

    private Path path() {
        return this.entry.getFullPath();
    }

    @Override
    public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
        boolean hovered = this.isMouseOver(mouseX, mouseY);
        RenderUtils.drawRect(ctx, x, y, w, h, selected || hovered ? COLOR_HOVER : this.isOdd ? COLOR_ODD : COLOR_EVEN);
        FolderIcons.Choice choice = FolderIcons.get(path());
        switch (this.layout) {
            case LIST -> renderListRow(ctx, choice, x, y, w, h);
            case PREVIEW_LIST -> renderPreviewRow(ctx, choice, x, y, w, h);
            default -> renderTile(ctx, choice, x, y, w, h);
        }
        if (selected) RenderUtils.drawOutline(ctx, x, y, w, h, COLOR_SELECTED_OUTLINE);
    }

    // ------------------------------------------------------------------ 三种画法

    /** 普通列表：小图标（文件类型标记 / 文件夹 / 自定义物品）+ 名字 */
    private void renderListRow(GuiContext ctx, @Nullable FolderIcons.Choice choice, int x, int y, int w, int h) {
        int bx = x + 2, by = y + (h - BADGE) / 2;
        drawBadge(ctx, choice, bx, by, true);
        setIconArea(bx, by, BADGE, BADGE);
        drawName(ctx, x + BADGE + 6, y + (h - 8) / 2, w - BADGE - 8);
    }

    /** 带预览的列表：左边一栏放缩略图或大图标，然后小图标和名字 */
    private void renderPreviewRow(GuiContext ctx, @Nullable FolderIcons.Choice choice, int x, int y, int w, int h) {
        int column = h + 10;
        int pic = h - 6;
        int picX = x + (column - pic) / 2, picY = y + 3;
        boolean large = choice != null && choice.placement() == FolderIcons.Placement.LARGE;
        drawPicture(ctx, choice, picX, picY, pic, pic);
        int textX = x + column + 4;
        if (!large) {
            int bx = textX, by = y + (h - BADGE) / 2;
            drawBadge(ctx, choice, bx, by, false);
            textX += BADGE + 4;
            setIconArea(bx, by, BADGE, BADGE);
        }
        if (large) setIconArea(picX, picY, pic, pic);
        drawName(ctx, textX, y + (h - 8) / 2, x + w - textX - 2);
    }

    /** 网格：上面一行小图标 + 名字（太长就截断），下面是缩略图或大图标 */
    private void renderTile(GuiContext ctx, @Nullable FolderIcons.Choice choice, int x, int y, int w, int h) {
        boolean large = choice != null && choice.placement() == FolderIcons.Placement.LARGE;
        int textX = x + 3;
        if (!large) {
            drawBadge(ctx, choice, x + 2, y + 2, false);
            setIconArea(x + 2, y + 2, BADGE, BADGE);
            textX = x + BADGE + 5;
        }
        drawName(ctx, textX, y + 4, x + w - textX - 2);
        int top = y + BADGE + 4;
        int size = Math.min(w - 6, y + h - 3 - top);
        if (size > 4) {
            int px = x + (w - size) / 2, py = top + (y + h - 3 - top - size) / 2;
            drawPicture(ctx, choice, px, py, size, size);
            if (large) setIconArea(px, py, size, size);
        }
    }

    // ------------------------------------------------------------------ 部件

    /** 小图标：自定义物品（“大图标”时不画），否则文件夹 / 文件类型标记 */
    private void drawBadge(GuiContext ctx, @Nullable FolderIcons.Choice choice, int x, int y, boolean listMode) {
        this.badgeX = x;
        this.badgeY = y;
        ItemStack item = choice != null && choice.hasItem() ? choice.stack() : null;
        if (item != null && (listMode || choice.placement() != FolderIcons.Placement.LARGE)) {
            drawItem(ctx, item, x - 1, y - 1, 14);
            return;
        }
        IGuiIcon icon = this.iconProvider == null ? null
                : isDirectory() ? this.iconProvider.getIconDirectory() : this.iconProvider.getIconForFile(path());
        if (icon != null) icon.renderAt(ctx, x + (BADGE - icon.getWidth()) / 2, y + (BADGE - icon.getHeight()) / 2, 0.0F, false, false);
    }

    /** 图片区：投影缩略图 / 文件夹里第一个投影的缩略图 / 大图标 */
    private void drawPicture(GuiContext ctx, @Nullable FolderIcons.Choice choice, int x, int y, int w, int h) {
        FolderIcons.Placement placement = choice != null ? choice.placement() : FolderIcons.Placement.SMALL;
        if (placement == FolderIcons.Placement.LARGE) {
            ItemStack item = choice.hasItem() ? choice.stack() : null;
            if (item != null) {
                drawItem(ctx, item, x, y, Math.min(w, h));
            } else if (this.iconProvider != null) {
                IGuiIcon icon = isDirectory() ? this.iconProvider.getIconDirectory() : this.iconProvider.getIconForFile(path());
                if (icon != null) drawScaledIcon(ctx, icon, x, y, Math.min(w, h));
            }
            return;
        }
        Path schematic = null;
        if (!isDirectory()) {
            schematic = path();
        } else if (placement == FolderIcons.Placement.SMALL_WITH_PREVIEW) {
            schematic = Previews.firstSchematicIn(path());
        }
        if (schematic == null) return;
        ThumbnailCache.State state = Previews.thumbnails().draw(ctx, schematic, x, y, w, h);
        if (!isDirectory()) {
            if (state == ThumbnailCache.State.TOO_BIG) centeredText(ctx, StringUtils.translate("autyism-le.preview.too_large"), x, y, w, h, 0xFFA0A0A0);
            else if (state == ThumbnailCache.State.FAILED) centeredText(ctx, StringUtils.translate("autyism-le.preview.unreadable"), x, y, w, h, 0xFFFF6060);
        }
    }

    private void drawName(GuiContext ctx, int x, int y, int maxWidth) {
        Font font = Minecraft.getInstance().font;
        String name = this.entry.getDisplayName();
        if (font.width(name) > maxWidth) {
            String dots = "...";
            name = font.plainSubstrByWidth(name, Math.max(0, maxWidth - font.width(dots))) + dots;
        }
        ctx.drawString(font, name, x, y, 0xFFFFFFFF, true);
    }

    private static void centeredText(GuiGraphics g, String text, int x, int y, int w, int h, int color) {
        Font font = Minecraft.getInstance().font;
        if (font.width(text) > w - 2) text = font.plainSubstrByWidth(text, Math.max(0, w - 2));
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2 + 1, color, true);
    }

    /** 物品图标缩放到 size x size */
    static void drawItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
        float scale = size / 16.0F;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    private static void drawScaledIcon(GuiContext ctx, IGuiIcon icon, int x, int y, int size) {
        float scale = (float) size / Math.max(icon.getWidth(), icon.getHeight());
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + (size - icon.getWidth() * scale) / 2.0F, y + (size - icon.getHeight() * scale) / 2.0F);
        ctx.pose().scale(scale, scale);
        icon.renderAt(ctx, 0, 0, 0.0F, false, false);
        ctx.pose().popMatrix();
    }

    private void setIconArea(int x, int y, int w, int h) {
        this.iconX = x;
        this.iconY = y;
        this.iconW = w;
        this.iconH = h;
    }

    public boolean isOverIcon(double mouseX, double mouseY) {
        return this.iconW > 0 && mouseX >= this.iconX && mouseX < this.iconX + this.iconW && mouseY >= this.iconY && mouseY < this.iconY + this.iconH;
    }

    @Override
    public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        if (isOverIcon(mouseX, mouseY)) {
            RenderUtils.drawHoverText(ctx, mouseX, mouseY, List.of(StringUtils.translate("autyism-le.gui.hover.change_icon")));
        } else {
            super.postRenderHovered(ctx, mouseX, mouseY, selected);
        }
    }

    @Override
    protected boolean onMouseClickedImpl(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == 1 && isOverIcon(click.x(), click.y())) {
            GuiBase.openGui(new FolderIconScreen(path(), this.entry.getDisplayName(), isDirectory(), Minecraft.getInstance().screen));
            return true;
        }
        return super.onMouseClickedImpl(click, doubleClick);
    }

    SchematicBrowserWidget browser() {
        return this.browser;
    }
}
