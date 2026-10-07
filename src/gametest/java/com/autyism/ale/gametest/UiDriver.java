package com.autyism.ale.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/** 用界面坐标像玩家一样操作鼠标和键盘；截图名带前缀和序号 */
@SuppressWarnings("UnstableApiUsage")
final class UiDriver {
    final ClientGameTestContext context;
    final String prefix;
    private int n;

    UiDriver(ClientGameTestContext context, String prefix) {
        this.context = context;
        this.prefix = prefix;
    }

    /** 1920x1080、界面缩放 3：界面坐标 640x360，和玩家常用的大小一致 */
    void bigWindow() {
        context.getInput().resizeWindow(1920, 1080);
        context.waitTicks(2);
        context.runOnClient(c -> {
            c.options.guiScale().set(3);
            resize(c);
        });
        context.waitTicks(2);
    }

    /** 恢复默认窗口（其他测试按 1280x720 写的坐标） */
    void normalWindow(int guiScale) {
        context.runOnClient(c -> c.options.guiScale().set(guiScale));
        context.getInput().resizeWindow(1280, 720);
        context.waitTicks(2);
        context.runOnClient(UiDriver::resize);
        context.waitTicks(2);
    }

    private static void resize(net.minecraft.client.Minecraft c) {
        //? if >=26.1 {
        /*c.resizeGui();
        *///?} else {
        c.resizeDisplay();
        //?}
    }

    double scale() {
        return context.computeOnClient(c -> c.getWindow().getGuiScale());
    }

    void hover(double x, double y) {
        double s = scale();
        context.getInput().setCursorPos(x * s, y * s);
        context.waitTicks(3);
    }

    void click(double x, double y, int button) {
        hover(x, y);
        context.getInput().pressMouse(button == 0 ? GLFW.GLFW_MOUSE_BUTTON_LEFT : button == 1 ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        context.waitTicks(2);
    }

    void drag(double x0, double y0, double x1, double y1) {
        drag(x0, y0, x1, y1, 0);
    }

    void drag(double x0, double y0, double x1, double y1, int button) {
        int b = button == 0 ? GLFW.GLFW_MOUSE_BUTTON_LEFT : button == 1 ? GLFW.GLFW_MOUSE_BUTTON_RIGHT : GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        hover(x0, y0);
        context.getInput().holdMouse(b);
        context.waitTick();
        double s = scale();
        for (int i = 1; i <= 8; i++) {
            context.getInput().setCursorPos((x0 + (x1 - x0) * i / 8) * s, (y0 + (y1 - y0) * i / 8) * s);
            context.waitTick();
        }
        context.getInput().releaseMouse(b);
        context.waitTicks(3);
    }

    void scrollAt(double x, double y, double amount) {
        hover(x, y);
        context.getInput().scroll(amount);
        context.waitTicks(2);
    }

    Path shot(String name) {
        return GT.shot(context, String.format("%s-%02d-%s", prefix, n++, name));
    }
}
