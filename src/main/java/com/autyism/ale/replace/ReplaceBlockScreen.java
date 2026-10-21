package com.autyism.ale.replace;

import com.autyism.ale.AleMod;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 材料列表“替换”的对话框：标题“把 X 替换为”，搜索框，方块网格（按 ID 排序，当前方块默认选中并滚到可见），确定 / 取消。
 * 外观沿用 ALE 方块选择界面的配色。
 */
public class ReplaceBlockScreen extends GuiBase {
    private static final int CELL = 18;
    private static final int COLUMNS = 10;
    private static final int ROWS = 9;
    private static final int W = COLUMNS * CELL + 20;
    private static final int H = 22 + 22 + ROWS * CELL + 8 + 12 + 26;
    private static final int COLOR_BG = 0xF0101010;
    private static final int COLOR_BORDER = 0xFF999999;

    /** 一条可选的方块；同一个物品的几种形态（立式 / 挂墙……）合成一条，search 里有所有形态的 ID */
    private record Option(Block block, ItemStack icon, String id, String name, String search) {
    }

    private static List<Option> allOptions;

    private final MaterialListBase list;
    private final ItemStack material;
    private List<Option> filtered;
    private String search = "";
    @Nullable
    private Block selected;
    private int scroll;
    private int left, top, gridX, gridY;
    private boolean draggingBar;

    /** 打开原来的（Schematic Preview 的）替换对话框；没有时为 null */
    @Nullable
    private final Runnable openOriginal;

    public ReplaceBlockScreen(MaterialListBase list, ItemStack material, @Nullable Screen parent) {
        this(list, material, parent, null);
    }

    public ReplaceBlockScreen(MaterialListBase list, ItemStack material, @Nullable Screen parent, @Nullable Runnable openOriginal) {
        this.openOriginal = openOriginal;
        this.list = list;
        this.material = material.copy();
        Block current = MaterialReplacer.blockOf(material);
        this.selected = current != null ? com.autyism.ale.gui.BlockForms.primary(current) : null;
        this.title = StringUtils.translate("autyism-le.gui.title.replace_block", material.getHoverName().getString());
        this.useTitleHierarchy = false;
        this.setParent(parent);
        this.filtered = options();
        scrollToSelected();
    }

