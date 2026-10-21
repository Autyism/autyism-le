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
//? if >=1.21.9
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
    /** 打开 MaLiLib 原来的列表编辑框要用（为 null 时不显示“原来的编辑框”按钮） */
    @Nullable
    private final fi.dy.masa.malilib.gui.interfaces.IConfigGui configGui;
    private final List<Entry> allEntries = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();
    private GuiTextFieldGeneric searchField;
    private String search = "";
    private int scrollLeft;
    private int scrollRight;
    private boolean modified;

    // 布局
    private int panelX, panelY, panelW, panelH, colW, listTop, listHeight;

    /**
     * 一个可选项：物品图标 + 名称 + 写进列表的 ID。同一个物品的几种方块形态（告示牌 / 墙上的告示牌、火把 / 墙上的火把……）
     * 合成一条，ids 是这几种形态的 ID，加入 / 移除时一起处理
     */
    public record Entry(String id, ItemStack icon, String name, List<String> ids) {
        Entry(String id, ItemStack icon, String name) {
            this(id, icon, name, List.of(id));
        }
    }

    /** 左边当前列表里的一行：显示的条目 + 它在配置列表里占的位置（合并的一行占几个位置） */
    private record Row(Entry entry, List<Integer> indices) {
    }

    public GuiBlockPicker(IConfigStringList config, String modId, Mode mode, @Nullable Screen parent) {
        this(config, modId, mode, parent, null);
    }

    public GuiBlockPicker(IConfigStringList config, String modId, Mode mode, @Nullable Screen parent,
                          @Nullable fi.dy.masa.malilib.gui.interfaces.IConfigGui configGui) {
        this.config = config;
        this.modId = modId;
        this.mode = mode;
        this.configGui = configGui;
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
                // 立式 / 挂墙等几种形态只列一条（物品默认放出来的那种）
                if (BlockForms.primary(block) != block) continue;
                allEntries.add(entryFor(block));
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

    /** 一个方块（连同它的其他形态）的条目 */
    private static Entry entryFor(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        List<Block> forms = BlockForms.formsOf(block);
        if (forms.size() <= 1) return new Entry(id.toString(), iconFor(block), StringUtils.translate(block.getDescriptionId()));
        List<String> ids = new ArrayList<>();
        for (Block f : forms) ids.add(BuiltInRegistries.BLOCK.getKey(f).toString());
        ItemStack icon = iconFor(block);
        return new Entry(id.toString(), icon, icon.getHoverName().getString(), ids);
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
        //? if >=1.21.11 {
        searchField.setValueWrapper(search);
        //?} else
        //searchField.setTextWrapper(search);
        searchField.setFocused(true);
        this.addTextField(searchField, field -> {
            //? if >=1.21.11 {
            search = field.getValueWrapper();
            //?} else
            //search = field.getTextWrapper();
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
        // 右上角：换成 MaLiLib 原来的列表编辑框（手动输入 ID、调整顺序时用）
        if (configGui != null) {
            ButtonGeneric original = new ButtonGeneric(0, 0, -1, 16, StringUtils.translate("autyism-le.gui.button.original_list"));
            original.setPosition(panelX + panelW - original.getWidth() - 6, panelY + 3);
            original.setHoverStrings(StringUtils.translate("autyism-le.gui.button.original_list.hover"));
            this.addButton(original, (b, mb) -> openOriginal());
        }
    }

    /** 打开 MaLiLib 原来的列表编辑框，代替这个界面（关掉它回到原来的上一层界面） */
    private void openOriginal() {
        if (configGui == null) return;
        if (modified) {
            ConfigManager.getInstance().onConfigsChanged(modId);
            modified = false;
        }
        GuiBase.openGui(new fi.dy.masa.malilib.gui.GuiStringListEdit(config, configGui, null, this.getParent()));
    }

    /** 测试用：这一条（连同所有形态）加进列表 */
    public void addForTest(Block block) {
        add(entryFor(BlockForms.primary(block)));
    }

    /** 测试用：左边显示的行（名称） */
    public List<String> shownRows() {
        List<String> out = new ArrayList<>();
        for (Row r : rows()) out.add(r.entry().name());
        return out;
    }

    /** 测试用：移除左边第 index 行 */
    public void removeRowForTest(int index) {
        List<Row> curRows = rows();
        List<String> list = current();
        List<Integer> indices = new ArrayList<>(curRows.get(index).indices());
        indices.sort(java.util.Collections.reverseOrder());
        for (int i : indices) list.remove(i);
        setStrings(list);
    }

    /** 测试用：右边列表里有没有这个方块的单独一条 */
    public boolean listsSeparately(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        for (Entry e : allEntries) if (e.id().equals(id)) return true;
        return false;
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

    /** 这一条的所有形态都已经在列表里 */
    private boolean containsAll(Entry e) {
        for (String id : e.ids()) if (!contains(id)) return false;
        return true;
    }

    /** 把这一条（所有形态）加进列表，已经有的不重复加 */
    private void add(Entry e) {
        List<String> list = current();
        boolean changed = false;
        for (String id : e.ids()) {
            if (!contains(id)) {
                list.add(id);
                changed = true;
            }
        }
        if (changed) setStrings(list);
    }

    /**
     * 左边当前列表的显示行：一个方块的所有形态都在列表里时合成一行（显示在第一个形态的位置）；
     * 只有部分形态（例如手动只填了墙上的火把）时按原样一个个显示
     */
    /** 左边的显示行按列表内容缓存（列表没变时每帧不重新算） */
    private List<String> rowsKey = List.of();
    private List<Row> rowsCache = List.of();

    private List<Row> rows() {
        List<String> cur = current();
        if (cur.equals(rowsKey)) return rowsCache;
        rowsCache = computeRows(cur);
        rowsKey = cur;
        return rowsCache;
    }

    private List<Row> computeRows(List<String> cur) {
        List<Row> rows = new ArrayList<>();
        boolean[] used = new boolean[cur.size()];
        for (int i = 0; i < cur.size(); i++) {
            if (used[i]) continue;
            if (mode == Mode.BLOCK) {
                Entry merged = mergedEntryOf(cur.get(i));
                if (merged != null) {
                    List<Integer> indices = new ArrayList<>();
                    for (String id : merged.ids()) {
                        for (int j = 0; j < cur.size(); j++) {
                            String t = cur.get(j).trim();
                            if (!used[j] && (t.equals(id) || ("minecraft:" + t).equals(id))) {
                                indices.add(j);
                                break;
                            }
                        }
                    }
                    if (indices.size() == merged.ids().size()) {
                        for (int j : indices) used[j] = true;
                        rows.add(new Row(merged, indices));
                        continue;
                    }
                }
            }
            used[i] = true;
            rows.add(new Row(describe(cur.get(i)), List.of(i)));
        }
        return rows;
    }

    /** 列表里这一条是有几种形态的方块时，它所属的合并条目；否则 null */
    @Nullable
    private Entry mergedEntryOf(String raw) {
        String s = raw.trim();
        Identifier id = Identifier.tryParse(s.contains(":") ? s : "minecraft:" + s);
        if (id == null) return null;
        var block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty() || !BlockForms.hasSeveralForms(block.get())) return null;
        return entryFor(BlockForms.primary(block.get()));
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
        List<Row> curRows = rows();
        scrollLeft = clamp(scrollLeft, Math.max(0, curRows.size() - rows));
        for (int i = 0; i < rows && scrollLeft + i < curRows.size(); i++) {
            Entry e = curRows.get(scrollLeft + i).entry();
            drawRow(ctx, e, leftX, listTop + i * ROW_HEIGHT, mouseX, mouseY, false, false);
        }
        if (cur.isEmpty()) {
            this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.list_empty"), leftX + 4, listTop + 6, 0xFF777777);
        }
        scrollRight = clamp(scrollRight, Math.max(0, filtered.size() - rows));
        for (int i = 0; i < rows && scrollRight + i < filtered.size(); i++) {
            Entry e = filtered.get(scrollRight + i);
            drawRow(ctx, e, rightX, listTop + i * ROW_HEIGHT, mouseX, mouseY, true, containsAll(e));
        }
        drawScrollbar(ctx, leftX + colW - 3, curRows.size(), scrollLeft, rows);
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

    // 1.21.9 起 MaLiLib 的鼠标事件带 MouseButtonEvent，之前是 (x, y, 按键)
    //? if >=1.21.9 {
    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        // 选取方块（鼠标中键）：指着右边的一条时加入它；在界面外时加入鼠标所指的世界里的方块；其他地方取准星对着的方块
        if (WorldPick.isPickButton(click)) {
            pickAt(click.x(), click.y());
            return true;
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int mx = (int) click.x(), my = (int) click.y();
    //?} else {
    /*@Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (WorldPick.isPickButton(mouseButton)) {
            pickAt(mouseX, mouseY);
            return true;
        }
        if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
            int mx = mouseX, my = mouseY;
    *///?}
            int leftX = panelX + 10;
            int rightX = panelX + 10 + colW + 10;
            int rows = visibleRows();
            if (my >= listTop && my < listTop + rows * ROW_HEIGHT) {
                int row = (my - listTop) / ROW_HEIGHT;
                // 右边：+ 按钮或整行都可以点（加入）
                if (mx >= rightX && mx < rightX + colW) {
                    int idx = scrollRight + row;
                    if (idx < filtered.size()) {
                        add(filtered.get(idx));
                        return true;
                    }
                }
                // 左边：只有点 − 才移除（避免误删）
                if (mx >= leftX + colW - 20 && mx < leftX + colW - 4) {
                    int idx = scrollLeft + row;
                    List<Row> curRows = rows();
                    if (idx < curRows.size()) {
                        List<String> list = current();
                        List<Integer> indices = new ArrayList<>(curRows.get(idx).indices());
                        indices.sort(java.util.Collections.reverseOrder());
                        for (int i : indices) list.remove(i);
                        setStrings(list);
                        return true;
                    }
                }
            }
        }
        //? if >=1.21.9 {
        return super.onMouseClicked(click, doubleClick);
        //?} else
        //return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    //? if >=1.21.9 {
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    //?} else
    //public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        int step = verticalAmount > 0 ? -3 : 3;
        if (mouseX < panelX + 10 + colW + 5) scrollLeft += step;
        else scrollRight += step;
        return true;
    }

    private void pickAt(double mx, double my) {
        int rightX = panelX + 10 + colW + 10;
        int rows = visibleRows();
        if (mx >= rightX && mx < rightX + colW && my >= listTop && my < listTop + rows * ROW_HEIGHT) {
            int idx = scrollRight + (int) ((my - listTop) / ROW_HEIGHT);
            if (idx < filtered.size()) add(filtered.get(idx));
            return;
        }
        boolean outside = mx < panelX || mx >= panelX + panelW || my < panelY || my >= panelY + panelH;
        net.minecraft.world.level.block.state.BlockState state = WorldPick.pick(mx, my, outside);
        if (state == null) {
            this.addMessage(fi.dy.masa.malilib.gui.Message.MessageType.WARNING, "autyism-le.message.replace.pick_nothing");
            return;
        }
        Block block = state.getBlock();
        if (mode == Mode.BLOCK) {
            add(entryFor(BlockForms.primary(block)));
        } else {
            Item item = block.asItem();
            if (item == Items.AIR) {
                this.addMessage(fi.dy.masa.malilib.gui.Message.MessageType.WARNING, "autyism-le.message.replace.pick_nothing");
                return;
            }
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!contains(id)) {
                List<String> list = current();
                list.add(id);
                setStrings(list);
            }
        }
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
