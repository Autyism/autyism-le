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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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
    private static final int H = 22 + 22 + ROWS * CELL + 8 + 26;
    private static final int COLOR_BG = 0xF0101010;
    private static final int COLOR_BORDER = 0xFF999999;

    private record Option(Block block, ItemStack icon, String id, String name) {
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

    public ReplaceBlockScreen(MaterialListBase list, ItemStack material, @Nullable Screen parent) {
        this.list = list;
        this.material = material.copy();
        this.selected = MaterialReplacer.blockOf(material);
        this.title = StringUtils.translate("autyism-le.gui.title.replace_block", material.getHoverName().getString());
        this.useTitleHierarchy = false;
        this.setParent(parent);
        this.filtered = options();
        scrollToSelected();
    }

    /** 所有有物品图标的方块，按 ID 排序 */
    private static List<Option> options() {
        if (allOptions == null) {
            List<Option> out = new ArrayList<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Item item = block.asItem();
                if (item == Items.AIR) continue;
                String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                out.add(new Option(block, new ItemStack(item), id, StringUtils.translate(block.getDescriptionId())));
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
                if (o.id().contains(q) || o.name().toLowerCase(Locale.ROOT).contains(q)) out.add(o);
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
        field.setValueWrapper(this.search);
        field.setFocused(true);
        this.addTextField(field, f -> {
            this.search = f.getValueWrapper();
            applyFilter();
            return true;
        });
        int by = this.gridY + ROWS * CELL + 6;
        int bw = (W - 24) / 2;
        this.addButton(new ButtonGeneric(this.left + 10, by, bw, 20, StringUtils.translate("autyism-le.gui.button.ok")), (b, mb) -> confirm());
        this.addButton(new ButtonGeneric(this.left + 14 + bw, by, bw, 20, StringUtils.translate("autyism-le.gui.button.cancel")),
                (b, mb) -> this.closeGui(true));
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

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        double mx = click.x(), my = click.y();
        if (click.button() == 0 && mx >= this.gridX && mx < this.gridX + COLUMNS * CELL && my >= this.gridY && my < this.gridY + ROWS * CELL) {
            int c = (int) ((mx - this.gridX) / CELL), r = (int) ((my - this.gridY) / CELL);
            int i = (this.scroll + r) * COLUMNS + c;
            if (i < this.filtered.size()) {
                this.selected = this.filtered.get(i).block();
                if (doubleClick) confirm();
            }
            return true;
        }
        if (click.button() == 0 && maxScroll() > 0 && mx >= this.gridX + COLUMNS * CELL && mx < this.gridX + COLUMNS * CELL + 8
                && my >= this.gridY && my < this.gridY + ROWS * CELL) {
            this.draggingBar = true;
            scrollTo(my);
            return true;
        }
        return super.onMouseClicked(click, doubleClick);
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent click) {
        this.draggingBar = false;
        return super.onMouseReleased(click);
    }

    private void scrollTo(double mouseY) {
        double t = (mouseY - this.gridY) / (ROWS * CELL);
        this.scroll = (int) Math.round(t * maxScroll());
        clampScroll();
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            this.scroll += verticalAmount > 0 ? -1 : 1;
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.onKeyTyped(event);
    }

    /** 测试用：选中某个方块 */
    public void select(Block block) {
        this.selected = block;
        scrollToSelected();
    }

    /** 测试用：网格左上角和格子大小 */
    public int[] gridGeometry() {
        return new int[]{this.gridX, this.gridY, CELL, COLUMNS, this.scroll};
    }
}