    /**
     * 所有有物品图标的方块，按 ID 排序。告示牌 / 墙上的告示牌、火把 / 墙上的火把这类同一个物品的几种形态只列一条
     * （物品默认放出来的那种，名字用物品的名字）；替换时每个方块会换成对应的形态。
     */
    private static List<Option> options() {
        if (allOptions == null) {
            List<Option> out = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Item item = block.asItem();
                if (item == Items.AIR || com.autyism.ale.gui.BlockForms.primary(block) != block) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                ItemStack icon = new ItemStack(item);
                StringBuilder search = new StringBuilder();
                for (Block form : com.autyism.ale.gui.BlockForms.formsOf(block)) {
                    search.append(BuiltInRegistries.BLOCK.getKey(form)).append(' ');
                }
                out.add(new Option(block, icon, id, icon.getHoverName().getString(), search.toString()));
            }
            out.sort((a, b) -> a.id().compareTo(b.id()));
            allOptions = out;
        }
        return allOptions;
    }

    private void applyFilter() {
        String q = this.search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            this.filtered = options();
        } else {
            List<Option> out = new ArrayList<>();
            for (Option o : options()) {
                if (o.search().contains(q) || o.name().toLowerCase(Locale.ROOT).contains(q)) out.add(o);
            }
            this.filtered = out;
        }
        this.scroll = 0;
        scrollToSelected();
    }

    private void scrollToSelected() {
        if (this.selected == null) return;
        for (int i = 0; i < this.filtered.size(); i++) {
            if (this.filtered.get(i).block() == this.selected) {
                int row = i / COLUMNS;
                if (row < this.scroll || row >= this.scroll + ROWS) this.scroll = Math.max(0, row - ROWS / 2);
                break;
            }
        }
        clampScroll();
    }

    private int maxScroll() {
        return Math.max(0, (this.filtered.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.gridX = this.left + 10;
        this.gridY = this.top + 44;
        GuiTextFieldGeneric field = new GuiTextFieldGeneric(this.left + 10, this.top + 20, W - 20, 18, this.font);
        //? if >=1.21.11 {
        field.setValueWrapper(this.search);
        //?} else
        //field.setTextWrapper(this.search);
        field.setFocused(true);
        this.addTextField(field, f -> {
            //? if >=1.21.11 {
            this.search = f.getValueWrapper();
            //?} else
            //this.search = f.getTextWrapper();
            applyFilter();
            return true;
        });
        int by = this.gridY + ROWS * CELL + 6 + 12;
        int bw = (W - 24) / 2;
        this.addButton(new ButtonGeneric(this.left + 10, by, bw, 20, StringUtils.translate("autyism-le.gui.button.ok")), (b, mb) -> confirm());
        this.addButton(new ButtonGeneric(this.left + 14 + bw, by, bw, 20, StringUtils.translate("autyism-le.gui.button.cancel")),
                (b, mb) -> this.closeGui(true));
        // 右上角：换成原来的（Schematic Preview 的）替换对话框
        if (this.openOriginal != null) {
            ButtonGeneric original = new ButtonGeneric(0, 4, -1, 16, StringUtils.translate("autyism-le.gui.button.original_replace"));
            original.setPosition(this.width - original.getWidth() - 4, 4);
            original.setHoverStrings(StringUtils.translate("autyism-le.gui.button.original_replace.hover"));
            this.addButton(original, (b, mb) -> this.openOriginal.run());
        }
    }

    private void confirm() {
        if (this.selected == null) {
            this.addMessage(Message.MessageType.ERROR, "autyism-le.message.replace.nothing_selected");
            return;
        }
        MaterialReplacer.Target target = MaterialReplacer.targetOf(this.list);
        if (target == null) {
            this.closeGui(true);
            return;
        }
        Block block = this.selected;
        int count;
        try {
            count = MaterialReplacer.replace(target, this.material, block);
        } catch (Throwable t) {
            AleMod.LOGGER.error("Replacing {} failed", this.material, t);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "autyism-le.message.replace.failed");
            this.closeGui(true);
            return;
        }
        this.closeGui(true);
        MaterialRefresh.afterReplace(this.list, target);
        InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "autyism-le.message.replace.done", count,
                StringUtils.translate(block.getDescriptionId()));
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        if (this.getParent() != null) this.getParent().extractRenderState(g, -1, -1, partialTicks);
        super.extractRenderState(g, mouseX, mouseY, partialTicks);
    }
    *///?} else {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        if (this.getParent() != null) this.getParent().render(g, -1, -1, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
    }
    //?}

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        RenderUtils.drawRect(ctx, 0, 0, this.width, this.height, 0x60000000);
        RenderUtils.drawOutlinedBox(ctx, this.left, this.top, W, H, COLOR_BG, COLOR_BORDER);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        String t = this.getTitleString();
        if (this.font.width(t) > W - 20) t = this.font.plainSubstrByWidth(t, W - 26) + "...";
        this.drawStringWithShadow(ctx, t, this.left + (W - this.font.width(t)) / 2, this.top + 7, COLOR_WHITE);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        if (this.draggingBar) scrollTo(mouseY);
        if (this.search.isEmpty()) {
            this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.search_hint"), this.left + 14, this.top + 25, 0xFF777777);
        }
        RenderUtils.drawOutlinedBox(ctx, this.gridX - 1, this.gridY - 1, COLUMNS * CELL + 2, ROWS * CELL + 2, 0x40000000, 0xFF555555);
        Option hovered = null;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                int i = (this.scroll + r) * COLUMNS + c;
                if (i >= this.filtered.size()) break;
                Option o = this.filtered.get(i);
                int x = this.gridX + c * CELL, y = this.gridY + r * CELL;
                boolean over = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
                if (over) {
                    hovered = o;
                    RenderUtils.drawRect(ctx, x, y, CELL, CELL, 0x50FFFFFF);
                }
                ctx.renderItem(o.icon(), x + 1, y + 1);
                if (o.block() == this.selected) RenderUtils.drawOutline(ctx, x, y, CELL, CELL, 0xFFFFFFFF);
            }
        }
        if (this.filtered.isEmpty()) {
            this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.no_match"), this.gridX + 4, this.gridY + 4, 0xFF777777);
        }
        // 选取方块的提示（键名跟着玩家的按键设置）
        String hint = StringUtils.translate("autyism-le.gui.label.replace.pick_hint",
                net.minecraft.client.Minecraft.getInstance().options.keyPickItem.getTranslatedKeyMessage().getString());
        if (this.font.width(hint) > W - 20) hint = this.font.plainSubstrByWidth(hint, W - 26) + "...";
        this.drawString(ctx, hint, this.gridX, this.gridY + ROWS * CELL + 6, 0xFF8A8A8A);
        // 滚动条
        int max = maxScroll();
        if (max > 0) {
            int trackH = ROWS * CELL;
            int barH = Math.max(10, trackH * ROWS / (ROWS + max));
            int barY = this.gridY + (trackH - barH) * this.scroll / max;
            RenderUtils.drawRect(ctx, this.gridX + COLUMNS * CELL + 2, barY, 3, barH, 0xFFAAAAAA);
        }
        if (hovered != null) {
            com.autyism.ale.preview.PreviewInput.tooltip(ctx, mouseX, mouseY, hovered.name(), "§7" + hovered.id());
        }
    }

    // 1.21.9 起 MaLiLib 的输入事件是 MouseButtonEvent / KeyEvent，之前是分开的数字；双击之前要自己判断
    //? if >=1.21.9 {
    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (clicked(click.x(), click.y(), click.button(), com.autyism.ale.gui.WorldPick.isPickButton(click), doubleClick)) return true;
        return super.onMouseClicked(click, doubleClick);
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        this.draggingBar = false;
        return super.onMouseReleased(click);
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (keyTyped(event.key(), com.autyism.ale.gui.WorldPick.isPickKey(event))) return true;
        return super.onKeyTyped(event);
    }
    //?} else {
    /*@Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        long now = net.minecraft.Util.getMillis();
        boolean doubleClick = mouseButton == this.lastClickButton && now - this.lastClickTime < 250L;
        this.lastClickButton = mouseButton;
        this.lastClickTime = now;
        if (clicked(mouseX, mouseY, mouseButton, com.autyism.ale.gui.WorldPick.isPickButton(mouseButton), doubleClick)) return true;
        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    private int lastClickButton = -1;
    private long lastClickTime;

    @Override
    public boolean onMouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.draggingBar = false;
        return super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onKeyTyped(int keyCode, int scanCode, int modifiers) {
        if (keyTyped(keyCode, com.autyism.ale.gui.WorldPick.isPickKey(keyCode, scanCode))) return true;
        return super.onKeyTyped(keyCode, scanCode, modifiers);
    }
    *///?}

    private boolean clicked(double mx, double my, int button, boolean pick, boolean doubleClick) {
        if (pick) {
            pickAt(mx, my);
            return true;
        }
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mx >= this.gridX && mx < this.gridX + COLUMNS * CELL && my >= this.gridY && my < this.gridY + ROWS * CELL) {
            int c = (int) ((mx - this.gridX) / CELL), r = (int) ((my - this.gridY) / CELL);
            int i = (this.scroll + r) * COLUMNS + c;
            if (i < this.filtered.size()) {
                this.selected = this.filtered.get(i).block();
                if (doubleClick) confirm();
            }
            return true;
        }
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && maxScroll() > 0 && mx >= this.gridX + COLUMNS * CELL && mx < this.gridX + COLUMNS * CELL + 8
                && my >= this.gridY && my < this.gridY + ROWS * CELL) {
            this.draggingBar = true;
            scrollTo(my);
            return true;
        }
        return false;
    }

    private void scrollTo(double mouseY) {
        double t = (mouseY - this.gridY) / (ROWS * CELL);
        this.scroll = (int) Math.round(t * maxScroll());
        clampScroll();
    }

    @Override
    //? if >=1.21.9 {
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    //?} else
    //public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            this.scroll += verticalAmount > 0 ? -1 : 1;
            clampScroll();
        }
        return true;
    }

    private boolean keyTyped(int key, boolean pick) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            confirm();
            return true;
        }
        // 选取方块键绑在键盘上时：搜索框没在打字才算
        if (pick && !this.searchFocused()) {
            double scale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
            pickAt(net.minecraft.client.Minecraft.getInstance().mouseHandler.xpos() / scale, net.minecraft.client.Minecraft.getInstance().mouseHandler.ypos() / scale);
            return true;
        }
        return false;
    }

    private boolean searchFocused() {
        for (var tf : ((com.autyism.ale.mixin.malilib.GuiBaseAccessor) this).ale$getTextFields()) {
            if (tf.isFocused()) return true;
        }
        return false;
    }

    /**
     * 选取方块：鼠标在网格里时选那一格；在对话框外时选鼠标所指的世界（或投影）里的方块；在对话框上其他地方时选准星对着的方块。
     */
    private void pickAt(double mx, double my) {
        if (mx >= this.gridX && mx < this.gridX + COLUMNS * CELL && my >= this.gridY && my < this.gridY + ROWS * CELL) {
            int i = (this.scroll + (int) ((my - this.gridY) / CELL)) * COLUMNS + (int) ((mx - this.gridX) / CELL);
            if (i < this.filtered.size()) this.selected = this.filtered.get(i).block();
            return;
        }
        boolean outside = mx < this.left || mx >= this.left + W || my < this.top || my >= this.top + H;
        net.minecraft.world.level.block.state.BlockState state = com.autyism.ale.gui.WorldPick.pick(mx, my, outside);
        if (state == null || state.getBlock().asItem() == Items.AIR) {
            this.addMessage(Message.MessageType.WARNING, "autyism-le.message.replace.pick_nothing");
            return;
        }
        pick(state.getBlock());
    }

    /** 选中某个方块（同一组形态的用代表的那个），必要时清掉搜索、滚到它 */
    public void pick(Block block) {
        Block primary = com.autyism.ale.gui.BlockForms.primary(block);
        boolean visible = false;
        for (Option o : this.filtered) visible |= o.block() == primary;
        if (!visible) {
            this.search = "";
            //? if >=1.21.11 {
            for (var tf : ((com.autyism.ale.mixin.malilib.GuiBaseAccessor) this).ale$getTextFields()) tf.textField().setValue("");
            //?} else
            //for (var tf : ((com.autyism.ale.mixin.malilib.GuiBaseAccessor) this).ale$getTextFields()) tf.getTextField().setValue("");
            applyFilter();
        }
        this.selected = primary;
        scrollToSelected();
    }

    @Nullable
    public Block selected() {
        return this.selected;
    }

    /** 测试用：选中某个方块 */
    public void select(Block block) {
        this.selected = block;
        scrollToSelected();
    }

    /** 测试用：所有可选的方块（每组形态一条） */
    public List<Block> optionBlocksForTest() {
        List<Block> out = new ArrayList<>();
        for (Option o : options()) out.add(o.block());
        return out;
    }

    /** 测试用：对话框的范围 {左, 上, 宽, 高} */
    public int[] panelForTest() {
        return new int[]{this.left, this.top, W, H};
    }

    /** 测试用：网格左上角和格子大小 */
    public int[] gridGeometry() {
        return new int[]{this.gridX, this.gridY, CELL, COLUMNS, this.scroll};
    }
}
