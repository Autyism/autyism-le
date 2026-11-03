package com.autyism.ale.diff;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.locate.WrongBlockGuide;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;

/**
 * The schematic diff's screen: counts per kind, the settings (show / hide, which one is the old version, alignment,
 * manual offset) and the list of differences, nearest first. Clicking an entry points the wrong block guide at it.
 */
public class DiffListScreen extends GuiBase {
    private static final int ROW = 12;
    private static final int LIST_TOP = 92;
    /** Filter: -1 = all, else DiffSession.ADDED / REMOVED / CHANGED; kept while the game runs */
    private static int filter = -1;

    private final DiffSession session;
    @Nullable
    private Future<int[]> sorting;
    @Nullable
    private int[] order;
    @Nullable
    private int[] shown;
    @Nullable
    private SchematicDiff.Result sortedFor, sortingFor;
    private int scroll;

    public DiffListScreen(DiffSession session) {
        this.session = session;
        this.title = StringUtils.translate("autyism-le.gui.title.diff", session.placement().getName(), session.otherFile().getFileName().toString());
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 10, y = 40;
        x = button(x, y, AleToolsConfigs.DIFF_SHOW.getBooleanValue() ? "autyism-le.gui.button.diff.hide" : "autyism-le.gui.button.diff.show",
                () -> AleToolsConfigs.DIFF_SHOW.toggleBooleanValue());
        x = button(x, y, this.session.swapped() ? "autyism-le.gui.button.diff.placement_new" : "autyism-le.gui.button.diff.placement_old",
                () -> {
                    this.session.setSwapped(!this.session.swapped());
                    this.shown = null;
                });
        x = button(x, y, this.session.alignment() == SchematicDiff.Alignment.ORIGIN ? "autyism-le.gui.button.diff.align_origin"
                : "autyism-le.gui.button.diff.align_region", () -> this.session.setAlignment(
                this.session.alignment() == SchematicDiff.Alignment.ORIGIN ? SchematicDiff.Alignment.REGION : SchematicDiff.Alignment.ORIGIN));
        x = button(x, y, "autyism-le.gui.button.diff.clear", () -> {
            DiffSession.stop();
            this.closeGui(true);
        });
        // manual offset, one block per click (shift: 10)
        x = 10;
        y = 62;
        String[] axes = {"X", "Y", "Z"};
        for (int a = 0; a < 3; a++) {
            final int axis = a;
            x = small(x, y, axes[a] + "-", () -> nudge(axis, -1));
            x = small(x, y, axes[a] + "+", () -> nudge(axis, 1)) + 4;
        }
        x = button(x, y, "autyism-le.gui.button.diff.reset_offset", () -> this.session.setOffset(Vec3i.ZERO));
        x += 6;
        String[] filters = {"all", "added", "removed", "changed"};
        for (int f = -1; f <= 2; f++) {
            final int value = f;
            ButtonGeneric b = new ButtonGeneric(x, y, -1, 20, StringUtils.translate("autyism-le.gui.button.diff.filter." + filters[f + 1]));
            b.setEnabled(filter != f);
            this.addButton(b, (btn, mb) -> {
                filter = value;
                this.shown = null;
                this.scroll = 0;
                this.initGui();
            });
            x += b.getWidth() + 2;
        }
    }

    private int button(int x, int y, String key, Runnable action) {
        ButtonGeneric b = new ButtonGeneric(x, y, -1, 20, StringUtils.translate(key));
        this.addButton(b, (btn, mb) -> {
            action.run();
            this.initGui();
        });
        return x + b.getWidth() + 2;
    }

    private int small(int x, int y, String label, Runnable action) {
        ButtonGeneric b = new ButtonGeneric(x, y, 22, 20, label);
        this.addButton(b, (btn, mb) -> {
            action.run();
            this.initGui();
        });
        return x + 24;
    }

    private void nudge(int axis, int step) {
        int n = isShiftDown() ? step * 10 : step;
        Vec3i o = this.session.offset();
        this.session.setOffset(new Vec3i(o.getX() + (axis == 0 ? n : 0), o.getY() + (axis == 1 ? n : 0), o.getZ() + (axis == 2 ? n : 0)));
    }

    /** Filtered entry indices, nearest first (null while sorting) */
    @Nullable
    private int[] rows() {
        SchematicDiff.Result r = this.session.result();
        if (r == null) return null;
        if (this.sortedFor != r) {
            // (the result changes when the alignment or offset changed: sort that one)
            if (this.sorting == null || this.sortingFor != r) {
                this.sorting = this.session.sortedByDistance();
                this.sortingFor = r;
            }
            if (!this.sorting.isDone()) return null;
            try {
                this.order = this.sorting.get();
            } catch (Exception e) {
                this.order = new int[0];
            }
            this.sorting = null;
            this.sortedFor = r;
            this.shown = null;
        }
        if (this.shown == null && this.order != null) {
            if (filter < 0) {
                this.shown = this.order;
            } else {
                int[] tmp = new int[this.order.length];
                int n = 0;
                for (int i : this.order) {
                    if (this.session.shownKind(r.kinds()[i]) == filter) tmp[n++] = i;
                }
                this.shown = java.util.Arrays.copyOf(tmp, n);
            }
        }
        return this.shown;
    }

