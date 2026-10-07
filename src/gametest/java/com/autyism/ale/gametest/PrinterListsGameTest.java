package com.autyism.ale.gametest;

import com.autyism.ale.gui.GuiBlockPicker;
import fi.dy.masa.malilib.config.IConfigStringList;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiStringListEdit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 打印机的方块列表（破基岩方块列表、填充方块列表）也走 ALE 的方块选择器：截图给用户看界面。
 * 只在同时装了打印机时运行。
 */
@SuppressWarnings("UnstableApiUsage")
public final class PrinterListsGameTest implements FabricClientGameTest {
    private static final String PRINTER = "litematica-printer-autyism";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("printerlists")) return;
        if (!FabricLoader.getInstance().isModLoaded(PRINTER)) {
            GT.log("[printerlists] printer not loaded, skipped");
            return;
        }
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            runList(context, "Bedrock", "BEDROCK", "BLOCK_LIST", "end_portal_frame", "bedrock");
            runList(context, "Fill", "FILL", "FILL_BLOCK_LIST", "stone_bricks", "fill");
        } finally {
            context.runOnClient(c -> c.setScreen(null));
        }
    }

    private static void runList(ClientGameTestContext context, String group, String tab, String field, String search, String tag) {
        IConfigStringList list = context.computeOnClient(c -> (IConfigStringList) staticField("com.autyism.printer.config.Configs$" + group, field));
        List<String> before = context.computeOnClient(c -> List.copyOf(list.getStrings()));
        try {
            // 打印机设置界面对应的分页
            Screen[] ui = new Screen[1];
            context.runOnClient(c -> {
                try {
                    Class<?> cls = Class.forName("com.autyism.printer.gui.ConfigUi");
                    Class<?> tabCls = Class.forName("com.autyism.printer.gui.ConfigUi$Tab");
                    Field f = cls.getDeclaredField("tab");
                    f.setAccessible(true);
                    f.set(null, Enum.valueOf(tabCls.asSubclass(Enum.class), tab));
                    ui[0] = (Screen) cls.getConstructor(Screen.class).newInstance((Screen) null);
                    c.setScreen(ui[0]);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            context.waitTicks(5);
            GT.shot(context, "ale-printer-" + tag + "-tab");
            context.runOnClient(c -> c.setScreen(new GuiStringListEdit(list, (GuiConfigsBase) ui[0], null, ui[0])));
            context.waitTicks(5);
            GT.shot(context, "ale-printer-" + tag + "-list");
            GT.clickButton(context, "Pick blocks");
            context.waitFor(c -> c.screen instanceof GuiBlockPicker, 40);
            context.getInput().typeChars(search);
            context.waitTicks(3);
            GT.shot(context, "ale-printer-" + tag + "-picker");
            GT.log("[printerlists] " + tag + " OK, list=" + context.computeOnClient(c -> List.copyOf(list.getStrings())));
        } finally {
            context.runOnClient(c -> {
                list.setStrings(before);
                c.setScreen(null);
            });
        }
    }

    private static Object staticField(String cls, String name) {
        try {
            Field f = Class.forName(cls).getField(name);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
