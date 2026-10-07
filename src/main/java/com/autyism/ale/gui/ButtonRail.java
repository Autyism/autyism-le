package com.autyism.ale.gui;

import com.autyism.ale.mixin.malilib.GuiBaseAccessor;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Litematica 界面里一行按钮放不下、或者互相压住（例如材料列表底部的 “Litematica menu” 压到统计文字，
 * 或者别的模组也往同一行加了按钮）时，把这一行变成一条“滑轨”：按原来的顺序一个挨一个排开，不重叠；
 * 放不下的部分用鼠标滚轮或两端的 ◀ ▶ 翻过去。没有挤的行保持原样。
 */
public final class ButtonRail {
    private static final int GAP = 2;
    private static final int ARROW = 12;
    private static final int HIDDEN_X = -10000;
    private static final boolean DEBUG = Boolean.getBoolean("ale.debugrail");

    private static final Map<GuiBase, List<Rail>> RAILS = new WeakHashMap<>();
    private static final Set<GuiBase> PENDING = Collections.newSetFromMap(new WeakHashMap<>());

    private ButtonRail() {
    }

    /** 按钮栏里的一个位置：一个按钮，或者几个叠在完全相同位置上的按钮（别的模组常这样叠，保持叠着一起移动） */
    private record Item(List<ButtonBase> buttons, int y, int width) {
        int x() {
            return buttons.get(0).getX();
        }

        void moveTo(int x) {
            for (ButtonBase b : buttons) b.setPosition(x, y);
        }

        boolean contains(ButtonBase b) {
            return buttons.contains(b);
        }
    }

    private static final class Rail {
        final int top;
        final int bottom;
        final List<Item> items;
        final boolean scrollable;
        final int viewLeft;
        final int viewRight;
        int first;
        /** 左箭头的位置 */
        int arrowLeft = 10;

        Rail(int top, int bottom, List<Item> items, boolean scrollable, int viewLeft, int viewRight) {
            this.top = top;
            this.bottom = bottom;
            this.items = items;
            this.scrollable = scrollable;
            this.viewLeft = viewLeft;
            this.viewRight = viewRight;
        }

        void layout() {
            int x = viewLeft;
            boolean full = false;
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                if (i < first || full || (scrollable && x + it.width() > viewRight)) {
                    it.moveTo(HIDDEN_X);
                    if (i >= first) full = true;
                    continue;
                }
                it.moveTo(x);
                x += it.width() + GAP;
            }
        }

        /** 最后一个元素已经能看到时不用再往右翻 */
        int maxFirst() {
            int width = 0;
            for (int i = items.size() - 1; i >= 0; i--) {
                width += items.get(i).width() + (width > 0 ? GAP : 0);
                if (width > viewRight - viewLeft) return i + 1;
            }
            return 0;
        }

