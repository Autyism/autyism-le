//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.gametest;

import com.autyism.ale.browser.BrowserEntryWidget;
import com.autyism.ale.browser.BrowserLayout;
import com.autyism.ale.browser.SchematicBrowserWidget;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.preview.PreviewSession;
import com.autyism.ale.preview.Previews;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 并排对照：同一组投影、同样的窗口，按同样的顺序操作，装了 Schematic Preview 时拍它（cmp-sp-*），
 * 没装时拍 ALE 自己的版本（cmp-ale-*）。两次运行的截图一一对应，用来比较行为。
 */
@SuppressWarnings("UnstableApiUsage")
public final class CompareGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.explicit("compare")) return;
        boolean original = Previews.ORIGINAL_INSTALLED;
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        UiDriver ui = new UiDriver(context, original ? "cmp-sp" : "cmp-ale");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 205.5 64 20.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 205.5) < 0.01, 200);
            PreviewFixtures.createAll(context, sp, 0);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, PreviewFixtures.HOUSE_MIN, PreviewFixtures.HOUSE_MAX,
                    PreviewFixtures.HOUSE_MIN.offset(0, 0, 14), "ale_house_placed");
            if (!original) context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.LIST));
            ui.bigWindow();

            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(40);
            ui.hover(300, 345);
            ui.shot("1-list");
            String[] modes = {"2-preview-list", "3-grid5", "4-grid4", "5-grid3", "6-list-again"};
            for (String m : modes) {
                ui.click(21, 35, 0);
                context.waitTicks(80);
                ui.hover(300, 345);
                ui.shot(m);
            }
            // 信息面板的 3D 预览
            BrowserPreviewGameTest.clickEntry(context, ui, "ale_house.litematic", 0);
            context.waitTicks(80);
            ui.hover(300, 345);
            ui.shot("7-panel");
            ui.drag(548, 260, 588, 260);
            ui.hover(300, 345);
            ui.shot("8-drag-right-40");
            ui.drag(548, 260, 548, 230);
            ui.hover(300, 345);
            ui.shot("9-drag-up-30");
            ui.scrollAt(548, 260, 1);
            ui.hover(300, 345);
            ui.shot("10-scroll-in-1");
            ui.scrollAt(548, 260, -3);
            ui.hover(300, 345);
            ui.shot("11-scroll-out-3");
            double[] full = original ? new double[]{466, 163} : button(context, 0);
            double[] cam = original ? new double[]{474, 163} : button(context, 1);
            ui.click(full[0], full[1], 0);
            context.waitTicks(20);
            ui.shot("12-fullscreen");
            boolean calib = !original && System.getProperty("ale.gt", "").contains("calib");
            if (calib) calibrate(context, ui, "yaw", new float[]{10, 13, 15, 17, 19, 21, 24, 30}, true);
            ui.drag(320, 180, 380, 180);
            ui.shot("13-fullscreen-drag");
            if (calib) calibrate(context, ui, "pitch", new float[]{25, 30, 35, 40, 45, 50, 55, 60}, false);
            ui.drag(320, 180, 320, 120);
            ui.shot("13b-fullscreen-drag-up");
            ui.scrollAt(320, 180, 1);
            ui.shot("13c-fullscreen-scroll-in");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitTicks(10);
            ui.hover(300, 345);
            ui.shot("14-back");
            ui.click(cam[0], cam[1], 0);
            ui.hover(548, 260);
            context.getInput().holdKeyFor(InputConstants.KEY_W, 15);
            ui.shot("15-freecam-w");
            ui.drag(548, 260, 568, 260);
            ui.shot("16-freecam-look");
            ui.scrollAt(548, 260, 1);
            ui.shot("17-freecam-scroll");
            ui.click(cam[0], cam[1], 0);
            ui.hover(300, 345);
            ui.shot("18-freecam-off");

            // 文件夹图标（带预览的列表里右键文件夹的小图标）
            ui.click(21, 35, 0);
            context.waitTicks(40);
            double[] icon = folderIcon(context, original);
            ui.hover(icon[0], icon[1]);
            ui.shot("19-icon-hover");
            ui.click(icon[0], icon[1], 1);
            context.waitTicks(10);
            ui.shot("20-icon-dialog");
            context.getInput().typeChars("minecraft:redstone_block");
            if (context.computeOnClient(c -> GT.findButton(c, original ? "Position" : "Icon:")) != null) GT.clickButton(context, original ? "Position" : "Icon:");
            context.waitTicks(3);
            ui.shot("21-icon-dialog-filled");
            GT.clickButton(context, "OK");
            context.waitTicks(20);
            ui.hover(300, 345);
            ui.shot("22-icon-set");
            for (int i = 0; i < 4; i++) {
                ui.click(21, 35, 0);
                context.waitTicks(20);
            }
            ui.hover(300, 345);
            ui.shot("23-icon-in-list");
            ui.click(21, 35, 0);
            ui.click(21, 35, 0);
            context.waitTicks(40);
            ui.hover(300, 345);
            ui.shot("24-icon-in-grid");

            // 材料列表的“替换”
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(10);
            ui.shot("25-material-list");
            MaterialReplaceGameTest.clickReplace(context, Items.OAK_PLANKS);
            context.waitTicks(10);
            ui.shot("26-replace-dialog");
            if (original) {
                // 原作的搜索框要先点一下才能输入
                ui.click(320, 92, 0);
            }
            context.getInput().typeChars("spruce_planks");
            context.waitTicks(5);
            ui.shot("27-replace-search");
            GT.clickButton(context, original ? "Ok" : "OK");
            context.waitTicks(5);
            ui.shot("28-replace-ok-nothing-picked");
            if (context.computeOnClient(c -> !(c.screen instanceof GuiMaterialList))) {
                // 还在对话框里：点第一个格子再确定
                double[] cell = original ? new double[]{230, 115} : firstCell(context);
                ui.click(cell[0], cell[1], 0);
                context.waitTicks(3);
                ui.shot("29-replace-picked");
                GT.clickButton(context, original ? "Ok" : "OK");
                context.waitTicks(10);
            }
            ui.shot("30-replace-result");
            context.waitTicks(40);
            ui.shot("31-replace-result-later");

            // 带实体（盔甲架、展示框、画、矿车、船）的投影：网格缩略图、信息面板、带预览的列表
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(80);
            ui.hover(300, 345);
            ui.shot("32-entities-grid");
            for (int i = 0; i < 3; i++) {
                ui.click(21, 35, 0);
                context.waitTicks(10);
            }
            BrowserPreviewGameTest.clickEntry(context, ui, "ale_entities.litematic", 0);
            context.waitTicks(80);
            ui.hover(300, 345);
            ui.shot("33-entities-panel");
            ui.click(21, 35, 0);
            context.waitTicks(60);
            ui.hover(300, 345);
            ui.shot("34-entities-preview-list");
            GT.log("[compare] done (" + (original ? "Schematic Preview" : "ALE") + ")");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                PreviewSession.setFreecam(false);
                c.setScreen(null);
            });
            ui.normalWindow(oldScale);
        }
    }

    /**
     * 只在 ALE 这一边、-Pgt 里带 calib 时用：把全屏预览分别转若干个已知角度拍下来，
     * 和原作同一步的截图比对，找出原作拖动对应的角度。拍完相机恢复原样。
     */
    private static void calibrate(ClientGameTestContext context, UiDriver ui, String name, float[] degrees, boolean yaw) {
        com.autyism.ale.preview.PreviewCamera saved = context.computeOnClient(c -> {
            com.autyism.ale.preview.PreviewCamera copy = new com.autyism.ale.preview.PreviewCamera();
            copy.copyFrom(PreviewSession.current().camera());
            return copy;
        });
        for (float deg : degrees) {
            context.runOnClient(c -> {
                com.autyism.ale.preview.PreviewCamera cam = PreviewSession.current().camera();
                cam.copyFrom(saved);
                double w = c.screen.width, h = c.screen.height;
                if (yaw) cam.drag(deg / 180.0 * w, 0, w, h);
                else cam.drag(0, -deg / 180.0 * h, w, h);
            });
            context.waitTicks(2);
            ui.shot("calib-" + name + "-" + (int) deg);
        }
        context.runOnClient(c -> PreviewSession.current().camera().copyFrom(saved));
        context.waitTicks(2);
    }

    private static double[] button(ClientGameTestContext context, int index) {
        int[][] l = context.computeOnClient(c -> ((SchematicBrowserWidget) GT.listWidget(c)).panel().layout());
        return new double[]{l[index][0] + l[index][2] / 2.0, l[index][1] + l[index][3] / 2.0};
    }

    private static double[] folderIcon(ClientGameTestContext context, boolean original) {
        if (original) return new double[]{73, 62};
        int[] a = context.computeOnClient(c -> ((BrowserEntryWidget) GT.entryWidget(c, "folder_a")).iconArea());
        return new double[]{a[0] + a[2] / 2.0, a[1] + a[3] / 2.0};
    }

    private static double[] firstCell(ClientGameTestContext context) {
        int[] g = context.computeOnClient(c -> ((com.autyism.ale.replace.ReplaceBlockScreen) c.screen).gridGeometry());
        return new double[]{g[0] + g[2] / 2.0, g[1] + g[2] / 2.0};
    }
}
//?}