    private int visibleRows() {
        return Math.max(1, (this.height - LIST_TOP - 8) / ROW);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // summary
        SchematicDiff.Result r = this.session.result();
        String status;
        if (this.session.error() != null) status = "§c" + this.session.error();
        else if (r == null) status = StringUtils.translate("autyism-le.hud.diff.computing");
        else status = StringUtils.translate("autyism-le.gui.label.diff.summary", this.session.count(DiffSession.ADDED),
                this.session.count(DiffSession.REMOVED), this.session.count(DiffSession.CHANGED))
                + (r.truncated() ? " §7" + StringUtils.translate("autyism-le.gui.label.diff.truncated", SchematicDiff.MAX_ENTRIES) : "");
        this.drawString(ctx, status, 10, 26, 0xFFFFFFFF);
        Vec3i o = this.session.offset();
        this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.diff.offset", o.getX(), o.getY(), o.getZ()), 10, 84, 0xFFAAAAAA);

        int[] rows = rows();
        int top = LIST_TOP + 4;
        RenderUtils.drawOutlinedBox(ctx, 8, LIST_TOP, this.width - 16, this.height - LIST_TOP - 4, 0x60000000, 0xFF555555);
        if (rows == null) {
            if (r != null) this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.diff.sorting"), 14, top, 0xFF999999);
            return;
        }
        if (rows.length == 0) {
            this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.diff.empty"), 14, top, 0xFF999999);
            return;
        }
        clampScroll(rows.length);
        int visible = visibleRows();
        for (int i = 0; i < visible && this.scroll + i < rows.length; i++) {
            int e = rows[this.scroll + i];
            int y = top + i * ROW;
            boolean over = mouseX >= 10 && mouseX < this.width - 10 && mouseY >= y - 1 && mouseY < y + ROW - 1;
            if (over) RenderUtils.drawRect(ctx, 10, y - 1, this.width - 20, ROW, 0x40FFFFFF);
            int kind = this.session.shownKind(r.kinds()[e]);
            BlockPos w = this.session.worldPos(r.positions()[e]);
            String tag = switch (kind) {
                case DiffSession.ADDED -> "§a+ " + StringUtils.translate("autyism-le.hud.locate.kind.added");
                case DiffSession.REMOVED -> "§c- " + StringUtils.translate("autyism-le.hud.locate.kind.removed");
                default -> "§e~ " + StringUtils.translate("autyism-le.hud.locate.kind.changed");
            };
            this.drawString(ctx, tag, 14, y, 0xFFFFFFFF);
            this.drawString(ctx, w.getX() + " " + w.getY() + " " + w.getZ(), 90, y, 0xFFCCCCCC);
            String change = describe(this.session.oldState(e)) + " §7->§r " + describe(this.session.newState(e));
            int room = this.width - 200;
            if (this.font.width(change) > room) change = this.font.plainSubstrByWidth(change, room - 8) + "...";
            this.drawString(ctx, change, 180, y, 0xFFFFFFFF);
        }
        if (rows.length > visible) {
            int trackH = visible * ROW;
            int barH = Math.max(10, trackH * visible / rows.length);
            int barY = top + (trackH - barH) * this.scroll / Math.max(1, rows.length - visible);
            RenderUtils.drawRect(ctx, this.width - 13, barY, 3, barH, 0xFFAAAAAA);
        }
    }

    private static String describe(BlockState s) {
        if (s.isAir()) return "§7" + s.getBlock().getName().getString();
        return s.getBlock().getName().getString();
    }

    private void clampScroll(int n) {
        this.scroll = Math.max(0, Math.min(this.scroll, n - visibleRows()));
    }

    /** Click on a row: point the guide there and go back to the game */
    private boolean clickedRow(double mx, double my) {
        int[] rows = rows(); // (null while a new result is being sorted)
        SchematicDiff.Result r = this.session.result();
        if (rows == null || r == null || mx < 10 || mx >= this.width - 10 || my < LIST_TOP + 3) return false;
        int i = (int) ((my - (LIST_TOP + 3)) / ROW);
        if (i < 0 || i >= visibleRows() || this.scroll + i >= rows.length) return false;
        pick(rows[this.scroll + i]);
        return true;
    }

    /** Point the guide at entry e (also used by the tests) */
    public void pick(int e) {
        SchematicDiff.Result r = this.session.result();
        if (r == null) return;
        int kind = this.session.shownKind(r.kinds()[e]);
        WrongBlockGuide.pointAt(this.session.worldPos(r.positions()[e]), this.session.newState(e), this.session.oldState(e), kind);
        GuiBase.openGui(null);
    }

    /** For the tests: the rows shown (entry indices), null while sorting */
    @Nullable
    public int[] rowsForTest() {
        return rows();
    }

    //? if >=1.21.9 {
    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubleClick) {
        if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && clickedRow(click.x(), click.y())) return true;
        return super.onMouseClicked(click, doubleClick);
    }
    //?} else {
    /*@Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && clickedRow(mouseX, mouseY)) return true;
        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }
    *///?}

    @Override
    //? if >=1.21.9 {
    public boolean onMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
    //?} else
    //public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            this.scroll += verticalAmount > 0 ? -3 : 3;
            if (this.shown != null) clampScroll(this.shown.length);
            else this.scroll = Math.max(0, this.scroll);
        }
        return true;
    }
}
