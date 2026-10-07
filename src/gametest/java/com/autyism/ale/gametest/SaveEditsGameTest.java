package com.autyism.ale.gametest;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiTextInput;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 需求 4：Schematic Preview 替换 / Litematica 编辑模式的修改只在内存里 → 用“保存修改”覆盖原文件、“另存为…”存成新文件。
 * 编辑用与那两个工具相同的方式直接改投影的方块容器；按钮通过真实鼠标点击触发；最后从磁盘重新读文件验证。
 */
@SuppressWarnings("UnstableApiUsage")
public final class SaveEditsGameTest implements FabricClientGameTest {
    private static final BlockPos MIN = new BlockPos(10, 64, 0);
    private static final BlockPos MAX = new BlockPos(15, 64, 0);
    private static final BlockPos SIGN = new BlockPos(15, 64, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("save")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 4, -6, 20, 6, 70);
            sp.getServer().runCommand("tp @a 12.5 64 3.5 180 20");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 12.5) < 0.01, 200);
            sp.getServer().runOnServer(s -> {
                for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) s.overworld().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
                s.overworld().setBlockAndUpdate(SIGN, Blocks.OAK_SIGN.defaultBlockState());
                if (s.overworld().getBlockEntity(SIGN) instanceof SignBlockEntity sign) {
                    GT.setSignLine(sign, true, 0, Component.literal("hello"));
                }
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_save_test");
            Path dir = context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());

            // 与 Schematic Preview “替换”/编辑模式相同：直接改放置引用的投影容器（石头→花岗岩，告示牌→石头）
            context.runOnClient(c -> {
                LitematicaSchematic schematic = placement.getSchematic();
                String region = schematic.getAreas().keySet().iterator().next();
                LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);
                for (int x = 0; x < container.getSize().getX(); x++) {
                    BlockState st = container.get(x, 0, 0);
                    if (st.is(Blocks.STONE)) container.set(x, 0, 0, Blocks.GRANITE.defaultBlockState());
                    else if (st.is(Blocks.OAK_SIGN)) container.set(x, 0, 0, Blocks.STONE.defaultBlockState());
                }
                schematic.getMetadata().setModifiedSinceSaved();
                DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);
            });

            // 打开放置设置界面，点“保存修改”
            context.runOnClient(c -> c.setScreen(new GuiPlacementConfiguration(placement)));
            context.waitTicks(5);
            context.takeScreenshot("ale-save-buttons");
            GT.clickButton(context, "Save edits");
            context.waitTicks(5);
            verifyFile(context, dir, "ale_save_test.litematic");
            boolean cleared = context.computeOnClient(c -> !placement.getSchematic().getMetadata().wasModifiedSinceSaved());
            if (!cleared) throw new AssertionError("[save] schematic still marked as modified after saving");
            GT.log("[save] overwrite OK: edits written to ale_save_test.litematic, stale sign NBT removed");

            // 另存为：点按钮 → 文件名输入框（默认 原名_edited）→ 回车
            context.runOnClient(c -> c.setScreen(new GuiPlacementConfiguration(placement)));
            context.waitTicks(5);
            GT.clickButton(context, "Save as");
            context.waitFor(c -> c.screen instanceof GuiTextInput, 40);
            context.takeScreenshot("ale-save-as-dialog");
            context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
            context.waitTicks(5);
            verifyFile(context, dir, "ale_save_test_edited.litematic");
            GT.log("[save] save-as OK: ale_save_test_edited.litematic written");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> c.setScreen(null));
        }
    }

    private static void verifyFile(ClientGameTestContext context, Path dir, String name) {
        if (!Files.exists(dir.resolve(name))) throw new AssertionError("[save] file not written: " + name);
        String problem = context.computeOnClient(c -> {
            LitematicaSchematic loaded = LitematicaSchematic.createFromFile(dir, name);
            if (loaded == null) return "could not read " + name;
            String region = loaded.getAreas().keySet().iterator().next();
            LitematicaBlockStateContainer container = loaded.getSubRegionContainer(region);
            for (int x = 0; x < container.getSize().getX(); x++) {
                BlockState st = container.get(x, 0, 0);
                boolean last = x == container.getSize().getX() - 1;
                if (last ? !st.is(Blocks.STONE) : !st.is(Blocks.GRANITE)) return "x=" + x + " is " + st;
            }
            if (!loaded.getBlockEntityMapForRegion(region).isEmpty()) return "stale block entity data left: " + loaded.getBlockEntityMapForRegion(region).keySet();
            return null;
        });
        if (problem != null) throw new AssertionError("[save] " + name + ": " + problem);
    }
}
