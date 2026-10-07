package com.autyism.ale.gametest;

import com.autyism.ale.browser.BrowserLayout;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.preview.PreviewSession;
import com.autyism.ale.preview.Previews;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * 投影浏览器：显示方式、缩略图、3D 预览（拖动、滚轮、全屏、自由视角）。
 * 只在没装 Schematic Preview 时测 ALE 自己的版本（-PnoSchematicPreview）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class BrowserPreviewGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("preview")) return;
        if (Previews.ORIGINAL_INSTALLED) {
            GT.log("[preview] Schematic Preview is installed: ALE's own browser previews step aside, skipped");
            return;
        }
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        UiDriver ui = new UiDriver(context, "ale");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 205.5 64 20.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 205.5) < 0.01, 200);
            PreviewFixtures.createAll(context, sp, 40);
            context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.LIST));
            ui.bigWindow();

            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(40);
            ui.shot("list");
            ui.hover(21, 35);
            ui.shot("toggle-hover");
            for (int i = 1; i <= 5; i++) {
                ui.click(21, 35, 0);
                context.waitTicks(60);
                BrowserLayout layout = context.computeOnClient(c -> AleConfigs.Browser.layout());
                ui.shot("mode-" + layout.getStringValue());
            }
            // 回到列表，选中 ale_house（第 4 行）
            ui.click(100, 104, 0);
            context.waitTicks(80);
            ui.shot("selected-house");
            ui.drag(548, 240, 588, 240);
            ui.shot("drag-x");
            ui.drag(548, 240, 548, 210);
            ui.shot("drag-y");
            for (int i = 0; i < 3; i++) {
                ui.scrollAt(548, 240, 1);
                ui.shot("zoom-in-" + i);
            }
            for (int i = 0; i < 6; i++) {
                ui.scrollAt(548, 240, -1);
                ui.shot("zoom-out-" + i);
            }
            context.runOnClient(c -> GT.log("[preview] session " + (PreviewSession.current() == null ? "none" : PreviewSession.current().file)));
            // 全屏
            ui.click(466, 163, 0);
            context.waitTicks(20);
            context.runOnClient(c -> GT.log("[preview] screen after fullscreen: " + (c.screen == null ? "none" : c.screen.getClass().getSimpleName())));
            ui.shot("fullscreen");
            ui.drag(320, 180, 380, 180);
            ui.shot("fullscreen-drag");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(10);
            context.runOnClient(c -> GT.log("[preview] screen after esc: " + (c.screen == null ? "none" : c.screen.getClass().getSimpleName())));
            ui.shot("after-fullscreen");
            // 自由视角
            ui.click(474, 163, 0);
            ui.hover(548, 240);
            ui.shot("freecam-on");
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 15);
            ui.shot("freecam-w");
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 10);
            ui.shot("freecam-space");
            ui.drag(548, 240, 568, 240);
            ui.shot("freecam-look");
            ui.click(474, 163, 0);
            ui.hover(548, 240);
            ui.shot("freecam-off");
        } finally {
            context.runOnClient(c -> c.setScreen(null));
            ui.normalWindow(oldScale);
        }
    }
}
