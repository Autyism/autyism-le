package com.autyism.ale.gametest;

import com.autyism.ale.browser.SchematicBrowserWidget;
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

/**
 * 装了 Schematic Preview 时 ALE 的版本让开：浏览器用的是它的列表（不是 ALE 的），没有 ALE 的预览会话，
 * 材料列表每行只有一个“替换”按钮（它的）。没装时跳过。
 */
@SuppressWarnings("UnstableApiUsage")
public final class StepAsideGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("stepaside")) return;
        if (!Previews.ORIGINAL_INSTALLED) {
            GT.log("[stepaside] Schematic Preview not installed, skipped");
            return;
        }
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        UiDriver ui = new UiDriver(context, "both");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("tp @a 205.5 64 20.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 205.5) < 0.01, 200);
            PreviewFixtures.createAll(context, sp, 0);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, PreviewFixtures.HOUSE_MIN, PreviewFixtures.HOUSE_MAX,
                    PreviewFixtures.HOUSE_MIN.offset(0, 0, 14), "ale_house_placed");
            ui.bigWindow();
            context.runOnClient(c -> c.setScreen(new GuiSchematicLoad()));
            context.waitTicks(20);
            String widget = context.computeOnClient(c -> GT.listWidget(c).getClass().getName());
            GT.log("[stepaside] browser list widget: " + widget);
            if (context.computeOnClient(c -> GT.listWidget(c) instanceof SchematicBrowserWidget)) {
                throw new AssertionError("[stepaside] ALE's browser widget is used although Schematic Preview is installed");
            }
            BrowserPreviewGameTest.clickEntry(context, ui, "ale_house.litematic", 0);
            context.waitTicks(40);
            ui.shot("browser");
            if (context.computeOnClient(c -> PreviewSession.current() != null)) throw new AssertionError("[stepaside] ALE opened its own preview");

            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(10);
            ui.shot("material-list");
            // 每一行恰好一个“替换”按钮（Schematic Preview 的）；这里只检查，不点。
            // 列表只给看得到的行建按钮，所以检查当前看得到的所有行
            checkOneReplacePerRow(context);
            GT.log("[stepaside] OK: with Schematic Preview installed, only its browser, preview and Replace buttons are shown");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> c.setScreen(null));
            ui.normalWindow(oldScale);
        }
    }

    private static void checkOneReplacePerRow(ClientGameTestContext context) {
        java.util.List<String> rows = context.computeOnClient(c -> {
            try {
                Object list = GT.listWidget(c);
                java.lang.reflect.Field lw = fi.dy.masa.malilib.gui.widgets.WidgetListBase.class.getDeclaredField("listWidgets");
                lw.setAccessible(true);
                java.lang.reflect.Field sub = fi.dy.masa.malilib.gui.widgets.WidgetContainer.class.getDeclaredField("subWidgets");
                sub.setAccessible(true);
                java.lang.reflect.Field ds = fi.dy.masa.malilib.gui.button.ButtonBase.class.getDeclaredField("displayString");
                ds.setAccessible(true);
                java.util.List<String> out = new java.util.ArrayList<>();
                for (Object w : (java.util.List<?>) lw.get(list)) {
                    Object entry = ((fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase<?>) w).getEntry();
                    if (!(entry instanceof fi.dy.masa.litematica.materials.MaterialListEntry me)) continue;
                    int count = 0;
                    for (Object o : (java.util.List<?>) sub.get(w)) {
                        if (o instanceof fi.dy.masa.malilib.gui.button.ButtonBase b && String.valueOf(ds.get(b)).contains("Replace")) count++;
                    }
                    out.add(me.getStack().getItem() + "=" + count);
                }
                return out;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        GT.log("[stepaside] Replace buttons per visible row: " + rows);
        if (rows.size() < 3) throw new AssertionError("[stepaside] only " + rows.size() + " material rows visible");
        for (String row : rows) {
            if (!row.endsWith("=1")) throw new AssertionError("[stepaside] not exactly one Replace button in the row " + row);
        }
    }
}
