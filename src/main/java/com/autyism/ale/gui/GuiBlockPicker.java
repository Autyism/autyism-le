package com.autyism.ale.gui;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 需求 3：方块（或物品）选择界面，替代手打方块 ID。
 * <p>
 * 交互参照 Meteor Client 的 Block ESP 方块列表设置：左边是当前列表（点 − 移除），右边是全部方块
 * （按名称 / ID 搜索，显示图标和正常名称，点 + 直接加入列表）；外观使用 MaLiLib / Litematica 系列的风格。
 * 修改直接写进配置（与 MaLiLib 字符串列表编辑框共用同一个列表），关闭时保存。
 */
public class GuiBlockPicker extends GuiBase {
    public enum Mode { BLOCK, ITEM }

    private static final int ROW_HEIGHT = 20;
    private static final int COLOR_BG = 0xE0101010;
    private static final int COLOR_BORDER = 0xFF999999;
    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;
    private static final int COLOR_ADD = 0xFF55FF55;
    private static final int COLOR_REMOVE = 0xFFFF5555;

    private final IConfigStringList config;
    private final String modId;
    private final Mode mode;
    private final List<Entry> allEntries = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private GuiTextFieldGeneric searchField;
    private String search = "";
    private int scrollLeft;
    private int scrollRight;
    private boolean modified;

    // 布局
    private int panelX, panelY, panelW, panelH, colW, listTop, listHeight;

    /** 一个可选项：物品图标 + 名称 + 写进列表的 ID */
    public record Entry(String id, ItemStack icon, String name) {
    }

    public GuiBlockPicker(IConfigStringList config, String modId, Mode mode, @Nullable Screen parent) {
        this.config = config;
        this.modId = modId;
        this.mode = mode;
        this.title = StringUtils.translate(mode == Mode.BLOCK ? "autyism-le.gui.title.block_picker" : "autyism-le.gui.title.item_picker",
                config.getConfigGuiDisplayName());
        this.setParent(parent);
        this.useTitleHierarchy = false;
        buildEntries();
        this.filtered = new ArrayList<>(allEntries);
    }

