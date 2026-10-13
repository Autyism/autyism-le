package com.autyism.ale.gametest;

import com.autyism.ale.gui.GuiBlockPicker;
import com.autyism.ale.replace.ReplaceBlockScreen;
import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiStringListEdit;
import fi.dy.masa.malilib.gui.button.ConfigButtonStringList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料列表“替换”和方块列表的界面：
 * 立式 / 挂墙等几种形态合成一条；对话框打开时用鼠标中键选取准星对着的方块、鼠标所指的世界里的方块；
 * 方块列表点开直接是 ALE 的选择界面，右上角按钮换成原来的编辑框，原来的编辑框里的按钮再换回来；
 * 选择界面里加入 / 移除一条时几种形态一起处理，也能用鼠标中键从世界里加。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ReplaceUiGameTest implements FabricClientGameTest {
    private static final int X = 500, Z = 0;
    private static final BlockPos SIGN = new BlockPos(X + 2, 64, Z - 8);
    private static final BlockPos WALL_SIGN_BACK = new BlockPos(X + 4, 64, Z - 8);
    private static final BlockPos TARGET_STONE = new BlockPos(X, 65, Z - 4);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("replaceui")) return;
        List<String> savedIgnorable = context.computeOnClient(c -> new ArrayList<>(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.getStrings()));
        List<String> problems = new ArrayList<>();
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X - 16, Z - 12, X + 16, Z + 4, 76);
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f 64 %.1f 180 0", X + 0.5, Z + 0.5));
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - (X + 0.5)) < 0.01, 200);
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                // 投影：立式橡木告示牌 + 贴在石头上的橡木告示牌
                level.setBlock(SIGN, Blocks.OAK_SIGN.defaultBlockState(), ViewShots.QUIET);
                level.setBlock(WALL_SIGN_BACK, Blocks.STONE.defaultBlockState(), ViewShots.QUIET);
                level.setBlock(WALL_SIGN_BACK.south(), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH), ViewShots.QUIET);
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, SIGN, WALL_SIGN_BACK.south(), SIGN, "ale_replaceui");
            GT.waitSchematicBlock(context, SIGN, Blocks.OAK_SIGN);
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (BlockPos p : List.of(SIGN, WALL_SIGN_BACK, WALL_SIGN_BACK.south())) level.setBlock(p, Blocks.AIR.defaultBlockState(), ViewShots.QUIET);
                // 准星对着：石头上贴着云杉告示牌；左边一面金块墙、右边一面绿宝石块墙
                level.setBlock(TARGET_STONE, Blocks.STONE.defaultBlockState(), ViewShots.QUIET);
                level.setBlock(TARGET_STONE.south(), Blocks.SPRUCE_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH), ViewShots.QUIET);
                for (int x = 3; x <= 14; x++) {
                    for (int y = 63; y <= 74; y++) {
                        level.setBlock(new BlockPos(X - x, y, Z - 6), Blocks.GOLD_BLOCK.defaultBlockState(), ViewShots.QUIET);
                        level.setBlock(new BlockPos(X + x, y, Z - 6), Blocks.EMERALD_BLOCK.defaultBlockState(), ViewShots.QUIET);
                    }
                }
            });
            context.waitTicks(20);

            // ---- 1) 替换对话框：几种形态合成一条 ----
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                GuiMaterialList gui = new GuiMaterialList(list);
                c.setScreen(gui);
                c.setScreen(new ReplaceBlockScreen(list, new ItemStack(Items.OAK_SIGN), gui));
            });
            context.waitTicks(5);
            List<Block> options = context.computeOnClient(c -> ((ReplaceBlockScreen) c.screen).optionBlocksForTest());
            for (Block one : List.of(Blocks.OAK_SIGN, Blocks.TORCH, Blocks.SOUL_TORCH, Blocks.REDSTONE_TORCH, Blocks.WHITE_BANNER, Blocks.SKELETON_SKULL,
                    Blocks.TUBE_CORAL_FAN, Blocks.OAK_HANGING_SIGN)) {
                if (!options.contains(one)) problems.add("replace list lacks " + one);
            }
            for (Block wall : List.of(Blocks.OAK_WALL_SIGN, Blocks.WALL_TORCH, Blocks.SOUL_WALL_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.WHITE_WALL_BANNER,
                    Blocks.SKELETON_WALL_SKULL, Blocks.TUBE_CORAL_WALL_FAN, Blocks.OAK_WALL_HANGING_SIGN)) {
                if (options.contains(wall)) problems.add("replace list still has a separate " + wall);
            }
            GT.shot(context, "ale-replaceui-dialog");

            // ---- 2) 鼠标中键：对话框上（不在网格里）→ 准星对着的方块（云杉墙上告示牌 → 云杉告示牌这一条）----
            int[] panel = context.computeOnClient(c -> ((ReplaceBlockScreen) c.screen).panelForTest());
            middleClick(context, panel[0] + panel[2] / 2.0, panel[1] + 8);
            Block picked = context.computeOnClient(c -> ((ReplaceBlockScreen) c.screen).selected());
            GT.log("[replaceui] middle click on the dialog picked " + picked);
            if (picked != Blocks.SPRUCE_SIGN) problems.add("crosshair pick gave " + picked);
            // 对话框外：鼠标所指的世界里的方块（左边金块、右边绿宝石块）
            double[] screen = context.computeOnClient(c -> new double[]{c.screen.width, c.screen.height});
            middleClick(context, 12, screen[1] / 2);
            Block left = context.computeOnClient(c -> ((ReplaceBlockScreen) c.screen).selected());
            middleClick(context, screen[0] - 12, screen[1] / 2);
            Block right = context.computeOnClient(c -> ((ReplaceBlockScreen) c.screen).selected());
            GT.log("[replaceui] middle click outside the dialog: left edge picked " + left + ", right edge picked " + right);
            if (left != Blocks.GOLD_BLOCK || right != Blocks.EMERALD_BLOCK) problems.add("cursor pick gave " + left + " / " + right);
            GT.shot(context, "ale-replaceui-picked");
            // 换成准星选到的云杉告示牌：立式的变云杉告示牌，墙上的变云杉墙上告示牌
            middleClick(context, panel[0] + panel[2] / 2.0, panel[1] + 8);
            context.getInput().pressKey(InputConstants.KEY_RETURN);
            context.waitTicks(5);
            var schematicWorld = context.computeOnClient(c -> fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld());
            Block standing = context.computeOnClient(c -> schematicWorld.getBlockState(SIGN).getBlock());
            Block onWall = context.computeOnClient(c -> schematicWorld.getBlockState(WALL_SIGN_BACK.south()).getBlock());
            GT.log("[replaceui] after replacing with the picked sign: standing=" + standing + " wall=" + onWall);
            if (standing != Blocks.SPRUCE_SIGN || onWall != Blocks.SPRUCE_WALL_SIGN) problems.add("replace result " + standing + " / " + onWall);
            context.runOnClient(c -> c.setScreen(null));

            // ---- 2b) 装了 Schematic Preview 时：它的“替换”默认打开 ALE 的对话框，右上角按钮换回它自己的 ----
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("schematicpreview")) {
                context.runOnClient(c -> {
                    MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                    DataManager.setMaterialList(list);
                    GuiMaterialList gui = new GuiMaterialList(list);
                    c.setScreen(gui);
                    try {
                        Class<?> select = Class.forName("ru.dimaskama.schematicpreview.gui.GuiBlockSelect");
                        Object dialog = select.getConstructors()[0].newInstance(gui, "Replace", Blocks.OAK_PLANKS, (java.util.function.Consumer<Block>) b -> {
                        });
                        c.setScreen((net.minecraft.client.gui.screens.Screen) dialog);
                    } catch (ReflectiveOperationException e) {
                        throw new AssertionError(e);
                    }
                });
                context.waitTicks(3);
                boolean ours = context.computeOnClient(c -> c.screen instanceof ReplaceBlockScreen);
                GT.shot(context, "ale-replaceui-sp-default");
                if (ours) GT.clickButton(context, "Schematic Preview's dialog");
                String after = context.computeOnClient(c -> c.screen == null ? "null" : c.screen.getClass().getSimpleName());
                GT.log("[replaceui] Schematic Preview's Replace opened ALE's dialog: " + ours + ", top-right button opened " + after);
                if (!ours || !after.equals("GuiBlockSelect")) problems.add("Schematic Preview dialog swap: ours=" + ours + " then " + after);
                context.runOnClient(c -> c.setScreen(null));
            }

            // ---- 3) 方块列表：点开直接是 ALE 的选择界面 ----
            context.runOnClient(c -> {
                Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.setStrings(new ArrayList<>());
                c.setScreen(new fi.dy.masa.litematica.gui.GuiConfigs());
            });
            context.waitTicks(3);
            context.runOnClient(c -> {
                var configGui = (fi.dy.masa.malilib.gui.interfaces.IConfigGui) c.screen;
                ConfigButtonStringList button = new ConfigButtonStringList(10, 10, 100, 20, Configs.Visuals.IGNORABLE_EXISTING_BLOCKS, configGui, null);
                button.onMouseClicked(new MouseButtonEvent(20, 15, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            });
            context.waitTicks(3);
            boolean pickerFirst = context.computeOnClient(c -> c.screen instanceof GuiBlockPicker);
            if (!pickerFirst) problems.add("block list opened " + context.computeOnClient(c -> String.valueOf(c.screen)) + " instead of ALE's picker");
            GT.shot(context, "ale-replaceui-picker");
            if (pickerFirst) {
                // 加入 / 移除时几种形态一起
                context.runOnClient(c -> ((GuiBlockPicker) c.screen).addForTest(Blocks.OAK_SIGN));
                List<String> afterAdd = context.computeOnClient(c -> new ArrayList<>(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.getStrings()));
                List<String> shown = context.computeOnClient(c -> ((GuiBlockPicker) c.screen).shownRows());
                boolean separate = context.computeOnClient(c -> ((GuiBlockPicker) c.screen).listsSeparately(Blocks.OAK_WALL_SIGN));
                GT.log("[replaceui] picker: list after adding Oak Sign " + afterAdd + ", shown as " + shown + ", wall sign listed separately: " + separate);
                if (!afterAdd.contains("minecraft:oak_sign") || !afterAdd.contains("minecraft:oak_wall_sign") || shown.size() != 1 || separate) {
                    problems.add("picker merge: " + afterAdd + " " + shown + " " + separate);
                }
                context.runOnClient(c -> ((GuiBlockPicker) c.screen).removeRowForTest(0));
                List<String> afterRemove = context.computeOnClient(c -> new ArrayList<>(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.getStrings()));
                if (!afterRemove.isEmpty()) problems.add("picker remove left " + afterRemove);
                // 鼠标中键：准星对着的云杉墙上告示牌 → 云杉告示牌两种形态都加进来
                double[] pickerScreen = context.computeOnClient(c -> new double[]{c.screen.width, c.screen.height});
                middleClick(context, pickerScreen[0] / 2, 14);
                List<String> afterPick = context.computeOnClient(c -> new ArrayList<>(Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.getStrings()));
                GT.log("[replaceui] picker: middle click added " + afterPick);
                if (!afterPick.contains("minecraft:spruce_sign") || !afterPick.contains("minecraft:spruce_wall_sign")) problems.add("picker pick: " + afterPick);
                // 右上角按钮 → 原来的编辑框；它的“选择方块…”→ 回到选择界面
                GT.clickButton(context, "Original editor");
                boolean original = context.computeOnClient(c -> c.screen instanceof GuiStringListEdit);
                GT.shot(context, "ale-replaceui-original-editor");
                GT.clickButton(context, "Pick blocks");
                boolean back = context.computeOnClient(c -> c.screen instanceof GuiBlockPicker);
                boolean parentIsConfigs = context.computeOnClient(c -> c.screen instanceof GuiBlockPicker p
                        && p.getParent() instanceof fi.dy.masa.litematica.gui.GuiConfigs);
                GT.log("[replaceui] original editor opened: " + original + ", back to the picker: " + back + " (parent is the config screen: " + parentIsConfigs + ")");
                if (!original || !back || !parentIsConfigs) problems.add("editor swap: original=" + original + " back=" + back + " parent=" + parentIsConfigs);
            }
            if (!problems.isEmpty()) throw new AssertionError("[replaceui] " + problems);
            GT.log("[replaceui] OK: wall and standing forms merged, pick block in the replace dialog (crosshair and cursor), "
                    + "picker opens first with the original editor behind a button, merged add / remove, pick block in the picker");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                c.setScreen(null);
                Configs.Visuals.IGNORABLE_EXISTING_BLOCKS.setStrings(savedIgnorable);
            });
        }
    }

    private static void middleClick(ClientGameTestContext context, double guiX, double guiY) {
        double scale = context.computeOnClient(c -> c.getWindow().getGuiScale());
        context.getInput().setCursorPos(guiX * scale, guiY * scale);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_MIDDLE);
        context.waitTicks(2);
    }
}
