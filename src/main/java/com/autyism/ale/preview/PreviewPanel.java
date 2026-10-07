package com.autyism.ale.preview;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * 信息面板里的 3D 预览：投影信息下面一排两个小按钮（全屏、自由视角），再下面是预览本身。
 * 左键拖动转角度，滚轮拉近拉远；自由视角时拖动转视线、移动键飞行。
 */
public final class PreviewPanel {
    private static final int BUTTON = 9;

    @Nullable
    private Path file;
    private int px, py, pw, ph;
    private int fullX, fullY, camX, camY;
    private boolean visible;
    private boolean dragging;
    private double lastX, lastY;
    private int mouseX = -1, mouseY = -1;

    /**
     * 画面板：infoX/infoY/infoW/infoH 是信息面板的框，textBottom 是投影信息文字的下沿。
     */
    public void draw(GuiGraphics g, Path file, int infoX, int infoY, int infoW, int infoH, int textBottom, int mouseX, int mouseY) {
        this.file = file;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.fullX = infoX + 3;
        this.fullY = textBottom + 2;
        this.camX = this.fullX + BUTTON + 3;
        this.camY = this.fullY;
        this.px = infoX + 2;
        this.py = this.fullY + BUTTON + 3;
        this.pw = infoW - 4;
        this.ph = infoY + infoH - 2 - this.py;
        this.visible = this.ph >= 16 && this.pw >= 16;
        drawButtons(g, mouseX, mouseY);
        if (!this.visible) return;

        PreviewSession session = PreviewSession.forFile(file);
        followDrag(session);
        if (session.camera().isFree() && (isOverPreview(mouseX, mouseY) || this.dragging)) session.applyMovementKeys();
        session.drawPanel(g, this.px, this.py, this.px + this.pw, this.py + this.ph);
        drawStatus(g, session.model(), this.px, this.py, this.pw, this.ph);
    }

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        boolean hoverFull = inside(mouseX, mouseY, this.fullX, this.fullY, BUTTON, BUTTON);
        boolean hoverCam = inside(mouseX, mouseY, this.camX, this.camY, BUTTON, BUTTON);
        boolean free = PreviewSession.isFreecam();
        drawFullscreenIcon(g, this.fullX, this.fullY, hoverFull ? 0xFFFFFFFF : 0xFFB0B0B0);
        if (free) g.fill(this.camX - 1, this.camY - 1, this.camX + BUTTON + 1, this.camY + BUTTON + 1, 0x60FFC040);
        drawFreecamIcon(g, this.camX, this.camY, free ? 0xFFFFD050 : hoverCam ? 0xFFFFFFFF : 0xFFB0B0B0);
    }

    /** 全屏图标：四个向外的角 */
    static void drawFullscreenIcon(GuiGraphics g, int x, int y, int color) {
        int s = BUTTON;
        g.fill(x, y, x + 3, y + 1, color);
        g.fill(x, y, x + 1, y + 3, color);
        g.fill(x + s - 3, y, x + s, y + 1, color);
        g.fill(x + s - 1, y, x + s, y + 3, color);
        g.fill(x, y + s - 1, x + 3, y + s, color);
        g.fill(x, y + s - 3, x + 1, y + s, color);
        g.fill(x + s - 3, y + s - 1, x + s, y + s, color);
        g.fill(x + s - 1, y + s - 3, x + s, y + s, color);
        g.fill(x + 3, y + 3, x + s - 3, y + s - 3, color & 0x80FFFFFF);
    }

    /** 自由视角图标：四个方向的箭头（飞行） */
    static void drawFreecamIcon(GuiGraphics g, int x, int y, int color) {
        int c = x + BUTTON / 2, m = y + BUTTON / 2;
        g.fill(c, y + 1, c + 1, y + BUTTON - 1, color);
        g.fill(x + 1, m, x + BUTTON - 1, m + 1, color);
        g.fill(c - 1, y + 1, c + 2, y + 2, color);
        g.fill(c - 1, y + BUTTON - 2, c + 2, y + BUTTON - 1, color);
        g.fill(x + 1, m - 1, x + 2, m + 2, color);
        g.fill(x + BUTTON - 2, m - 1, x + BUTTON - 1, m + 2, color);
    }

    private static void drawStatus(GuiGraphics g, PreviewModel model, int x, int y, int w, int h) {
        var font = Minecraft.getInstance().font;
        String text = null;
        int color = 0xFFA0A0A0;
        if (model.status() == PreviewModel.Status.FAILED) {
            text = StringUtils.translate("autyism-le.preview.failed");
            color = 0xFFFF6060;
        } else if (!model.isSized()) {
            text = StringUtils.translate("autyism-le.preview.loading");
        }
        if (text != null) {
            g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2, color, true);
            return;
        }
        if (!model.isComplete()) {
            int bar = (int) (w * model.progress());
            g.fill(x, y + h - 2, x + w, y + h, 0x60000000);
            g.fill(x, y + h - 2, x + bar, y + h, 0xC0FFFFFF);
        } else if (model.isTruncated()) {
            String note = StringUtils.translate("autyism-le.preview.truncated");
            g.drawString(font, note, x + 2, y + h - font.lineHeight - 1, 0xFFA0A0A0, true);
        }
    }

    /** 悬停提示（在列表画完之后调用，保证在最上层） */
    public void drawHoverText(GuiContext g, int mouseX, int mouseY) {
        if (this.file == null) return;
        if (inside(mouseX, mouseY, this.fullX, this.fullY, BUTTON, BUTTON)) {
            RenderUtils.drawHoverText(g, mouseX, mouseY, List.of(StringUtils.translate("autyism-le.preview.button.fullscreen")));
        } else if (inside(mouseX, mouseY, this.camX, this.camY, BUTTON, BUTTON)) {
            String key = PreviewSession.isFreecam() ? "autyism-le.preview.button.freecam_off" : "autyism-le.preview.button.freecam_on";
            RenderUtils.drawHoverText(g, mouseX, mouseY, List.of(StringUtils.translate(key).split("\n")));
        }
    }

    private void followDrag(PreviewSession session) {
        if (!this.dragging) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.mouseHandler.isLeftPressed()) {
            this.dragging = false;
            return;
        }
        double x = mc.mouseHandler.getScaledXPos(mc.getWindow()), y = mc.mouseHandler.getScaledYPos(mc.getWindow());
        session.camera().drag(x - this.lastX, y - this.lastY);
        this.lastX = x;
        this.lastY = y;
    }

    public boolean isOverPreview(double mouseX, double mouseY) {
        return this.visible && this.file != null && inside(mouseX, mouseY, this.px, this.py, this.pw, this.ph);
    }

    /** 鼠标按下；用掉了返回 true */
    public boolean mouseClicked(double mouseX, double mouseY, int button, @Nullable Screen parent) {
        if (this.file == null) return false;
        if (button == 0 && inside(mouseX, mouseY, this.fullX, this.fullY, BUTTON, BUTTON)) {
            PreviewSession session = PreviewSession.forFile(this.file);
            GuiBase.openGui(new FullscreenPreviewScreen(parent, session));
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, this.camX, this.camY, BUTTON, BUTTON)) {
            PreviewSession.setFreecam(!PreviewSession.isFreecam());
            return true;
        }
        if (button == 0 && isOverPreview(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            this.dragging = true;
            this.lastX = mc.mouseHandler.getScaledXPos(mc.getWindow());
            this.lastY = mc.mouseHandler.getScaledYPos(mc.getWindow());
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.dragging = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.file == null || amount == 0 || !isOverPreview(mouseX, mouseY)) return false;
        PreviewSession session = PreviewSession.current();
        if (session != null && session.file.equals(this.file)) session.camera().scroll(Math.signum(amount));
        return true;
    }

    /** 自由视角打开、鼠标在预览上时，移动键交给预览（不打开浏览器的搜索框） */
    public boolean wantsKeys() {
        return PreviewSession.isFreecam() && (isOverPreview(this.mouseX, this.mouseY) || this.dragging);
    }

    /** 选中的不再是投影文件时调用 */
    public void clear() {
        this.file = null;
        this.visible = false;
        this.dragging = false;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