        boolean contains(double mouseY) {
            return mouseY >= top && mouseY < bottom;
        }
    }

    /** 最下面那一排按钮的末尾位置 {x, y}（往 Litematica 界面加按钮时用） */
    public static int[] endOfBottomRow(GuiBase gui) {
        int y = -1, x = 10;
        for (ButtonBase b : ((GuiBaseAccessor) gui).ale$getButtons()) {
            if (b.getY() > y) {
                y = b.getY();
                x = 10;
            }
            if (b.getY() == y) x = Math.max(x, b.getX() + b.getWidth() + GAP);
        }
        return new int[]{x, y < 0 ? gui.getScreenHeight() - 26 : y};
    }

    /** 只处理 Litematica 自己的界面 */
    public static boolean applies(GuiBase gui) {
        return gui.getClass().getName().startsWith("fi.dy.masa.litematica.gui.");
    }

    /** 界面重新初始化（打开、改窗口大小）：等第一次绘制时再排，那时别的模组也已经把按钮加进来了 */
    public static void onInit(GuiBase gui) {
        if (!applies(gui)) return;
        RAILS.remove(gui);
        PENDING.add(gui);
    }

    public static void beforeRender(GuiBase gui) {
        if (PENDING.remove(gui)) build(gui);
    }

    private static void build(GuiBase gui) {
        GuiBaseAccessor acc = (GuiBaseAccessor) gui;
        int width = gui.getScreenWidth();
        // 文字和输入框（不会被移动，只当作“不能压住的东西”）
        List<int[]> obstacles = new ArrayList<>();
        for (WidgetBase w : acc.ale$getWidgets()) obstacles.add(visibleRect(w));
        for (TextFieldWrapper<? extends GuiTextFieldGeneric> t : acc.ale$getTextFields()) {
            GuiTextFieldGeneric f = t.textField();
            obstacles.add(new int[]{f.getX(), f.getY(), f.getWidth(), f.getHeight()});
        }
        java.util.TreeMap<Integer, List<ButtonBase>> rows = new java.util.TreeMap<>();
        for (ButtonBase b : acc.ale$getButtons()) {
            // 隐藏的按钮（visible = false）不参与排列
            if (b.getX() > HIDDEN_X / 2 && ((com.autyism.ale.mixin.malilib.ButtonBaseAccessor) b).ale$isVisible()) {
                rows.computeIfAbsent(b.getY(), k -> new ArrayList<>()).add(b);
            }
        }
        if (rows.isEmpty()) return;
        int dockY = rows.lastKey();
        List<Rail> rails = new ArrayList<>();
        List<ButtonBase> toDock = new ArrayList<>();
        for (var row : rows.entrySet()) {
            if (row.getKey() == dockY) continue;
            List<Item> items = toItems(row.getValue());
            if (crowded(items, width)) {
                if (DEBUG) {
                    StringBuilder sb = new StringBuilder("[ale-rail] " + gui.getClass().getSimpleName() + " row y=" + row.getKey() + " width=" + width + ":");
                    for (Item it : items) sb.append(' ').append(it.buttons().size()).append('x').append('@').append(it.x()).append('+').append(it.width());
                    System.out.println(sb);
                }
                // 从这一排第一个按钮原来的位置开始排，不盖住左边的输入框 / 文字
                rails.add(makeRail(row.getKey(), items, width, Math.max(10, items.get(0).x())));
            } else {
                // 压在文字 / 输入框上的按钮挪到最下面那一排
                for (ButtonBase b : row.getValue()) {
                    if (hitsObstacle(b, obstacles)) toDock.add(b);
                }
            }
        }
        List<Item> dock = toItems(rows.get(dockY));
        for (ButtonBase b : toDock) dock.add(new Item(new ArrayList<>(List.of(b)), dockY, b.getWidth()));
        if (!toDock.isEmpty() || crowded(dock, width)) rails.add(makeRail(dockY, dock, width, 10));
        RAILS.put(gui, rails);
    }

    /** 标签只算它真正画出来的文字范围（标签的框常常比文字宽很多） */
    private static int[] visibleRect(WidgetBase w) {
        if (w instanceof fi.dy.masa.malilib.gui.widgets.WidgetLabel label) {
            var acc = (com.autyism.ale.mixin.malilib.WidgetLabelAccessor) label;
            var font = Minecraft.getInstance().font;
            int textWidth = 0;
            for (String line : acc.ale$getLabels()) textWidth = Math.max(textWidth, font.width(line));
            int lines = acc.ale$getLabels().size();
            int textHeight = lines * font.lineHeight;
            int x = acc.ale$isCentered() ? w.getX() + (w.getWidth() - textWidth) / 2 : w.getX();
            int y = w.getY() + w.getHeight() / 2 - 1 - textHeight / 2;
            return new int[]{x, y, textWidth, textHeight};
        }
        return new int[]{w.getX(), w.getY(), w.getWidth(), w.getHeight()};
    }

    private static List<Item> toItems(List<ButtonBase> buttons) {
        List<Item> items = new ArrayList<>();
        outer:
        for (ButtonBase b : buttons) {
            for (Item it : items) {
                ButtonBase first = it.buttons().get(0);
                if (first.getX() == b.getX() && first.getWidth() == b.getWidth()) {
                    it.buttons().add(b);
                    continue outer;
                }
            }
            items.add(new Item(new ArrayList<>(List.of(b)), b.getY(), b.getWidth()));
        }
        items.sort(Comparator.comparingInt(Item::x));
        return items;
    }

    private static Rail makeRail(int y, List<Item> items, int width, int start) {
        int total = -GAP;
        for (Item it : items) total += it.width() + GAP;
        boolean scroll = total > width - 10 - start;
        int left = scroll ? start + ARROW + 2 : start;
        int right = scroll ? width - 10 - ARROW - 2 : width - 10;
        Rail rail = new Rail(y, y + 20, items, scroll, left, right);
        rail.arrowLeft = start;
        rail.layout();
        return rail;
    }

    private static boolean hitsObstacle(ButtonBase b, List<int[]> obstacles) {
        for (int[] r : obstacles) {
            if (b.getX() < r[0] + r[2] && b.getX() + b.getWidth() > r[0] && b.getY() < r[1] + r[3] && b.getY() + b.getHeight() > r[1]) return true;
        }
        return false;
    }

    /** 测试 / 程序要点某个按钮时：把它所在的按钮栏翻到能看见它 */
    public static void reveal(GuiBase gui, ButtonBase button) {
        beforeRender(gui);
        for (Rail rail : RAILS.getOrDefault(gui, List.of())) {
            for (int i = 0; i < rail.items.size(); i++) {
                if (!rail.items.get(i).contains(button)) continue;
                while (button.getX() == HIDDEN_X && rail.first != i && rail.first < rail.maxFirst()) {
                    rail.first += rail.first < i ? 1 : -1;
                    rail.layout();
                }
                if (button.getX() == HIDDEN_X) {
                    rail.first = Math.min(i, rail.maxFirst());
                    rail.layout();
                }
                return;
            }
        }
    }

    /** 有重叠，或者超出屏幕 */
    private static boolean crowded(List<Item> items, int width) {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.x() < 0 || it.x() + it.width() > width) return true;
            if (i > 0) {
                Item prev = items.get(i - 1);
                // 重叠超过 1 像素才算挤（边框挨着不算）
                if (prev.x() + prev.width() > it.x() + 1) return true;
            }
        }
        return false;
    }

    /** 鼠标滚轮：在一条可滑动的行上滚动就左右翻 */
    public static boolean onScroll(GuiBase gui, double mouseX, double mouseY, double amount) {
        for (Rail rail : RAILS.getOrDefault(gui, List.of())) {
            if (rail.scrollable && rail.contains(mouseY) && amount != 0) {
                rail.first = Math.max(0, Math.min(rail.maxFirst(), rail.first + (amount > 0 ? -1 : 1)));
                rail.layout();
                return true;
            }
        }
        return false;
    }

    /** 点两端的箭头 */
    public static boolean onClick(GuiBase gui, double mouseX, double mouseY) {
        for (Rail rail : RAILS.getOrDefault(gui, List.of())) {
            if (!rail.scrollable || !rail.contains(mouseY)) continue;
            if (mouseX >= rail.arrowLeft && mouseX < rail.arrowLeft + ARROW) {
                rail.first = Math.max(0, rail.first - 1);
            } else if (mouseX >= gui.getScreenWidth() - 10 - ARROW && mouseX < gui.getScreenWidth() - 10) {
                rail.first = Math.min(rail.maxFirst(), rail.first + 1);
            } else {
                continue;
            }
            rail.layout();
            return true;
        }
        return false;
    }

    /** 画两端的箭头（到头了画成灰色） */
    public static void draw(GuiBase gui, GuiGraphics g) {
        var font = Minecraft.getInstance().font;
        for (Rail rail : RAILS.getOrDefault(gui, List.of())) {
            if (!rail.scrollable) continue;
            int y = rail.top + 6;
            int leftColor = rail.first > 0 ? 0xFFFFFFFF : 0xFF555555;
            int rightColor = rail.first < rail.maxFirst() ? 0xFFFFFFFF : 0xFF555555;
            g.fill(rail.arrowLeft, rail.top, rail.arrowLeft + ARROW, rail.top + 20, 0x80000000);
            g.fill(gui.getScreenWidth() - 10 - ARROW, rail.top, gui.getScreenWidth() - 10, rail.top + 20, 0x80000000);
            //? if >=26.1 {
            /*g.text(font, "◀", rail.arrowLeft + 2, y, leftColor, false);
            g.text(font, "▶", gui.getScreenWidth() - 10 - ARROW + 3, y, rightColor, false);
            *///?} else {
            g.drawString(font, "◀", rail.arrowLeft + 2, y, leftColor, false);
            g.drawString(font, "▶", gui.getScreenWidth() - 10 - ARROW + 3, y, rightColor, false);
            //?}
        }
    }
}
