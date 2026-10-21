//? if <1.21.6 {
/*package com.autyism.ale.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

// 1.21.5 的 MaLiLib 画矩形是立即绘制，不经过 GuiGraphics：先把 GuiGraphics 里已经排队的图标和文字画掉，前后顺序才对
// （和 MaLiLib 自己的界面一样）。各处的 RenderUtils.drawRect / drawOutlinedBox / drawOutline(ctx, …) 在这个版本换成这里的方法。
// 立即绘制也不管界面的裁剪框（GuiGraphics.enableScissor）：这里按当前的裁剪框自己截掉
public final class GuiRects {
    private GuiRects() {
    }

    public static void drawRect(GuiGraphics ctx, int x, int y, int width, int height, int color) {
        RenderUtils.forceDraw(ctx);
        rect(x, y, width, height, color);
    }

    // 和 MaLiLib 的一样：背景，再在外面一圈画边框
    public static void drawOutlinedBox(GuiGraphics ctx, int x, int y, int width, int height, int colorBg, int colorBorder) {
        RenderUtils.forceDraw(ctx);
        rect(x, y, width, height, colorBg);
        outline(x - 1, y - 1, width + 2, height + 2, colorBorder);
    }

    public static void drawOutline(GuiGraphics ctx, int x, int y, int width, int height, int colorBorder) {
        RenderUtils.forceDraw(ctx);
        outline(x, y, width, height, colorBorder);
    }

    private static void outline(int x, int y, int width, int height, int color) {
        rect(x, y, 1, height, color);
        rect(x + width - 1, y, 1, height, color);
        rect(x + 1, y, width - 2, 1, color);
        rect(x + 1, y + height - 1, width - 2, 1, color);
    }

    // 只画落在裁剪框里的部分
    private static void rect(int x, int y, int width, int height, int color) {
        ScissorState scissor = RenderSystem.SCISSOR_STATE;
        if (scissor.isEnabled()) {
            Window window = Minecraft.getInstance().getWindow();
            double scale = window.getGuiScale();
            int left = (int) Math.round(scissor.getX() / scale);
            int right = (int) Math.round((scissor.getX() + scissor.getWidth()) / scale);
            int top = (int) Math.round((window.getHeight() - scissor.getY() - scissor.getHeight()) / scale);
            int bottom = (int) Math.round((window.getHeight() - scissor.getY()) / scale);
            int x0 = Math.max(x, left), y0 = Math.max(y, top), x1 = Math.min(x + width, right), y1 = Math.min(y + height, bottom);
            if (x1 <= x0 || y1 <= y0) return;
            x = x0;
            y = y0;
            width = x1 - x0;
            height = y1 - y0;
        }
        RenderUtils.drawRect(x, y, width, height, color);
    }
}
*///?}
