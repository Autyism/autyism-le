//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.gametest;

import com.autyism.ale.browser.BrowserEntryWidget;
import com.autyism.ale.browser.BrowserLayout;
import com.autyism.ale.browser.FolderIconScreen;
import com.autyism.ale.browser.FolderIcons;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.gui.GuiBlockPicker;
import com.autyism.ale.preview.Previews;
import fi.dy.masa.litematica.gui.GuiSchematicLoad;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import com.mojang.blaze3d.platform.InputConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 浏览器条目的自定义图标（ALE 自己的版本）：悬停提示、右键对话框、物品 ID / 选择界面、三种位置、
 * 位置改动立即可见、取消恢复、默认、无效 ID、回车确定，以及保存在 ALE 的设置文件里。
 */
@SuppressWarnings("UnstableApiUsage")
public final class FolderIconGameTest implements FabricClientGameTest {
    private final List<String> problems = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("icons")) return;
        if (Previews.ORIGINAL_INSTALLED) {
            GT.log("[icons] Schematic Preview is installed: ALE's own icons step aside, skipped");
            return;
        }
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        UiDriver ui = new UiDriver(context, "ale-icons");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("tp @a 205.5 64 20.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 205.5) < 0.01, 200);
            PreviewFixtures.createAll(context, sp, 3);
            Path dir = PreviewFixtures.dir(context);
            context.runOnClient(c -> {
                FolderIcons.read(null);
                AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.PREVIEW_LIST);
            });
            ui.bigWindow();
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(40);

            // 悬停提示
            int[] a = icon(context, "folder_a");
            ui.hover(a[0] + a[2] / 2.0, a[1] + a[3] / 2.0);
            ui.shot("hover-folder-icon");

            // 物品 + 大图标：位置改动在对话框后面立即可见，确定后保存
            openDialog(context, ui, "folder_a");
            ui.shot("dialog-open");
            context.getInput().typeChars("minecraft:redstone_block");
            GT.clickButton(context, "Icon:");
            context.waitTicks(3);
            ui.shot("dialog-large-live");
            GT.clickButton(context, "OK");
            context.waitTicks(10);
            expect(context, dir.resolve("folder_a"), "minecraft:redstone_block", FolderIcons.Placement.LARGE);
            ui.hover(300, 340);
            ui.shot("list-folder-a-large");

            // 取消：位置改了也恢复原样
            openDialog(context, ui, "folder_a");
            GT.clickButton(context, "Icon:");
            context.waitTicks(3);
            ui.shot("dialog-before-cancel");
            GT.clickButton(context, "Cancel");
            context.waitTicks(5);
            expect(context, dir.resolve("folder_a"), "minecraft:redstone_block", FolderIcons.Placement.LARGE);

            // many/：不填物品，“小图标 + 里面投影的预览”
            openDialog(context, ui, "many");
            GT.clickButton(context, "Icon:");
            GT.clickButton(context, "Icon:");
            GT.clickButton(context, "OK");
            context.waitTicks(40);
            expect(context, dir.resolve("many"), "", FolderIcons.Placement.SMALL_WITH_PREVIEW);
            ui.hover(300, 340);
            ui.shot("list-many-with-preview");

            // 投影文件也能换图标；回车 = 确定
            openDialog(context, ui, "ale_tower.litematic");
            context.getInput().typeChars("minecraft:diamond_block");
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(10);
            expect(context, dir.resolve("ale_tower.litematic"), "minecraft:diamond_block", FolderIcons.Placement.SMALL);

            // 用物品选择界面挑
            openDialog(context, ui, "folder_b");
            GT.clickButton(context, "Pick");
            context.waitFor(c -> c.screen instanceof GuiBlockPicker, 40);
            context.getInput().typeChars("emerald_block");
            context.waitTicks(3);
            ui.shot("picker");
            clickPickerRow(context);
            GT.clickButton(context, "Done");
            context.waitFor(c -> c.screen instanceof FolderIconScreen, 40);
            ui.shot("dialog-after-picker");
            GT.clickButton(context, "OK");
            context.waitTicks(10);
            expect(context, dir.resolve("folder_b"), "minecraft:emerald_block", FolderIcons.Placement.SMALL);

            // 无效的 ID：对话框不关，提示错误
            openDialog(context, ui, "folder_b");
            for (int i = 0; i < 40; i++) context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
            context.getInput().typeChars("minecraft:not_an_item");
            GT.clickButton(context, "OK");
            context.waitTicks(5);
            ui.shot("dialog-invalid-id");
            if (!context.computeOnClient(c -> c.screen instanceof FolderIconScreen)) problems.add("dialog closed with an invalid item id");
            GT.clickButton(context, "Cancel");
            context.waitTicks(5);
            expect(context, dir.resolve("folder_b"), "minecraft:emerald_block", FolderIcons.Placement.SMALL);

            // 默认：恢复成文件夹图标
            openDialog(context, ui, "folder_a");
            GT.clickButton(context, "Default");
            context.waitTicks(5);
            if (context.computeOnClient(c -> FolderIcons.get(dir.resolve("folder_a"))) != null) problems.add("Default did not remove the icon of folder_a");

            ui.hover(300, 340);
            ui.shot("list-final");
            context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.GRID_4));
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(40);
            ui.hover(300, 340);
            ui.shot("grid-final");
            context.runOnClient(c -> AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.LIST));
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(10);
            ui.hover(300, 340);
            ui.shot("plain-list-final");

            // 存在 ALE 的设置文件里（以相对 schematics 的路径为键）
            Path config = FabricLoader.getInstance().getConfigDir().resolve("autyism-le.json");
            String json = Files.exists(config) ? Files.readString(config) : "";
            if (!json.contains("\"FolderIcons\"") || !json.contains("\"folder_b\"") || !json.contains("\"ale_tower.litematic\"") || !json.contains("\"many\"")) {
                problems.add("icons not stored in " + config + ": " + json.substring(Math.max(0, json.indexOf("FolderIcons") - 2)));
            }

            for (String p : problems) GT.log("[icons] PROBLEM " + p);
            if (!problems.isEmpty()) throw new AssertionError("[icons] " + problems);
            GT.log("[icons] OK: tooltip, dialog, item id, picker, large/small/preview positions, cancel, default, invalid id, saved in ALE's config");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        } finally {
            context.runOnClient(c -> {
                c.setScreen(null);
                AleConfigs.Browser.LAYOUT.setOptionListValue(BrowserLayout.LIST);
            });
            ui.normalWindow(oldScale);
        }
    }

    private static int[] icon(ClientGameTestContext context, String name) {
        // 不在屏幕上就用滚轮翻（带预览的列表一屏只放得下七八行）
        BrowserPreviewGameTest.reveal(context, name);
        return context.computeOnClient(c -> {
            Object w = GT.entryWidget(c, name);
            if (!(w instanceof BrowserEntryWidget e)) throw new AssertionError("entry " + name + " not on screen");
            return e.iconArea();
        });
    }

    private void openDialog(ClientGameTestContext context, UiDriver ui, String name) {
        int[] a = icon(context, name);
        ui.click(a[0] + a[2] / 2.0, a[1] + a[3] / 2.0, 1);
        context.waitFor(c -> c.screen instanceof FolderIconScreen, 40);
    }

    private void expect(ClientGameTestContext context, Path path, String item, FolderIcons.Placement placement) {
        FolderIcons.Choice got = context.computeOnClient(c -> FolderIcons.get(path));
        GT.log("[icons] " + path.getFileName() + " -> " + got);
        if (got == null || !Objects.equals(got.itemId(), item) || got.placement() != placement) {
            problems.add(path.getFileName() + " is " + got + ", expected " + item + " / " + placement);
        }
    }

    /** 物品选择界面：点右边第一行（加入） */
    private static void clickPickerRow(ClientGameTestContext context) {
        double[] pos = context.computeOnClient(c -> {
            try {
                GuiBlockPicker gui = (GuiBlockPicker) c.screen;
                java.lang.reflect.Field fx = GuiBlockPicker.class.getDeclaredField("panelX");
                java.lang.reflect.Field fw = GuiBlockPicker.class.getDeclaredField("colW");
                java.lang.reflect.Field ft = GuiBlockPicker.class.getDeclaredField("listTop");
                fx.setAccessible(true);
                fw.setAccessible(true);
                ft.setAccessible(true);
                int x = fx.getInt(gui) + 10 + fw.getInt(gui) + 10 + 40, y = ft.getInt(gui) + 10;
                double s = c.getWindow().getGuiScale();
                return new double[]{x * s, y * s};
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }
}
//?}
