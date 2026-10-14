package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
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
import net.minecraft.world.level.block.state.properties.ChestType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 大箱子的“需要填充”：两半看成一个长方体，只在中间画一个 F（投影里只有一半装着东西也一样）。
 * 世界里那一半填对了 F 自己消失（单人游戏读得到内容），又拿空了 F 回来。
 */
@SuppressWarnings("UnstableApiUsage")
public final class DoubleChestFillGameTest implements FabricClientGameTest {
    private static final int X0 = 520, Y = 64, Z = 0;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("doublechest")) return;
        BlockPos left = new BlockPos(X0, Y, Z), right = new BlockPos(X0 + 1, Y, Z);
        BlockState leftState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
        BlockState rightState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 4, Z - 6, X0 + 6, Z + 6, 72);
            ViewShots.begin(context, sp);
            ViewShots.lookAt(context, sp, right);
            context.waitTicks(20);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
            });
            // 投影：大箱子，只有右半边装着东西
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlock(left, leftState, ViewShots.QUIET);
                level.setBlock(right, rightState, ViewShots.QUIET);
                if (level.getBlockEntity(right) instanceof Container c) c.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, left, right, left, "ale_doublechest");
            GT.waitSchematicBlock(context, left, Blocks.CHEST);
            // 世界：同样的大箱子，空的
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (BlockPos p : List.of(left, right)) {
                    if (level.getBlockEntity(p) instanceof Container c) c.clearContent();
                }
            });
            // 清空之前那一小会儿，自动检查可能已经看到了装满的箱子、记成“填对了”：要等下一次复查（40 tick 一次）才发现被清空了
            context.waitTicks(60);
            List<String> failures = new ArrayList<>();

            ViewShots.rebuild(context, placement);
            Path empty = GT.shot(context, "ale-doublechest-empty");
            int f1 = FillMarkerGameTest.countGreen(empty);
            GT.log("[doublechest] empty double chest: F=" + f1 + " px; known left=" + context.computeOnClient(c -> com.autyism.ale.verifier.ContainerVerifier.isKnownToMatch(left))
                    + " right=" + context.computeOnClient(c -> com.autyism.ale.verifier.ContainerVerifier.isKnownToMatch(right)));
            if (f1 < 400) failures.add("no-F");

            // 右半边填好：F 自己消失
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(right) instanceof Container c) c.setItem(0, new ItemStack(Items.IRON_INGOT, 7));
            });
            context.waitTicks(60);
            ViewShots.rebuild(context, placement);
            int f2 = FillMarkerGameTest.countGreen(GT.shot(context, "ale-doublechest-filled"));
            GT.log("[doublechest] right half filled: F=" + f2 + " px");
            if (f2 >= 400) failures.add("still-F");

            // 又拿空：F 回来
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(right) instanceof Container c) c.clearContent();
            });
            context.waitTicks(80);
            ViewShots.rebuild(context, placement);
            int f3 = FillMarkerGameTest.countGreen(GT.shot(context, "ale-doublechest-emptied"));
            GT.log("[doublechest] emptied again: F=" + f3 + " px; known right=" + context.computeOnClient(c -> com.autyism.ale.verifier.ContainerVerifier.isKnownToMatch(right)));
            if (f3 < 400) failures.add("F-not-back");

            if (!failures.isEmpty()) throw new AssertionError("[doublechest] wrong: " + failures);
            GT.log("[doublechest] OK: one F across the double chest, gone when filled, back when emptied");
            ViewShots.end(context);
        } finally {
            ViewShots.end(context);
            GT.removeAllPlacements(context);
        }
    }
}
