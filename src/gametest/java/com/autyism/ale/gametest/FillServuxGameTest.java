package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.verifier.ContainerVerifier;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家反馈：多人服务器（装了 Servux）上，“需要填充”的 F 填好了也不消失，要打开一次才消失；退出重进又出现。
 * 连本地的 Servux 测试服务器（端口 25599，不是玩家的服务器）：投影里一个装着铁锭的漏斗和一个装着煤的熔炉，
 * 世界里放空的，用指令装好（不打开），F 要自己消失；退出重进后也要自己消失。
 * 只在 -Pgt=fillservux 时跑（要先开测试服务器）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class FillServuxGameTest implements FabricClientGameTest {
    private static final String ADDRESS = "localhost:25599";
    private static final BlockPos HOPPER = new BlockPos(20, -60, 20);
    private static final BlockPos FURNACE = new BlockPos(23, -60, 20);
    private static final String NAME = "ale_fillservux";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("fillservux")) return;
        // 1. 单人世界里做投影（客户端世界里没有容器内容物，要在服务端线程截取）
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runOnServer(s -> {
                var level = s.overworld();
                level.setBlockAndUpdate(HOPPER, Blocks.HOPPER.defaultBlockState());
                level.setBlockAndUpdate(FURNACE, Blocks.FURNACE.defaultBlockState());
                if (level.getBlockEntity(HOPPER) instanceof Container c) c.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
                if (level.getBlockEntity(FURNACE) instanceof Container c) c.setItem(1, new ItemStack(Items.COAL, 8));
            });
            context.waitTicks(5);
            GT.captureAndPlace(context, sp, HOPPER, FURNACE, HOPPER, NAME);
            GT.removeAllPlacements(context);
        }
        List<String> failures = new ArrayList<>();
        connect(context);
        try {
            command(context, "gamemode creative");
            command(context, "fill 15 -60 15 30 -55 30 air");
            command(context, "setblock " + xyz(HOPPER) + " hopper");
            command(context, "setblock " + xyz(FURNACE) + " furnace");
            command(context, "tp @s 21.5 -60 15.5 0 30");
            context.waitFor(c -> c.level.getBlockState(FURNACE).is(Blocks.FURNACE) && c.player.getZ() < 16, 200);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false);
                var dir = DataManager.getSchematicsBaseDirectory();
                LitematicaSchematic schematic = LitematicaSchematic.createFromFile(dir, NAME + ".litematic");
                if (schematic == null) throw new AssertionError("[fillservux] could not read " + NAME);
                SchematicPlacement p = SchematicPlacement.createFor(schematic, HOPPER, NAME, true, true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
            });
            waitFor(context, () -> context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.available()), 200);
            boolean servux = context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.available());
            GT.log("[fillservux] connected; Servux data available=" + servux);
            if (!servux) failures.add("no-servux");
            // F 画出来了（还没填）
            boolean drawn = waitFor(context, () -> context.computeOnClient(c -> ContainerVerifier.isFillCandidate(HOPPER) && ContainerVerifier.isFillCandidate(FURNACE)), 400);
            boolean early = context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(HOPPER) || ContainerVerifier.isKnownToMatch(FURNACE));
            GT.log("[fillservux] F drawn on both=" + drawn + ", known before filling=" + early);
            if (!drawn) failures.add("not-drawn");
            context.waitTicks(20);
            GT.shot(context, "ale-fillservux-before");
            if (early) failures.add("known-before-filling");
            context.waitTicks(60);
            // 不打开，直接用指令装好
            command(context, "item replace block " + xyz(HOPPER) + " container.0 with minecraft:iron_ingot 5");
            command(context, "item replace block " + xyz(FURNACE) + " container.1 with minecraft:coal 8");
            int[] t = {0};
            boolean gone = waitFor(context, () -> {
                t[0]++;
                return context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(HOPPER) && ContainerVerifier.isKnownToMatch(FURNACE));
            }, 400);
            GT.log("[fillservux] filled with commands: F gone=" + gone + " after " + t[0] + " ticks");
            if (!gone) failures.add("not-gone-after-filling");
            context.waitTicks(20);
            GT.shot(context, "ale-fillservux-after");
            // 退出重进
            disconnect(context);
            context.waitTicks(Integer.getInteger("ale.reconnectWait", 0));
            connect(context);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
            });
            int placements = context.computeOnClient(c -> DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size());
            int[] t2 = {0};
            boolean goneAgain = waitFor(context, () -> {
                if (t2[0] % 100 == 0) GT.log("[fillservux] after reconnect t" + t2[0] + ": candidate=" + context.computeOnClient(c -> ContainerVerifier.isFillCandidate(HOPPER))
                        + " " + context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.debug(HOPPER))
                        + " client BE=" + context.computeOnClient(c -> String.valueOf(c.level.getBlockEntity(HOPPER))));
                t2[0]++;
                return context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(HOPPER) && ContainerVerifier.isKnownToMatch(FURNACE));
            }, 600);
            GT.log("[fillservux] reconnected (" + placements + " placement(s) restored): F gone=" + goneAgain + " after " + t2[0] + " ticks");
            if (!goneAgain) failures.add("back-after-reconnect");
            // 世界里还没有这个方块（例如区块还没载入）时画出的 F：要一直记着，方块出现、装好以后照样自己消失
            command(context, "setblock " + xyz(FURNACE) + " air");
            context.waitTicks(40);
            boolean kept = waitFor(context, () -> context.computeOnClient(c -> ContainerVerifier.isFillCandidate(FURNACE)), 100);
            context.waitTicks(60);
            kept &= context.computeOnClient(c -> ContainerVerifier.isFillCandidate(FURNACE));
            GT.log("[fillservux] furnace gone from the world: F still waiting to be checked=" + kept);
            if (!kept) failures.add("candidate-dropped-while-missing");
            command(context, "setblock " + xyz(FURNACE) + " furnace");
            command(context, "item replace block " + xyz(FURNACE) + " container.1 with minecraft:coal 8");
            boolean furnaceOk = waitFor(context, () -> context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(FURNACE)), 400);
            GT.log("[fillservux] furnace put back and filled: F gone=" + furnaceOk);
            if (!furnaceOk) failures.add("furnace-not-gone");
            // 拿空了：F 回来
            command(context, "item replace block " + xyz(HOPPER) + " container.0 with minecraft:air");
            boolean back = waitFor(context, () -> context.computeOnClient(c -> !ContainerVerifier.isKnownToMatch(HOPPER)), 600);
            GT.log("[fillservux] hopper emptied again: F back=" + back);
            if (!back) failures.add("not-back-after-emptying");
        } finally {
            GT.removeAllPlacements(context);
            disconnect(context);
        }
        if (!failures.isEmpty()) throw new AssertionError("[fillservux] " + failures);
        GT.log("[fillservux] OK");
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static boolean waitFor(ClientGameTestContext context, java.util.function.BooleanSupplier done, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (done.getAsBoolean()) return true;
            context.waitTick();
        }
        return done.getAsBoolean();
    }

    private static void connect(ClientGameTestContext context) {
        context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(ADDRESS),
                new ServerData("ale test", ADDRESS, ServerData.Type.OTHER), false, null));
        context.waitFor(client -> client.player != null && client.level != null && client.screen == null, 1200);
        context.waitTicks(40);
    }

    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.level != null) client.disconnect(new TitleScreen(), false);
        });
        context.waitFor(client -> client.level == null, 600);
        context.waitTicks(20);
    }

    private static void command(ClientGameTestContext context, String cmd) {
        context.runOnClient(client -> client.player.connection.sendCommand(cmd));
        context.waitTicks(5);
    }
}
