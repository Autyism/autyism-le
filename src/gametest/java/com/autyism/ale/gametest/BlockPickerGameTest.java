package com.autyism.ale.gametest;

import com.autyism.ale.gui.GuiBlockPicker;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.malilib.gui.GuiStringListEdit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 需求 3：方块列表的选择界面。以 Litematica 的“可忽略的已存在方块”列表为例：
 * 列表编辑框 → “选择方块…” → 搜索 → 点击加入 → 完成，列表里出现 minecraft:cobblestone；再点 − 移除。
 */
@SuppressWarnings("UnstableApiUsage")
public final class BlockPickerGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("picker")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            context.runOnClient(c -> {
                Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.setStrings(List.of());
                var configs = new fi.dy.masa.litematica.gui.GuiConfigs();
                c.setScreen(configs);
                c.setScreen(new GuiStringListEdit(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS, configs, null, configs));
            });
            context.waitTicks(5);
            GT.shot(context, "ale-picker-list-edit");
            GT.clickButton(context, "Pick blocks");
            context.waitFor(c -> c.screen instanceof GuiBlockPicker, 40);
            context.getInput().typeChars("minecraft:cobblestone");
            context.waitTicks(3);
            GT.shot(context, "ale-picker-search");
            clickRow(context, true, 0);
            context.waitTicks(2);
            GT.shot(context, "ale-picker-added");
            List<String> after = context.computeOnClient(c -> List.copyOf(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.getStrings()));
            if (!after.equals(List.of("minecraft:cobblestone"))) throw new AssertionError("[picker] list after adding: " + after);
            GT.clickButton(context, "Done");
            context.waitFor(c -> c.screen instanceof GuiStringListEdit, 40);
            GT.shot(context, "ale-picker-back");
            GT.log("[picker] add OK: " + after);

            // 再打开，点左边的 − 移除
            GT.clickButton(context, "Pick blocks");
            context.waitFor(c -> c.screen instanceof GuiBlockPicker, 40);
            clickRow(context, false, 0);
            context.waitTicks(2);
            List<String> removed = context.computeOnClient(c -> List.copyOf(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.getStrings()));
            if (!removed.isEmpty()) throw new AssertionError("[picker] list after removing: " + removed);
            GT.log("[picker] remove OK");
        } finally {
            context.runOnClient(c -> {
                Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.setStrings(List.of());
                c.setScreen(null);
            });
        }
    }

    /** 点击右边（加入）第 row 行，或左边（移除）第 row 行的 − 按钮 */
    private static void clickRow(ClientGameTestContext context, boolean right, int row) {
        double[] pos = context.computeOnClient(c -> {
            try {
                GuiBlockPicker gui = (GuiBlockPicker) c.screen;
                int panelX = intField(gui, "panelX"), colW = intField(gui, "colW"), listTop = intField(gui, "listTop");
                int x = right ? panelX + 10 + colW + 10 + 40 : panelX + 10 + colW - 12;
                int y = listTop + row * 20 + 10;
                double s = c.getWindow().getGuiScale();
                return new double[]{x * s, y * s};
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    private static int intField(Object o, String name) throws ReflectiveOperationException {
        Field f = GuiBlockPicker.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(o);
    }
}
