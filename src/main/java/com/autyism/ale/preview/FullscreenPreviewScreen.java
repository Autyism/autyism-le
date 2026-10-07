package com.autyism.ale.preview;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 全屏预览：整个窗口只显示投影，拖动 / 滚轮 / 自由视角和信息面板里一样；Esc 回到浏览器，角度和距离保留。
 */
public final class FullscreenPreviewScreen extends Screen implements Previews.PreviewOwnedScreen {
    @Nullable
    private final Screen parent;
    private final PreviewSession session;
    private boolean dragging;
    private double lastX, lastY;

    public FullscreenPreviewScreen(@Nullable Screen parent, PreviewSession session) {
        super(Component.literal(StringUtils.translate("autyism-le.preview.fullscreen_title")));
        this.parent = parent;
        this.session = session;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        g.fill(0, 0, this.width, this.height, 0xFF000000);
        if (this.dragging) {
            Minecraft mc = Minecraft.getInstance();
            double x = mc.mouseHandler.getScaledXPos(mc.getWindow()), y = mc.mouseHandler.getScaledYPos(mc.getWindow());
            this.session.camera().drag(x - this.lastX, y - this.lastY);
            this.lastX = x;
            this.lastY = y;
        }
        this.session.applyMovementKeys();
        this.session.drawFull(g, 0, 0, this.width, this.height);
        PreviewModel model = this.session.model();
        if (!model.isSized()) {
            String text = StringUtils.translate(model.status() == PreviewModel.Status.FAILED ? "autyism-le.preview.failed" : "autyism-le.preview.loading");
            g.drawString(this.font, text, (this.width - this.font.width(text)) / 2, this.height / 2, 0xFFA0A0A0, true);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // 背景在 render 里画成纯黑
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Minecraft mc = Minecraft.getInstance();
            this.dragging = true;
            this.lastX = mc.mouseHandler.getScaledXPos(mc.getWindow());
            this.lastY = mc.mouseHandler.getScaledYPos(mc.getWindow());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) this.dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) this.session.camera().scroll(Math.signum(vertical));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 移动键留给自由视角（不触发别的东西）
        if (PreviewInput.isMovementKey(event.key()) && this.session.camera().isFree()) return true;
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