    private void buildEntries() {
        if (mode == Mode.BLOCK) {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) continue;
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                allEntries.add(new Entry(id.toString(), iconFor(block), StringUtils.translate(block.getDescriptionId())));
            }
        } else {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                Identifier id = BuiltInRegistries.ITEM.getKey(item);
                allEntries.add(new Entry(id.toString(), new ItemStack(item), new ItemStack(item).getHoverName().getString()));
            }
        }
        allEntries.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
    }

    private static ItemStack iconFor(Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) return new ItemStack(item);
        // 没有对应物品的方块（例如水、火、墙上的火把）：用最接近的物品当图标
        if (block == Blocks.WATER) return new ItemStack(Items.WATER_BUCKET);
        if (block == Blocks.LAVA) return new ItemStack(Items.LAVA_BUCKET);
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) return new ItemStack(Items.FLINT_AND_STEEL);
        return new ItemStack(Items.BARRIER);
    }

    /** 把列表里的一条字符串解析成可显示的条目（无法解析的原样显示） */
    private Entry describe(String raw) {
        String s = raw.trim();
        Identifier id = Identifier.tryParse(s.contains(":") ? s : "minecraft:" + s);
        if (id != null) {
            if (mode == Mode.BLOCK) {
                var block = BuiltInRegistries.BLOCK.getOptional(id);
                if (block.isPresent() && (block.get() != Blocks.AIR || s.endsWith("air"))) {
                    return new Entry(raw, iconFor(block.get()), StringUtils.translate(block.get().getDescriptionId()));
                }
            } else {
                var item = BuiltInRegistries.ITEM.getOptional(id);
                if (item.isPresent() && item.get() != Items.AIR) {
                    return new Entry(raw, new ItemStack(item.get()), new ItemStack(item.get()).getHoverName().getString());
                }
            }
        }
        return new Entry(raw, new ItemStack(Items.NAME_TAG), raw);
    }

    @Override
    public void initGui() {
        super.initGui();
        panelW = Math.min(this.width - 20, 560);
        panelH = this.height - 20;
        panelX = (this.width - panelW) / 2;
        panelY = 10;
        colW = (panelW - 30) / 2;
        listTop = panelY + 50;
        listHeight = panelH - 50 - 30;

        searchField = new GuiTextFieldGeneric(panelX + 10 + colW + 10, panelY + 22, colW, 18, this.font);
        searchField.setValueWrapper(search);
        searchField.setFocused(true);
        this.addTextField(searchField, field -> {
            search = field.getValueWrapper();
            applyFilter();
            return true;
        });

        ButtonGeneric done = new ButtonGeneric(panelX + panelW - 90, panelY + panelH - 24, 80, 20,
                StringUtils.translate("autyism-le.gui.button.done"));
        this.addButton(done, (b, mb) -> this.closeGui(true));
        ButtonGeneric clear = new ButtonGeneric(panelX + 10, panelY + panelH - 24, -1, 20,
                StringUtils.translate("autyism-le.gui.button.clear_list"));
        clear.setHoverStrings(StringUtils.translate("autyism-le.gui.button.clear_list.hover"));
        this.addButton(clear, (b, mb) -> {
            if (GuiBase.isShiftDown()) setStrings(new ArrayList<>());
        });
    }

    private void applyFilter() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            filtered = new ArrayList<>(allEntries);
        } else {
            List<Entry> list = new ArrayList<>();
            for (Entry e : allEntries) {
                if (e.id().contains(q) || e.name().toLowerCase(Locale.ROOT).contains(q)) list.add(e);
            }
            filtered = list;
        }
        scrollRight = 0;
    }

    private List<String> current() {
        return new ArrayList<>(config.getStrings());
    }

    private void setStrings(List<String> list) {
        config.setStrings(list);
        modified = true;
    }

    private boolean contains(String id) {
        for (String s : config.getStrings()) {
            String t = s.trim();
            if (t.equals(id) || ("minecraft:" + t).equals(id)) return true;
        }
        return false;
    }

    private int visibleRows() {
        return Math.max(1, listHeight / ROW_HEIGHT);
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        RenderUtils.drawRect(ctx, 0, 0, this.width, this.height, 0x80000000);
        RenderUtils.drawOutlinedBox(ctx, panelX, panelY, panelW, panelH, COLOR_BG, COLOR_BORDER);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        this.drawStringWithShadow(ctx, this.getTitleString(), panelX + 10, panelY + 6, COLOR_WHITE);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        List<String> cur = current();
        int leftX = panelX + 10;
        int rightX = panelX + 10 + colW + 10;
        this.drawStringWithShadow(ctx, StringUtils.translate("autyism-le.gui.label.current_list", cur.size()), leftX, panelY + 28, 0xFFFFFF55);
        this.drawStringWithShadow(ctx, StringUtils.translate(mode == Mode.BLOCK ? "autyism-le.gui.label.all_blocks" : "autyism-le.gui.label.all_items",
                filtered.size()), rightX, listTop - 10, 0xFFAAAAAA);
        if (search.isEmpty() && !searchField.isFocused()) {
            this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.search_hint"), rightX + 4, panelY + 27, 0xFF777777);
        }
        RenderUtils.drawOutlinedBox(ctx, leftX - 2, listTop - 2, colW + 4, listHeight + 4, 0x40000000, 0xFF555555);
        RenderUtils.drawOutlinedBox(ctx, rightX - 2, listTop - 2, colW + 4, listHeight + 4, 0x40000000, 0xFF555555);

        int rows = visibleRows();
        scrollLeft = clamp(scrollLeft, Math.max(0, cur.size() - rows));
        for (int i = 0; i < rows && scrollLeft + i < cur.size(); i++) {
            Entry e = describe(cur.get(scrollLeft + i));
            drawRow(ctx, e, leftX, listTop + i * ROW_HEIGHT, mouseX, mouseY, false, false);
        }
        if (cur.isEmpty()) {
            this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.list_empty"), leftX + 4, listTop + 6, 0xFF777777);
        }
        scrollRight = clamp(scrollRight, Math.max(0, filtered.size() - rows));
        for (int i = 0; i < rows && scrollRight + i < filtered.size(); i++) {
            Entry e = filtered.get(scrollRight + i);
            drawRow(ctx, e, rightX, listTop + i * ROW_HEIGHT, mouseX, mouseY, true, contains(e.id()));
        }
        drawScrollbar(ctx, leftX + colW - 3, cur.size(), scrollLeft, rows);
        drawScrollbar(ctx, rightX + colW - 3, filtered.size(), scrollRight, rows);
    }

    private void drawRow(GuiContext ctx, Entry e, int x, int y, int mouseX, int mouseY, boolean addSide, boolean alreadyAdded) {
        boolean hover = mouseX >= x && mouseX < x + colW && mouseY >= y && mouseY < y + ROW_HEIGHT;
        if (hover) RenderUtils.drawRect(ctx, x, y, colW, ROW_HEIGHT, COLOR_ROW_HOVER);
        ctx.renderItem(e.icon(), x + 2, y + 2);
        String name = e.name();
        int maxTextW = colW - 46;
        if (this.getStringWidth(name) > maxTextW) name = this.font.plainSubstrByWidth(name, maxTextW - 6) + "…";
        this.drawStringWithShadow(ctx, name, x + 22, y + 2, alreadyAdded ? 0xFF55FF55 : COLOR_WHITE);
        String id = e.id();
        if (this.getStringWidth(id) > maxTextW) id = this.font.plainSubstrByWidth(id, maxTextW - 6) + "…";
        this.drawString(ctx, id, x + 22, y + 11, 0xFF888888);
        // 右侧 + / − 按钮
        int bx = x + colW - 20, by = y + 2;
        boolean hoverBtn = mouseX >= bx && mouseX < bx + 16 && mouseY >= by && mouseY < by + 16;
        int color = addSide ? COLOR_ADD : COLOR_REMOVE;
        if (addSide && alreadyAdded) color = 0xFF555555;
        RenderUtils.drawOutlinedBox(ctx, bx, by, 16, 16, hoverBtn ? 0x60FFFFFF : 0x30000000, color);
        String sym = addSide ? (alreadyAdded ? "✔" : "+") : "−";
        this.drawStringWithShadow(ctx, sym, bx + 8 - this.getStringWidth(sym) / 2, by + 4, color);
    }

    private void drawScrollbar(GuiContext ctx, int x, int total, int scroll, int rows) {
        if (total <= rows) return;
        int barH = Math.max(10, listHeight * rows / total);
        int barY = listTop + (listHeight - barH) * scroll / Math.max(1, total - rows);
        RenderUtils.drawRect(ctx, x, barY, 2, barH, 0xFFAAAAAA);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int mx = (int) click.x(), my = (int) click.y();
            int leftX = panelX + 10;
            int rightX = panelX + 10 + colW + 10;
            int rows = visibleRows();
            if (my >= listTop && my < listTop + rows * ROW_HEIGHT) {
                int row = (my - listTop) / ROW_HEIGHT;
                // 右边：+ 按钮或整行都可以点（加入）
                if (mx >= rightX && mx < rightX + colW) {
                    int idx = scrollRight + row;
                    if (idx < filtered.size()) {
                        Entry e = filtered.get(idx);
                        if (!contains(e.id())) {
                            List<String> list = current();
                            list.add(e.id());
                            setStrings(list);
                        }
                        return true;
                    }
                }
                // 左边：只有点 − 才移除（避免误删）
                if (mx >= leftX + colW - 20 && mx < leftX + colW - 4) {
                    int idx = scrollLeft + row;
                    List<String> list = current();
                    if (idx < list.size()) {
                        list.remove(idx);
                        setStrings(list);
                        return true;
                    }
                }
            }
        }
        return super.onMouseClicked(click, doubleClick);
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int step = verticalAmount > 0 ? -3 : 3;
        if (mouseX < panelX + 10 + colW + 5) scrollLeft += step;
        else scrollRight += step;
        return true;
    }

    @Override
    public void removed() {
        if (modified) {
            ConfigManager.getInstance().onConfigsChanged(modId);
        }
        super.removed();
    }

    // ------------------------------------------------------------------ 判断某个列表是不是方块/物品列表

    /** 根据配置名称和现有内容判断是否是方块（或物品）列表；不是返回 null */
    @Nullable
    public static Mode detectMode(IConfigStringList config) {
        String n = config.getName().toLowerCase(Locale.ROOT);
        if (n.contains("item") || n.contains("composter") || n.contains("handheld")) return Mode.ITEM;
        String[] blockHints = {"block", "skip", "replace", "fluid", "whitelist", "blacklist", "bedrock", "fill", "excavate",
                "ignorable", "placement", "break", "mine"};
        for (String h : blockHints) {
            if (n.contains(h)) return Mode.BLOCK;
        }
        for (String s : config.getStrings()) {
            Identifier id = Identifier.tryParse(s.trim());
            if (id != null && s.contains(":") && BuiltInRegistries.BLOCK.containsKey(id)) return Mode.BLOCK;
        }
        return null;
    }
}
