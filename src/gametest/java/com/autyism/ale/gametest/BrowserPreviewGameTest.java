package com.autyism.ale.gametest;

import com.autyism.ale.browser.BrowserLayout;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.preview.FullscreenPreviewScreen;
import com.autyism.ale.preview.PreviewCamera;
import com.autyism.ale.preview.PreviewModel;
import com.autyism.ale.preview.PreviewSession;
import com.autyism.ale.preview.Previews;
import com.autyism.ale.preview.ThumbnailCache;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 投影浏览器（ALE 自己的版本，-PnoSchematicPreview）：
 * 显示方式循环、缩略图（含“太大”和坏文件）、很多文件时的流畅度、信息面板的 3D 预览（拖动、滚轮、全屏、自由视角）、
 * 大投影不卡死。全部用鼠标键盘操作真实界面，每一步截图。
 */
@SuppressWarnings("UnstableApiUsage")
public final class BrowserPreviewGameTest implements FabricClientGameTest {
    private final List<String> problems = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("preview")) return;
        if (Previews.ORIGINAL_INSTALLED) {
            GT.log("[preview] Schematic Preview is installed: ALE's own browser previews step aside, skipped");
            return;
        }
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        UiDriver ui = new UiDriver(context, "ale-preview");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 205.5 64 20.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 205.5) < 0.01, 200);
            PreviewFixtures.createAll(context, sp, 120);
            Path dir = PreviewFixtures.dir(context);
            context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.LIST));
            ui.bigWindow();

            // ---- 显示方式：左上角按钮循环一圈
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(20);
            ui.shot("list");
            ui.hover(21, 35);
            ui.shot("toggle-tooltip");
            BrowserLayout[] expected = {BrowserLayout.PREVIEW_LIST, BrowserLayout.GRID_5, BrowserLayout.GRID_4, BrowserLayout.GRID_3, BrowserLayout.LIST};
            for (BrowserLayout want : expected) {
                ui.click(21, 35, 0);
                waitThumbnails(context, dir, want);
                BrowserLayout now = context.computeOnClient(c -> AleConfigs.Browser.layout());
                if (now != want) problems.add("layout after click is " + now + ", expected " + want);
                ui.hover(300, 340);
                ui.shot("mode-" + now.getStringValue());
            }
            checkThumbnail(context, dir.resolve("ale_house.litematic"), ThumbnailCache.State.READY);
            checkThumbnail(context, dir.resolve("ale_house_struct.nbt"), ThumbnailCache.State.READY);
            checkThumbnail(context, dir.resolve("ale_tower.litematic"), ThumbnailCache.State.READY);
            checkThumbnail(context, dir.resolve("ale_terrain.schem"), ThumbnailCache.State.TOO_BIG);
            checkThumbnail(context, dir.resolve("ale_huge.schem"), ThumbnailCache.State.TOO_BIG);
            checkThumbnail(context, dir.resolve("broken.litematic"), ThumbnailCache.State.FAILED);

            // ---- 很多文件：网格里进 many/（120 个），滚动时也要流畅
            context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.GRID_5));
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(10);
            clickEntry(context, ui, "many", 0);
            context.waitTicks(5);
            long frames0 = frames(context);
            long t0 = System.nanoTime();
            context.waitTicks(100);
            ui.shot("many-grid");
            for (int i = 0; i < 6; i++) ui.scrollAt(300, 200, -1);
            context.waitTicks(60);
            ui.shot("many-grid-scrolled");
            double seconds = (System.nanoTime() - t0) / 1e9;
            long frames = frames(context) - frames0;
            GT.log("[preview] many/ (120 files): " + frames + " frames in " + String.format("%.1f", seconds) + " s while thumbnails were built");
            if (frames / seconds < 15) problems.add("browser too slow with 120 files: " + frames / seconds + " fps");

            // ---- 信息面板的 3D 预览
            context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.LIST));
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(5);
            // Litematica 记住了上次的文件夹（many/）：回到根目录
            context.runOnClient(c -> ((fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase) GT.listWidget(c)).switchToDirectory(dir));
            context.waitTicks(5);
            clickEntry(context, ui, "ale_house.litematic", 0);
            waitModel(context, 200);
            ui.hover(300, 340);
            ui.shot("panel-house");
            float[] c0 = camera(context);
            ui.drag(548, 260, 588, 260);
            float[] c1 = camera(context);
            ui.shot("panel-drag-right");
            if (!(c1[0] - c0[0] > 30 && c1[0] - c0[0] < 60)) problems.add("drag right 40 px turned yaw by " + (c1[0] - c0[0]));
            ui.drag(548, 260, 548, 230);
            float[] c2 = camera(context);
            ui.shot("panel-drag-up");
            if (!(c1[1] - c2[1] > 20)) problems.add("drag up 30 px changed pitch by " + (c2[1] - c1[1]));
            ui.scrollAt(548, 260, 1);
            float[] c3 = camera(context);
            ui.shot("panel-zoom-in");
            if (!(c3[2] < c2[2])) problems.add("scroll up did not move closer: " + c2[2] + " -> " + c3[2]);
            ui.scrollAt(548, 260, -2);
            float[] c4 = camera(context);
            ui.shot("panel-zoom-out");
            if (!(c4[2] > c3[2])) problems.add("scroll down did not move away");
            int[][] panel = context.computeOnClient(c -> ((com.autyism.ale.browser.SchematicBrowserWidget) GT.listWidget(c)).panel().layout());
            double fx = panel[0][0] + 4, fy = panel[0][1] + 4, cx = panel[1][0] + 4, cy = panel[1][1] + 4;
            ui.hover(fx, fy);
            ui.shot("hover-fullscreen-button");

            // 全屏：同一个相机，Esc 回来角度不变
            ui.click(fx, fy, 0);
            context.waitTicks(10);
            if (!context.computeOnClient(c -> c.screen instanceof FullscreenPreviewScreen)) problems.add("fullscreen button did not open the full screen preview");
            ui.shot("fullscreen");
            ui.drag(320, 180, 380, 180);
            float[] f1 = camera(context);
            ui.shot("fullscreen-drag");
            if (Math.abs(f1[0] - c4[0]) < 20) problems.add("drag in full screen did not turn the preview");
            ui.scrollAt(320, 180, 1);
            ui.shot("fullscreen-zoom");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(10);
            if (!context.computeOnClient(c -> c.screen instanceof GuiSchematicLoad)) problems.add("Esc in full screen did not go back to the browser");
            float[] f2 = camera(context);
            ui.hover(300, 340);
            ui.shot("back-from-fullscreen");
            if (Math.abs(f2[0] - f1[0]) > 0.01 || f2[2] >= f1[2]) problems.add("camera not kept after full screen");

            // 自由视角：移动键飞、拖动转视线、关掉后回到原来的角度
            float[] orbit = camera(context);
            ui.hover(cx, cy);
            ui.shot("hover-freecam-button");
            ui.click(cx, cy, 0);
            if (!context.computeOnClient(c -> PreviewSession.isFreecam())) problems.add("freecam button did not turn on free camera");
            ui.hover(548, 260);
            Vector3f e0 = eye(context);
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_W, 20);
            Vector3f e1 = eye(context);
            ui.shot("freecam-forward");
            if (e0.distance(e1) < 1.0F) problems.add("W did not move the free camera (" + e0 + " -> " + e1 + ")");
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_SPACE, 10);
            Vector3f e2 = eye(context);
            if (!(e2.y > e1.y + 0.5F)) problems.add("space did not move the free camera up");
            float[] l0 = camera(context);
            ui.drag(548, 260, 578, 260);
            float[] l1 = camera(context);
            ui.shot("freecam-look");
            if (Math.abs(l1[0] - l0[0]) < 15) problems.add("drag did not turn the free camera");
            boolean searchOpened = context.computeOnClient(c -> GT.searchBarOpen(c));
            if (searchOpened) problems.add("movement keys opened the browser's search bar while flying");
            // 全屏里也能飞
            ui.click(fx, fy, 0);
            context.waitTicks(5);
            Vector3f g0 = eye(context);
            context.getInput().holdKeyFor(GLFW.GLFW_KEY_D, 15);
            Vector3f g1 = eye(context);
            ui.shot("fullscreen-freecam-strafe");
            if (g0.distance(g1) < 1.0F) problems.add("D did not move the free camera in full screen");
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            ui.click(cx, cy, 0);
            float[] back = camera(context);
            ui.hover(300, 340);
            ui.shot("freecam-off");
            if (Math.abs(back[0] - orbit[0]) > 0.01 || Math.abs(back[2] - orbit[2]) > 0.01) problems.add("turning freecam off did not return to the orbit view");

            // ---- 大投影 / 带水的地形 / 坏文件：面板里不卡、不崩
            // 方块实体：箱子（双箱子朝向）、床、告示牌文字、旗帜、头颅、潜影盒、附魔台、钟、讲台、营火、陶罐
            selectAndWatch(context, ui, "ale_block_entities.litematic", 200);
            int beCount = context.computeOnClient(c -> PreviewSession.current() == null ? -1 : PreviewSession.current().model().blockEntityCount());
            GT.log("[preview] block entities in the preview: " + beCount);
            if (beCount < 10) problems.add("only " + beCount + " block entities in the preview of ale_block_entities");
            selectAndWatch(context, ui, "ale_huge.schem", 600);
            selectAndWatch(context, ui, "ale_terrain.schem", 400);
            selectAndWatch(context, ui, "ale_house_struct.nbt", 200);
            clickEntry(context, ui, "broken.litematic", 0);
            context.waitTicks(40);
            ui.hover(300, 340);
            ui.shot("panel-broken");

            for (String p : problems) GT.log("[preview] PROBLEM " + p);
            if (!problems.isEmpty()) throw new AssertionError("[preview] " + problems);
            GT.log("[preview] OK: layouts, thumbnails, 120-file folder, drag/zoom, full screen, free camera, big and broken files");
        } finally {
            context.runOnClient(c -> {
                PreviewSession.setFreecam(false);
                c.setScreen(null);
            });
            ui.normalWindow(oldScale);
        }
    }

    /** 点击浏览器里的某个条目（找不到就失败） */
    static void clickEntry(ClientGameTestContext context, UiDriver ui, String name, int button) {
        double[] c = context.computeOnClient(cl -> GT.entryCenter(cl, name));
        if (c == null) throw new AssertionError("entry " + name + " is not on screen");
        ui.click(c[0], c[1], button);
    }

    /** 等屏幕上的缩略图都生成完（列表模式不用等） */
    private static void waitThumbnails(ClientGameTestContext context, Path dir, BrowserLayout layout) {
        if (layout == BrowserLayout.LIST) {
            context.waitTicks(5);
            return;
        }
        Path house = dir.resolve("ale_house.litematic");
        for (int i = 0; i < 200; i++) {
            ThumbnailCache.State s = context.computeOnClient(c -> Previews.thumbnails().stateOf(house));
            if (s != null && s != ThumbnailCache.State.LOADING) break;
            context.waitTick();
        }
        context.waitTicks(20);
    }

    private void checkThumbnail(ClientGameTestContext context, Path file, ThumbnailCache.State want) {
        ThumbnailCache.State got = context.computeOnClient(c -> Previews.thumbnails().stateOf(file));
        GT.log("[preview] thumbnail " + file.getFileName() + ": " + got);
        if (got != want) problems.add("thumbnail of " + file.getFileName() + " is " + got + ", expected " + want);
    }

    private static long frames(ClientGameTestContext context) {
        return context.computeOnClient(c -> GT.frameCounter());
    }

    private static void waitModel(ClientGameTestContext context, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            boolean done = context.computeOnClient(c -> PreviewSession.current() != null && PreviewSession.current().model().isComplete()
                    && PreviewSession.current().model().status() != PreviewModel.Status.LOADING);
            if (done) break;
            context.waitTick();
        }
        context.waitTicks(5);
    }

    /** {yaw, pitch, distance} */
    private static float[] camera(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            PreviewCamera cam = PreviewSession.current() == null ? null : PreviewSession.current().camera();
            return cam == null ? new float[3] : new float[]{cam.yaw(), cam.pitch(), cam.distance()};
        });
    }

    private static Vector3f eye(ClientGameTestContext context) {
        return context.computeOnClient(c -> PreviewSession.current() == null ? new Vector3f() : PreviewSession.current().camera().eye());
    }

    /** 选中一个投影，看它加载时游戏是否一直在走（没有卡住），截图 */
    private void selectAndWatch(ClientGameTestContext context, UiDriver ui, String name, int maxTicks) {
        clickEntry(context, ui, name, 0);
        long frames0 = frames(context);
        long t0 = System.nanoTime();
        long worst = 0;
        long last = t0;
        int ticks = 0;
        for (; ticks < maxTicks; ticks++) {
            context.waitTick();
            long now = System.nanoTime();
            worst = Math.max(worst, now - last);
            last = now;
            boolean done = context.computeOnClient(c -> PreviewSession.current() != null && PreviewSession.current().model().isComplete()
                    && PreviewSession.current().model().status() != PreviewModel.Status.LOADING);
            if (done) break;
        }
        double seconds = (System.nanoTime() - t0) / 1e9;
        long frames = frames(context) - frames0;
        String info = context.computeOnClient(c -> {
            PreviewSession s = PreviewSession.current();
            if (s == null) return "no session";
            PreviewModel m = s.model();
            return m.status() + " " + m.sizeX() + "x" + m.sizeY() + "x" + m.sizeZ() + (m.isTruncated() ? " (partly)" : "") + " complete=" + m.isComplete();
        });
        GT.log("[preview] " + name + ": " + info + ", " + ticks + " ticks, " + String.format("%.1f s, %d frames, longest tick %.0f ms",
                seconds, frames, worst / 1e6));
        if (worst > 2_000_000_000L) problems.add(name + ": the game stalled for " + worst / 1_000_000 + " ms");
        ui.hover(300, 340);
        ui.shot("panel-" + name);
    }
}
