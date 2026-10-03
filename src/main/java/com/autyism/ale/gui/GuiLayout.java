package com.autyism.ale.gui;

import com.autyism.ale.mixin.malilib.GuiBaseAccessor;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;

import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.wrappers.TextFieldWrapper;

import java.util.ArrayList;
import java.util.List;

/** 往 Litematica 的界面里加按钮时，找一个不和已有按钮重叠的位置。 */
public final class GuiLayout {
    private GuiLayout() {
    }

    /**
     * @param candidates 候选 {x, y}；x = -1 表示在这一行从右往左找空位
     */
    public static int[] findFreeSpot(GuiBase gui, int width, int height, int[][] candidates) {
        List<int[]> buttons = occupied(gui);
        for (int[] c : candidates) {
            if (c[0] >= 0) {
                if (isFree(buttons, c[0], c[1], width, height)) return c;
                continue;
            }
            for (int x = gui.getScreenWidth() - width - 10; x >= 10; x -= 4) {
                if (isFree(buttons, x, c[1], width, height)) return new int[]{x, c[1]};
            }
        }
        int[] last = candidates[candidates.length - 1];
        return new int[]{Math.max(last[0], 10), last[1]};
    }

    /** 界面上已经占用的矩形 {x, y, w, h}：按钮、文本框、标签和其他小部件（比如材料列表右上角的“倍数”输入框） */
    public static List<int[]> occupied(GuiBase gui) {
        GuiBaseAccessor acc = (GuiBaseAccessor) gui;
        List<int[]> rects = new ArrayList<>();
        // 标题文字（画在 (20, 10)）
        rects.add(new int[]{20, 8, gui.getStringWidth(gui.getTitleString()) + 4, 12});
        for (ButtonBase b : acc.ale$getButtons()) rects.add(new int[]{b.getX(), b.getY(), b.getWidth(), b.getHeight()});
        for (WidgetBase w : acc.ale$getWidgets()) rects.add(new int[]{w.getX(), w.getY(), w.getWidth(), w.getHeight()});
        for (TextFieldWrapper<? extends GuiTextFieldGeneric> t : acc.ale$getTextFields()) {
            GuiTextFieldGeneric f = t.textField();
            rects.add(new int[]{f.getX(), f.getY(), f.getWidth(), f.getHeight()});
        }
        return rects;
    }

    public static boolean isFree(List<int[]> rects, int x, int y, int w, int h) {
        for (int[] r : rects) {
            if (x < r[0] + r[2] && x + w > r[0] && y < r[1] + r[3] && y + h > r[1]) return false;
        }
        return true;
    }
}
