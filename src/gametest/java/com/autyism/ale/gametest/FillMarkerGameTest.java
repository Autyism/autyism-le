package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * “需要填充”标记：投影里装着东西的容器画绿色 F（世界里的容器是空的也画，不需要世界的数据）；
 * 空容器不画；“跳过列表里的物品”打开时，只装着潜影盒的容器不画；状态也不对时和 D、W 并排画；
 * 验证器确认内容一样后不再画；关掉设置后全部不画。
 */
@SuppressWarnings("UnstableApiUsage")
public final class FillMarkerGameTest implements FabricClientGameTest {
    private static final int X0 = 400, STEP = 6, Y = 64, Z = 0;
    private static final int MIN_PIXELS = 120;

    private record Case(String name, BlockState block, List<ItemStack> schematicItems, BlockState worldBlock) {
    }

    private static List<Case> cases() {
        List<Case> list = new ArrayList<>();
        list.add(new Case("hopper-items", Blocks.HOPPER.defaultBlockState(), List.of(new ItemStack(Items.IRON_INGOT, 5)), Blocks.HOPPER.defaultBlockState()));
        list.add(new Case("dropper-items", Blocks.DROPPER.defaultBlockState(), List.of(new ItemStack(Items.ARROW, 64)), Blocks.DROPPER.defaultBlockState()));
        list.add(new Case("furnace-fuel", Blocks.FURNACE.defaultBlockState(), List.of(new ItemStack(Items.COAL, 8)), Blocks.FURNACE.defaultBlockState()));
        list.add(new Case("barrel-empty", Blocks.BARREL.defaultBlockState(), List.of(), Blocks.BARREL.defaultBlockState()));
        list.add(new Case("hopper-shulkers", Blocks.HOPPER.defaultBlockState(), List.of(new ItemStack(Items.SHULKER_BOX), new ItemStack(Items.RED_SHULKER_BOX)),
                Blocks.HOPPER.defaultBlockState()));
        list.add(new Case("chest-missing", Blocks.CHEST.defaultBlockState(), List.of(new ItemStack(Items.DIAMOND, 2)), Blocks.AIR.defaultBlockState()));
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(BlockStateProperties.WATERLOGGED, true);
        list.add(new Case("chest-facing+water+items", chest, List.of(new ItemStack(Items.GOLD_INGOT, 3)),
                chest.setValue(ChestBlock.FACING, Direction.EAST).setValue(BlockStateProperties.WATERLOGGED, false)));
        return list;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("fillmarker")) return;
        List<Case> cases = cases();
        int xMax = X0 + STEP * (cases.size() - 1);
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 4, -6, xMax + 4, 8, 72);
            ViewShots.begin(context, sp);
            ViewShots.lookAt(context, sp, new BlockPos(X0, Y, Z));
            context.waitTicks(20);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false);
                AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(true);
                AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(true);
            });
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int i = 0; i < cases.size(); i++) {
                    BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                    level.setBlock(pos, cases.get(i).block, ViewShots.QUIET);
                    if (level.getBlockEntity(pos) instanceof Container c) {
                        List<ItemStack> items = cases.get(i).schematicItems;
                        for (int j = 0; j < items.size(); j++) c.setItem(j, items.get(j).copy());
                    }
                }
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, new BlockPos(X0, Y, Z), new BlockPos(xMax, Y, Z), new BlockPos(X0, Y, Z), "ale_fillmarker");
            GT.waitSchematicBlock(context, new BlockPos(X0, Y, Z), Blocks.HOPPER);
            // 世界里：同样的方块但都是空的（缺失的那个不放；最后一个朝向和含水也不对）
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int i = 0; i < cases.size(); i++) {
                    BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), ViewShots.QUIET);
                    level.setBlock(pos, cases.get(i).worldBlock, ViewShots.QUIET);
                }
            });
            context.waitTicks(10);

            List<String> failures = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                Case cs = cases.get(i);
                int[] px = shoot(context, sp, placement, i, cs.name);
                boolean wantF = !cs.schematicItems.isEmpty();
                boolean combined = cs.name.contains("facing");
                boolean ok = (px[0] >= MIN_PIXELS) == wantF && (!combined || (px[1] >= MIN_PIXELS && px[2] >= MIN_PIXELS));
                GT.log(String.format(java.util.Locale.ROOT, "[fillmarker] %-26s F: %5d px (want %s)  D: %5d px  W: %5d px  %s",
                        cs.name, px[0], wantF ? "yes" : "no", px[1], px[2], ok ? "ok" : "WRONG"));
                if (!ok) failures.add(cs.name);
            }

            // 跳过列表：只装着潜影盒的漏斗不再画，装着别的东西的照画
            context.runOnClient(c -> AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(true));
            int shulkerIndex = indexOf(cases, "hopper-shulkers");
            int[] skipped = shoot(context, sp, placement, shulkerIndex, "hopper-shulkers-skip");
            int[] stillThere = shoot(context, sp, placement, indexOf(cases, "hopper-items"), "hopper-items-skip");
            GT.log("[fillmarker] skip list on: shulker-only hopper F=" + skipped[0] + " px, hopper with iron F=" + stillThere[0] + " px");
            if (skipped[0] >= MIN_PIXELS || stillThere[0] < MIN_PIXELS) failures.add("skip-list");
            context.runOnClient(c -> AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false));

            // 世界里的漏斗放好同样的东西、验证器确认一样之后不再画 F
            int hopperIndex = indexOf(cases, "hopper-items");
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(new BlockPos(X0 + STEP * hopperIndex, Y, Z)) instanceof Container c) c.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
            });
            context.waitTicks(5);
            SchematicVerifier verifier = context.computeOnClient(c -> placement.getSchematicVerifier());
            context.runOnClient(c -> verifier.startVerification(c.level, SchematicWorldHandler.getSchematicWorld(), placement, () -> {
            }));
            context.waitFor(c -> verifier.isFinished(), 400);
            context.waitTicks(20);
            int[] filled = shoot(context, sp, placement, hopperIndex, "hopper-items-verified");
            int[] dropperStill = shoot(context, sp, placement, indexOf(cases, "dropper-items"), "dropper-items-verified");
            GT.log("[fillmarker] after verifying: filled hopper F=" + filled[0] + " px, still empty dropper F=" + dropperStill[0] + " px");
            if (filled[0] >= MIN_PIXELS || dropperStill[0] < MIN_PIXELS) failures.add("known-match");
            context.runOnClient(c -> verifier.reset());
            context.waitTicks(10);

            // 打开世界里的投掷器、往里放好投影要的东西：客户端看到内容一样，不再画 F
            int dropperIndex = indexOf(cases, "dropper-items");
            BlockPos dropper = new BlockPos(X0 + STEP * dropperIndex, Y, Z);
            ViewShots.lookAt(context, sp, dropper);
            // 打开：客户端记下点的方块（和真的点一下时 MultiPlayerGameMode.useItemOn 里记的一样），服务端打开界面
            context.runOnClient(c -> com.autyism.ale.verifier.OpenedContainers.onUseItemOn(dropper));
            sp.getServer().runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                if (s.overworld().getBlockEntity(dropper) instanceof net.minecraft.world.MenuProvider menu) player.openMenu(menu);
            });
            context.waitFor(c -> c.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>, 100);
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(dropper) instanceof Container c) c.setItem(4, new ItemStack(Items.ARROW, 64));
            });
            context.waitTicks(10);
            context.runOnClient(c -> c.setScreen(null));
            context.waitTicks(10);
            int[] opened = shoot(context, sp, placement, dropperIndex, "dropper-items-opened-filled");
            GT.log("[fillmarker] dropper opened and filled like the schematic: F=" + opened[0] + " px");
            if (opened[0] >= MIN_PIXELS) failures.add("opened-container");

            // 关掉设置：不画
            context.runOnClient(c -> AleConfigs.Generic.FILL_MARKER.setBooleanValue(false));
            int[] off = shoot(context, sp, placement, indexOf(cases, "dropper-items"), "dropper-items-off");
            GT.log("[fillmarker] option off: dropper F=" + off[0] + " px");
            if (off[0] >= MIN_PIXELS) failures.add("option-off");

            if (!failures.isEmpty()) throw new AssertionError("[fillmarker] wrong: " + failures);
            GT.log("[fillmarker] OK: F on containers with items in the schematic (also missing ones), not on empty ones, skip list, "
                    + "side by side with D and W, gone after the verifier confirmed the contents, option off");
            // 世界关掉之前恢复（渲染层等设置会存进同名世界的数据）
            ViewShots.end(context);
        } finally {
            ViewShots.end(context);
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false);
            });
        }
    }

    private static int indexOf(List<Case> cases, String name) {
        for (int i = 0; i < cases.size(); i++) if (cases.get(i).name.equals(name)) return i;
        throw new AssertionError(name);
    }

    /** 只渲染第 i 格，截图，返回 {F 的绿色像素, D 的红色像素, W 的蓝色像素} */
    private static int[] shoot(ClientGameTestContext context, TestSingleplayerContext sp, SchematicPlacement placement, int i, String name) {
        BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
        ViewShots.lookAt(context, sp, pos);
        ViewShots.onlyLayerX(context, pos.getX());
        ViewShots.rebuild(context, placement);
        Path shot = GT.shot(context, "ale-fillmarker-" + name);
        return new int[]{countGreen(shot), RenderGameTest.countRed(shot), RenderGameTest.countBlue(shot)};
    }

    /** “F” 的绿色（默认 #2BD96B） */
    static int countGreen(Path png) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            int n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (g > 170 && r < 120 && b < 160 && g > r + 60 && g > b + 40) n++;
                }
            }
            return n;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
