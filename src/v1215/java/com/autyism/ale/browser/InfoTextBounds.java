package com.autyism.ale.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 找出 Litematica 在信息面板里画的文字的下沿。Minecraft 1.21.5 版：界面直接画字，事后查不到画了哪些字，
 * 所以 Litematica 画信息面板的时候记下落在面板框内的最低一行（见 mixin/render/InfoTextRecorderMixin）。
 * 这样不管 Litematica 显示了哪几行信息（作者、描述、区域数……），预览都紧接在文字下面。
 */
public final class InfoTextBounds {
    private static boolean recording;
    private static int boxX, boxY, boxW, boxH;
    private static int lowest = -1;

    private InfoTextBounds() {
    }

    /** Litematica 画信息面板之前调用：开始记 (x, y, w, h) 框内画的字 */
    static void begin(int x, int y, int w, int h) {
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;
        lowest = -1;
        recording = true;
    }

    /** 面板里文字的下沿（界面坐标）；面板里没有文字时返回 -1 */
    static int bottom(GuiGraphics g, int x, int y, int w, int h) {
        recording = false;
        return lowest;
    }

    /** 界面在 (x, y)（已经换算成界面坐标）画了一行字 */
    public static void text(float x, float y) {
        if (!recording) return;
        if (x >= boxX && x < boxX + boxW && y >= boxY && y < boxY + boxH) {
            lowest = Math.max(lowest, (int) y + Minecraft.getInstance().font.lineHeight);
        }
    }
}
