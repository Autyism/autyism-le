package com.autyism.ale.gametest;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

/**
 * 对照用：在开发客户端里把 Schematic Preview 当作“黑盒”运行（只看屏幕、只用鼠标键盘操作），
 * 记录它每个功能的样子和反应，作为 ALE 自己实现的行为参照。只在装了 Schematic Preview 时运行。
 */
@SuppressWarnings("UnstableApiUsage")
public final class SpBlackBoxGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("spbox")) return;
        if (!FabricLoader.getInstance().isModLoaded("schematicpreview")) {
            GT.log("[spbox] Schematic Preview not loaded, skipped");
            return;
        }
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 205.5 64 20.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 205.5) < 0.01, 200);
            PreviewFixtures.createAll(context, sp, 40);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, PreviewFixtures.HOUSE_MIN, PreviewFixtures.HOUSE_MAX,
                    PreviewFixtures.HOUSE_MIN.offset(0, 0, 14), "ale_house_placed");
            UiDriver ui = new UiDriver(context, "sp");
            ui.bigWindow();

            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(40);
            // 普通列表：选中其他格式 / 大投影 / 坏文件，看信息面板
            String[] rows = {"ale_house_struct", "ale_huge", "ale_terrain", "ale_tower", "broken"};
            int[] ys = {138, 155, 172, 189, 206};
            for (int i = 0; i < rows.length; i++) {
                long t0 = System.nanoTime();
                ui.click(100, ys[i], 0);
                context.waitTicks(i == 1 ? 200 : 80);
                GT.log("[ui] selected " + rows[i] + " waited " + (System.nanoTime() - t0) / 1000000 + " ms");
                ui.shot("panel-" + rows[i]);
            }
            context.runOnClient(c -> GT.log("[ui] fps " + c.getFps()));
            // Schematic Preview 的设置界面（经 Mod Menu 打开，与玩家一样）
            context.runOnClient(c -> c.setScreen(com.terraformersmc.modmenu.ModMenu.getConfigScreen("schematicpreview", null)));
            context.waitTicks(10);
            ui.shot("config-generic");
            dumpButtons(context, "config");
            for (String tab : new String[]{"Menu", "Preview"}) {
                if (context.computeOnClient(c -> GT.findButton(c, tab)) != null) {
                    GT.clickButton(context, tab);
                    context.waitTicks(5);
                    ui.shot("config-" + tab.toLowerCase());
                }
            }

            // 材料列表里的“替换”
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(10);
            ui.shot("material-list");
            // 点“Oak Planks”那一行（第一行）的 Replace
            ui.click(548, 80, 0);
            context.waitTicks(10);
            logScreen(context, "after replace click");
            ui.shot("replace-dialog");
            dumpButtons(context, "replace dialog");
            context.getInput().typeChars("spruce");
            context.waitTicks(5);
            ui.shot("replace-search-spruce");
            ui.hover(320, 140);
            ui.shot("replace-hover-cell");
            ui.click(320, 140, 0);
            context.waitTicks(3);
            ui.shot("replace-selected-cell");
            for (int i = 0; i < 40; i++) context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
            context.waitTicks(3);
            ui.shot("replace-search-cleared");
            context.getInput().typeChars("spruce_planks");
            context.waitTicks(5);
            ui.shot("replace-search-spruce-planks");
            ui.click(225, 115, 0);
            context.waitTicks(3);
            ui.shot("replace-picked");
            if (context.computeOnClient(c -> GT.findButton(c, "Ok")) != null) GT.clickButton(context, "Ok");
            else if (context.computeOnClient(c -> GT.findButton(c, "OK")) != null) GT.clickButton(context, "OK");
            context.waitTicks(10);
            logScreen(context, "after ok");
            ui.shot("replace-result");
            context.waitTicks(40);
            ui.shot("replace-result-later");
        } finally {
            context.runOnClient(c -> c.setScreen(null));
            new UiDriver(context, "sp").normalWindow(oldScale);
        }
    }

    /** 右键 (x, y) 打开图标对话框：text = null 不改文字，"" 清空；点 position 次“位置”；最后点 finish */
    static void iconDialog(ClientGameTestContext context, UiDriver ui, double x, double y, String text, int position, String finish, String label) {
        ui.click(x, y, 1);
        context.waitTicks(10);
        logScreen(context, "icon dialog " + label);
        if (context.computeOnClient(c -> c.screen instanceof GuiSchematicLoad)) {
            ui.shot("no-dialog-" + label);
            return;
        }
        ui.shot("dialog-open-" + label);
        if (text != null) {
            for (int i = 0; i < 40; i++) context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
            if (!text.isEmpty()) context.getInput().typeChars(text);
            context.waitTicks(2);
        }
        for (int i = 0; i < position; i++) {
            GT.clickButton(context, "Position");
            context.waitTicks(2);
        }
        ui.shot("dialog-before-" + finish + "-" + label);
        dumpButtons(context, "dialog " + label);
        GT.clickButton(context, finish);
        context.waitTicks(10);
        logScreen(context, "after " + finish + " " + label);
        ui.hover(300, 250);
        ui.shot("after-" + label);
    }

    static void logScreen(ClientGameTestContext context, String label) {
        context.runOnClient(c -> GT.log("[ui] screen " + label + ": " + (c.screen == null ? "none" : c.screen.getClass().getName())));
    }

    /** 记下当前界面上看得到的按钮文字和位置（MaLiLib 界面） */
    static void dumpButtons(ClientGameTestContext context, String label) {
        context.runOnClient(c -> {
            if (!(c.screen instanceof fi.dy.masa.malilib.gui.GuiBase gui)) {
                GT.log("[ui] " + label + ": screen " + (c.screen == null ? "none" : c.screen.getClass().getSimpleName()));
                return;
            }
            try {
                java.lang.reflect.Field f = fi.dy.masa.malilib.gui.GuiBase.class.getDeclaredField("buttons");
                f.setAccessible(true);
                java.lang.reflect.Field ds = fi.dy.masa.malilib.gui.button.ButtonBase.class.getDeclaredField("displayString");
                ds.setAccessible(true);
                StringBuilder sb = new StringBuilder("[ui] " + label + " buttons:");
                for (Object o : (java.util.List<?>) f.get(gui)) {
                    var b = (fi.dy.masa.malilib.gui.button.ButtonBase) o;
                    sb.append(" '").append(String.valueOf(ds.get(b)).replaceAll("§.", "")).append("'@")
                            .append(b.getX()).append(',').append(b.getY()).append('+').append(b.getWidth()).append('x').append(b.getHeight());
                }
                GT.log(sb.toString());
            } catch (ReflectiveOperationException e) {
                GT.log("[ui] " + label + ": " + e);
            }
        });
    }
}
