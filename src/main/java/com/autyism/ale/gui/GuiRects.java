//? if <1.21.6 {
/*package com.autyism.ale.gui;

import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;

// 1.21.5 的 MaLiLib 画矩形是立即绘制，不经过 GuiGraphics：先把 GuiGraphics 里已经排队的图标和文字画掉，前后顺序才对
// （和 MaLiLib 自己的界面一样）。各处的 RenderUtils.drawRect / drawOutlinedBox(ctx, …) 在这个版本换成这里的方法
public final class GuiRects {
    private GuiRects() {
    }

    public static void drawRect(GuiGraphics ctx, int x, int y, int width, int height, int color) {
        RenderUtils.forceDraw(ctx);
        RenderUtils.drawRect(x, y, width, height, color);
    }

    public static void drawOutlinedBox(GuiGraphics ctx, int x, int y, int width, int height, int colorBg, int colorBorder) {
        RenderUtils.forceDraw(ctx);
        RenderUtils.drawOutlinedBox(x, y, width, height, colorBg, colorBorder);
    }
}
*///?}
