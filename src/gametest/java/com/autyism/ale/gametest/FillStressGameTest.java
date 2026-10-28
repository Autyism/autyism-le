package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.verifier.ContainerVerifier;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
//? if >=1.21.11 {
import fi.dy.masa.litematica.data.EntityDataManager;
//?} else
//import fi.dy.masa.litematica.data.EntitiesDataStorage;
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

/**
 * 玩家反馈（10-28）：服务器上容器填得和投影一模一样，F 也不自己消失，要打开一次。
 * 像他的基地一样：世界里很多装着东西的箱子（Container Searcher 要记住它们，-MoreMods 加上），“检查容器内容”开着，
 * 再看填好的漏斗和熔炉的 F 多久消失。连本地测试服务器 localhost:25599（不是玩家的服务器）。
 * 只在 -Pgt=fillstress 时跑。箱子数量：-Dale.fillstress.chests（默认 2048）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class FillStressGameTest implements FabricClientGameTest {
    private static final String ADDRESS = "localhost:25599";
    private static final BlockPos HOPPER = new BlockPos(20, -60, 20);
    private static final BlockPos FURNACE = new BlockPos(23, -60, 20);
    private static final String NAME = "ale_fillstress";
    /** F 至少要在这么多 tick 里消失（5 秒） */
    private static final int LIMIT = 100;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("fillstress")) return;
        int chests = Integer.getInteger("ale.fillstress.chests", 2048);
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
        connect(context);
        try {
            command(context, "gamemode creative");
            // 像玩家一样看得远：Container Searcher 记住看得到的所有箱子
            context.runOnClient(c -> c.options.renderDistance().set(12));
            command(context, "fill 15 -60 15 30 -55 30 air");
            // 一大片装着东西的箱子（一层一层，每层 64 × 16，隔一格放，不连成大箱子）
            int placed = 0;
            for (int layer = 0; placed < chests; layer++) {
                int y = -59 + layer * 2;
                command(context, "fill -40 " + y + " 40 87 " + y + " 71 air");
                for (int row = 0; row < 16 && placed < chests; row++) {
                    int z = 40 + row * 2;
                    command(context, "fill -40 " + y + " " + z + " 87 " + y + " " + z + " chest{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:3}]}");
                    placed += 128;
                }
            }
            GT.log("[fillstress] " + placed + " chests with items in the world");
            command(context, "setblock " + xyz(HOPPER) + " hopper");
            command(context, "setblock " + xyz(FURNACE) + " furnace");
            command(context, "tp @s 21.5 -60 15.5 0 30");
            context.waitFor(c -> c.level.getBlockState(FURNACE).is(Blocks.FURNACE) && c.player.getZ() < 16, 200);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false);
                AleConfigs.Generic.CONTAINER_CHECK.setBooleanValue(Boolean.parseBoolean(System.getProperty("ale.fillstress.check", "true")));
                var dir = DataManager.getSchematicsBaseDirectory();
                LitematicaSchematic schematic = LitematicaSchematic.createFromFile(dir, NAME + ".litematic");
                if (schematic == null) throw new AssertionError("[fillstress] could not read " + NAME);
                SchematicPlacement p = SchematicPlacement.createFor(schematic, HOPPER, NAME, true, true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
            });
            boolean drawn = waitFor(context, () -> context.computeOnClient(c -> ContainerVerifier.isFillCandidate(HOPPER) && ContainerVerifier.isFillCandidate(FURNACE)), 400);
            GT.log("[fillstress] F drawn=" + drawn + ", Container Searcher loaded=" + net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("csearcher"));
            // 让 Container Searcher 和检查先跑一会儿，像玩家在基地里待了一阵
            for (int i = 0; i < 6; i++) {
                context.waitTicks(100);
                GT.log("[fillstress] t" + (i + 1) * 100 + " Litematica queue=" + queue(context));
            }
            command(context, "item replace block " + xyz(HOPPER) + " container.0 with minecraft:iron_ingot 5");
            command(context, "item replace block " + xyz(FURNACE) + " container.1 with minecraft:coal 8");
            int[] t = {0};
            boolean gone = waitFor(context, () -> {
                if (t[0] % 100 == 0) GT.log("[fillstress] after filling t" + t[0] + ": queue=" + queue(context) + " "
                        + context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.debug(HOPPER)));
                t[0]++;
                return context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(HOPPER) && ContainerVerifier.isKnownToMatch(FURNACE));
            }, 2400);
            GT.log("[fillstress] filled exactly like the schematic: F gone=" + gone + " after " + t[0] + " ticks (limit " + LIMIT + ")");
            if (!gone || t[0] > LIMIT) throw new AssertionError("[fillstress] F took " + t[0] + " ticks (gone=" + gone + ")");
            GT.log("[fillstress] OK");
        } finally {
            GT.removeAllPlacements(context);
            disconnect(context);
        }
    }

    private static int queue(ClientGameTestContext context) {
        //? if >=1.21.11 {
        return context.computeOnClient(c -> EntityDataManager.getInstance().getRequestTracker().getPendingBlockEntityCount());
        //?} else
        //return -1;
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
