package com.autyism.ale.browser;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector2f;

/**
 * 找出 Litematica 在信息面板里画的文字的下沿：看这一帧已经登记要画的文字里，落在面板框内的最低一行。
 * 这样不管 Litematica 显示了哪几行信息（作者、描述、区域数……），预览都紧接在文字下面。
 */
final class InfoTextBounds {
    private InfoTextBounds() {
    }

    /** 面板里文字的下沿（界面坐标）；面板里没有文字时返回 -1 */
    static int bottom(GuiGraphics g, int x, int y, int w, int h) {
        int lineHeight = Minecraft.getInstance().font.lineHeight;
        int[] bottom = {-1};
        Vector2f p = new Vector2f();
        g.guiRenderState.forEachText(text -> {
            text.pose.transformPosition(text.x, text.y, p);
            if (p.x >= x && p.x < x + w && p.y >= y && p.y < y + h) bottom[0] = Math.max(bottom[0], (int) p.y + lineHeight);
        });
        return bottom[0];
    }
}
