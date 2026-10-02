package com.autyism.ale.gui;

import com.autyism.ale.mixin.malilib.GuiBaseAccessor;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;

import java.util.List;

/** 往 Litematica 的界面里加按钮时，找一个不和已有按钮重叠的位置。 */
public final class GuiLayout {
    private GuiLayout() {
    }

    /**
     * @param candidates 候选 {x, y}；x = -1 表示在这一行从右往左找空位
     */
    public static int[] findFreeSpot(GuiBase gui, int width, int height, int[][] candidates) {
        List<ButtonBase> buttons = ((GuiBaseAccessor) gui).ale$getButtons();
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

    public static boolean isFree(List<ButtonBase> buttons, int x, int y, int w, int h) {
        for (ButtonBase b : buttons) {
            if (x < b.getX() + b.getWidth() && x + w > b.getX() && y < b.getY() + b.getHeight() && y + h > b.getY()) return false;
        }
        return true;
    }
}
