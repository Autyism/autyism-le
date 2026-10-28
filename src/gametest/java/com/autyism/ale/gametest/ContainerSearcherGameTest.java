package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * “Container Searcher 不用打开”：要在 -MoreMods 里加 Container Searcher 和 cloth-config。
 * 一个箱子、一个大箱子（两半都有东西）、一个空箱子、一个漏斗（小容器，Container Searcher 默认不记），都不打开：
 * 箱子和大箱子进它的缓存（大箱子一条、两半的东西都在），空箱子和漏斗不进；把箱子里的东西拿走后这条会被删掉。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ContainerSearcherGameTest implements FabricClientGameTest {
    private static final int X0 = 520, Y = 64, Z = 0;
    private static final BlockPos CHEST = new BlockPos(X0, Y, Z);
    private static final BlockPos DOUBLE_A = new BlockPos(X0 + 3, Y, Z);
    private static final BlockPos DOUBLE_B = new BlockPos(X0 + 4, Y, Z);
    private static final BlockPos EMPTY = new BlockPos(X0 + 7, Y, Z);
    private static final BlockPos HOPPER = new BlockPos(X0 + 10, Y, Z);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("csservux")) servux(context);
        if (!GTFilter.enabled("csearcher")) return;
        if (!FabricLoader.getInstance().isModLoaded("csearcher")) {
            // 跑全部测试时没加 Container Searcher：跳过；点名要跑这个却没加：算失败
            if (System.getProperty("ale.gt", "").contains("csearcher")) throw new AssertionError("[csearcher] Container Searcher is not loaded (add it with -MoreMods)");
            GT.log("[csearcher] skipped: Container Searcher not loaded");
            return;
        }
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 4, -6, X0 + 14, 8, 72);
            ViewShots.begin(context, sp);
            ViewShots.lookAt(context, sp, DOUBLE_A);
            context.waitTicks(20);
            context.runOnClient(c -> AleConfigs.Generic.CONTAINER_SEARCHER_SERVUX.setBooleanValue(true));
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlock(CHEST, Blocks.CHEST.defaultBlockState(), ViewShots.QUIET);
                ((Container) level.getBlockEntity(CHEST)).setItem(4, new ItemStack(Items.DIAMOND, 7));
                BlockState left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT);
                BlockState right = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT);
                // 朝北的大箱子：西边一半是 LEFT，它的另一半在东边（getConnectedDirection 是朝向顺时针转）
                level.setBlock(DOUBLE_A, left, ViewShots.QUIET);
                level.setBlock(DOUBLE_B, right, ViewShots.QUIET);
                ((Container) level.getBlockEntity(DOUBLE_A)).setItem(0, new ItemStack(Items.EMERALD, 3));
                ((Container) level.getBlockEntity(DOUBLE_B)).setItem(26, new ItemStack(Items.GOLD_INGOT, 11));
                level.setBlock(EMPTY, Blocks.CHEST.defaultBlockState(), ViewShots.QUIET);
                level.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState(), ViewShots.QUIET);
                ((Container) level.getBlockEntity(HOPPER)).setItem(0, new ItemStack(Items.REDSTONE, 2));
            });
            context.waitTicks(200);

            List<String> failures = new ArrayList<>();
            Map<BlockPos, Map<String, Integer>> cache = context.computeOnClient(c -> cached());
            GT.log("[csearcher] cache: " + cache);
            if (!Map.of("minecraft:diamond", 7).equals(cache.get(CHEST))) failures.add("chest");
            Map<String, Integer> dbl = cache.containsKey(DOUBLE_A) ? cache.get(DOUBLE_A) : cache.get(DOUBLE_B);
            if (!Map.of("minecraft:emerald", 3, "minecraft:gold_ingot", 11).equals(dbl)) failures.add("double-chest");
            if (cache.containsKey(DOUBLE_A) && cache.containsKey(DOUBLE_B)) failures.add("double-chest-twice");
            if (cache.containsKey(EMPTY)) failures.add("empty-chest");
            if (cache.containsKey(HOPPER)) failures.add("hopper");

            // 拿走箱子里的东西：下一次查（一分钟一次）后这条被删掉
            sp.getServer().runOnServer(s -> ((Container) s.overworld().getBlockEntity(CHEST)).clearContent());
            context.waitTicks(20 * 70);
            Map<BlockPos, Map<String, Integer>> after = context.computeOnClient(c -> cached());
            GT.log("[csearcher] after emptying: " + after);
            if (after.containsKey(CHEST)) failures.add("emptied-chest-kept");

            if (!failures.isEmpty()) throw new AssertionError("[csearcher] failed: " + failures);
            GT.log("[csearcher] OK");
        }
    }

    /**
     * 装了 Servux 的服务器上（本地测试服务器，端口 25599）：内容从服务端要。一个放了东西的箱子，不打开也进缓存；
     * 拿空后被删掉
     */
    private static void servux(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("csearcher")) throw new AssertionError("[csservux] Container Searcher is not loaded (add it with -MoreMods)");
        BlockPos chest = new BlockPos(40, -60, 26);
        List<String> failures = new ArrayList<>();
        ContainerCheckServuxGameTest.connect(context);
        try {
            context.runOnClient(c -> {
                fi.dy.masa.litematica.config.Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                AleConfigs.Generic.CONTAINER_SEARCHER_SERVUX.setBooleanValue(true);
            });
            ContainerCheckServuxGameTest.command(context, "gamemode creative");
            ContainerCheckServuxGameTest.command(context, "fill 36 -60 25 46 -55 28 air");
            // 附近一大片容器（1024 个木桶）：Litematica 的队不能被它们占满，后放的箱子也要很快记上
            ContainerCheckServuxGameTest.command(context, "fill 40 -60 40 71 -60 71 barrel");
            context.waitTicks(100);
            ContainerCheckServuxGameTest.command(context, "setblock 40 -60 26 chest");
            ContainerCheckServuxGameTest.command(context, "item replace block 40 -60 26 container.13 with minecraft:lapis_lazuli 9");
            ContainerCheckServuxGameTest.command(context, "tp @s 40.5 -59 23.5 0 25");
            boolean stored = ContainerCheckServuxGameTest.waitFor(context,
                    () -> Map.of("minecraft:lapis_lazuli", 9).equals(context.computeOnClient(c -> cached()).get(chest)), 300);
            GT.log("[csservux] cache: " + context.computeOnClient(c -> cached()));
            if (!stored) {
                GT.log("[csservux] chest: block=" + context.computeOnClient(c -> c.level.getBlockState(chest)) + " player=" + context.computeOnClient(c -> c.player.blockPosition())
                        + " " + context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.debug(chest)));
                failures.add("chest-not-stored");
            }
            ContainerCheckServuxGameTest.command(context, "item replace block 40 -60 26 container.13 with minecraft:air");
            boolean removed = ContainerCheckServuxGameTest.waitFor(context, () -> !context.computeOnClient(c -> cached()).containsKey(chest), 20 * 80);
            GT.log("[csservux] after emptying: " + context.computeOnClient(c -> cached()));
            if (!removed) failures.add("emptied-chest-kept");
        } finally {
            ContainerCheckServuxGameTest.disconnect(context);
        }
        if (!failures.isEmpty()) throw new AssertionError("[csservux] " + failures);
        GT.log("[csservux] OK");
    }

    /** Container Searcher 当前世界缓存里的容器：位置 → 物品 id → 数量 */
    @SuppressWarnings("unchecked")
    private static Map<BlockPos, Map<String, Integer>> cached() {
        try {
            Class<?> cs = Class.forName("fr.loxoz.csearcher.CSearcher");
            Object inst = cs.getMethod("inst").invoke(null);
            Object cache = cs.getMethod("getCache").invoke(inst);
            Object entry = cache.getClass().getMethod("current").invoke(cache);
            Map<BlockPos, Map<String, Integer>> out = new java.util.HashMap<>();
            if (entry == null) return out;
            Map<BlockPos, Object> containers = (Map<BlockPos, Object>) entry.getClass().getField("containers").get(entry);
            for (var e : containers.entrySet()) {
                Map<String, Integer> items = new java.util.TreeMap<>();
                for (Object stack : (List<Object>) e.getValue().getClass().getMethod("getContent").invoke(e.getValue())) {
                    ItemStack s = (ItemStack) stack.getClass().getMethod("asStack").invoke(stack);
                    items.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), s.getCount(), Integer::sum);
                }
                out.put(e.getKey().immutable(), items);
            }
            return out;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("[csearcher] reading Container Searcher's cache failed", e);
        }
    }
}
