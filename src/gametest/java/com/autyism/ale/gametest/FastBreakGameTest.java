package com.autyism.ale.gametest;

import com.autyism.ale.api.FastBlockBreaking;
import com.autyism.ale.mining.FastBreakPayload;
import com.autyism.ale.mining.ServerFastBreak;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Properties;

/**
 * 秒破深板岩（服务端也装 ALE；单人游戏的内置服务端也算）。下界合金镐效率 V、急迫 II：深板岩一 tick 54%。
 * 1. 带请求的开始挖掘：一下挖掉，镐磨损 1，掉圆石深板岩（和原版秒破一样）；
 * 2. 不带请求（玩家自己挖）：照原版，一 tick 挖不掉；
 * 3. 不在标签里的黑曜石、急迫 I（一 tick 47%）：带请求也不秒破；
 * 4. 请求的是另一格：不秒破，请求也不会留给下一个包；
 * 5. 独立服务端（真实网络连接）照样能用；服务端不收请求（相当于没装 ALE）时客户端不可用、一个请求都不发，挖掘照原版。
 */
@SuppressWarnings("UnstableApiUsage")
public final class FastBreakGameTest implements FabricClientGameTest {
    private static final int FLOOR = 100;
    private static final int Y = FLOOR + 1;
    /** 玩家站的地方 */
    private static final BlockPos STAND = new BlockPos(0, Y, 0);
    private static final BlockPos FAST = new BlockPos(-1, Y, 2);
    private static final BlockPos MANUAL = new BlockPos(0, Y, 2);
    private static final BlockPos OBSIDIAN = new BlockPos(1, Y, 2);
    private static final BlockPos STARTED = new BlockPos(2, Y, 2);
    private static final BlockPos REQUESTED = new BlockPos(-1, Y + 1, 2);
    private static final BlockPos HASTE_ONE = new BlockPos(0, Y + 1, 2);
    private static final List<BlockPos> DEEPSLATE = List.of(FAST, MANUAL, STARTED, REQUESTED, HASTE_ONE);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("fastbreak")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            TestServerContext server = sp.getServer();
            prepare(context, server);
            checkAll(context, server, "single player");
        }
        dedicatedServer(context);
        GT.log("[fastbreak] OK: deepslate breaks at once with the request (single player and a dedicated server), "
                + "everything else mines the vanilla way, and without ALE on the server the client sends nothing");
    }

    private static void dedicatedServer(ClientGameTestContext context) {
        Properties properties = new Properties();
        // 不用 25565（本机真正的服务器用这个端口）
        properties.setProperty("server-port", "25572");
        properties.setProperty("view-distance", "4");
        properties.setProperty("simulation-distance", "4");
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            try (TestServerConnection connection = server.connect()) {
                joined(context, server, connection);
                prepare(context, server);
                checkAll(context, server, "dedicated server");
            }
            // 服务端不收请求（相当于服务端没装 ALE）
            ServerPlayNetworking.unregisterGlobalReceiver(FastBreakPayload.TYPE.id());
            try (TestServerConnection connection = server.connect()) {
                joined(context, server, connection);
                prepare(context, server);
                context.waitTicks(20);
                if (context.computeOnClient(c -> FastBlockBreaking.isAvailable())) {
                    throw new AssertionError("[fastbreak] the client thinks a server without ALE breaks deepslate at once");
                }
                int sentBefore = SENT[0];
                context.runOnClient(c -> FastBlockBreaking.breakNow(FAST, () -> c.gameMode.startDestroyBlock(FAST, Direction.NORTH)));
                expectStays(context, server, "without ALE on the server", FAST, Blocks.DEEPSLATE);
                if (SENT[0] != sentBefore) throw new AssertionError("[fastbreak] the client sent a request to a server without ALE");
                GT.log("[fastbreak] dedicated server without ALE: not available, nothing sent, deepslate mined the vanilla way");
            } finally {
                ServerFastBreak.register();
            }
        }
    }

    /** 客户端发出的请求数（ClientPlayNetworking 发出之前数，见 countRequests） */
    private static final int[] SENT = {0};

    private static void joined(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        connection.getClientWorld().waitForChunksRender();
        GT.waitServer(context, () -> server.computeOnServer(s -> !s.getPlayerList().getPlayers().isEmpty()
                && s.getPlayerList().getPlayers().getFirst().connection.hasClientLoaded()), 400, "[fastbreak] the player never finished joining");
    }

    /** 石头地面、一排方块、站在地上的玩家、下界合金镐效率 V、急迫 II */
    private static void prepare(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-4, FLOOR, -3), new BlockPos(5, FLOOR + 4, 5))) {
                level.setBlockAndUpdate(pos, pos.getY() == FLOOR ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            for (BlockPos pos : DEEPSLATE) level.setBlockAndUpdate(pos, Blocks.DEEPSLATE.defaultBlockState());
            level.setBlockAndUpdate(OBSIDIAN, Blocks.OBSIDIAN.defaultBlockState());
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(-6, FLOOR - 2, -6, 7, FLOOR + 6, 7))) item.discard();
        });
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a hotbar.0 with minecraft:netherite_pickaxe[minecraft:enchantments={\"minecraft:efficiency\":5}]");
        server.runCommand("effect clear @a");
        server.runCommand("effect give @a minecraft:haste infinite 1 true");
        server.runCommand("tp @a 0.5 " + Y + " 0.5 0 20");
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            player.getInventory().setSelectedSlot(0);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        });
        context.waitFor(c -> c.player != null && c.player.getInventory().getSelectedItem().is(Items.NETHERITE_PICKAXE)
                && c.player.hasEffect(net.minecraft.world.effect.MobEffects.HASTE) && c.player.onGround()
                && Math.abs(c.player.getZ() - 0.5) < 0.01 && c.level.getBlockState(HASTE_ONE).is(Blocks.DEEPSLATE), 300);
        context.waitTicks(5);
    }

    private static void checkAll(ClientGameTestContext context, TestServerContext server, String where) {
        String tag = "[fastbreak/" + where + "]";
        float progress = context.computeOnClient(c -> c.level.getBlockState(FAST).getDestroyProgress(c.player, c.level, FAST));
        boolean available = context.computeOnClient(c -> FastBlockBreaking.isAvailable());
        boolean can = context.computeOnClient(c -> FastBlockBreaking.canBreakNow(c.level.getBlockState(FAST), progress));
        GT.log(tag + " deepslate progress per tick=" + progress + ", available=" + available + ", canBreakNow=" + can);
        if (!available || !can) throw new AssertionError(tag + " fast breaking is not available for deepslate");

        // 1. 带请求：一下挖掉，镐磨损 1，掉落照原版
        int damageBefore = pickDamage(server);
        int sentBefore = SENT[0];
        context.runOnClient(c -> FastBlockBreaking.breakNow(FAST, () -> c.gameMode.startDestroyBlock(FAST, Direction.NORTH)));
        int ticks = GT.waitServer(context, () -> server.computeOnServer(s -> s.overworld().getBlockState(FAST).isAir()), 3,
                tag + " deepslate was not broken at once");
        context.runOnClient(c -> c.gameMode.stopDestroyBlock());
        context.waitTicks(5);
        int wear = pickDamage(server) - damageBefore;
        boolean dropped = server.computeOnServer(s -> !s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(FAST).inflate(2),
                e -> e.getItem().is(Items.COBBLED_DEEPSLATE)).isEmpty());
        GT.log(tag + " with the request: broken after " + ticks + " ticks, pick wear=" + wear + ", cobbled deepslate dropped=" + dropped
                + ", requests sent=" + (SENT[0] - sentBefore));
        if (wear != 1) throw new AssertionError(tag + " the pickaxe was worn by " + wear);
        if (!dropped) throw new AssertionError(tag + " no cobbled deepslate dropped");
        if (SENT[0] - sentBefore != 1) throw new AssertionError(tag + " sent " + (SENT[0] - sentBefore) + " requests for one block");

        // 2. 玩家自己挖（不带请求）：一 tick 挖不掉
        context.runOnClient(c -> c.gameMode.startDestroyBlock(MANUAL, Direction.NORTH));
        expectStays(context, server, tag + " without the request", MANUAL, Blocks.DEEPSLATE);

        // 3. 不在标签里的黑曜石
        boolean canObsidian = context.computeOnClient(c -> FastBlockBreaking.canBreakNow(c.level.getBlockState(OBSIDIAN),
                c.level.getBlockState(OBSIDIAN).getDestroyProgress(c.player, c.level, OBSIDIAN)));
        if (canObsidian) throw new AssertionError(tag + " obsidian counted as fast-breakable");
        context.runOnClient(c -> FastBlockBreaking.breakNow(OBSIDIAN, () -> c.gameMode.startDestroyBlock(OBSIDIAN, Direction.NORTH)));
        expectStays(context, server, tag + " obsidian with the request", OBSIDIAN, Blocks.OBSIDIAN);

        // 4. 请求的是另一格：开始挖掘的这一格不秒破；请求被这个包取走，之后再挖请求的那一格也不秒破
        context.runOnClient(c -> {
            ClientPlayNetworking.send(new FastBreakPayload(FastBreakPayload.VERSION, REQUESTED));
            c.gameMode.startDestroyBlock(STARTED, Direction.NORTH);
        });
        expectStays(context, server, tag + " request for another block", STARTED, Blocks.DEEPSLATE);
        context.runOnClient(c -> c.gameMode.startDestroyBlock(REQUESTED, Direction.NORTH));
        expectStays(context, server, tag + " later START at the requested block", REQUESTED, Blocks.DEEPSLATE);

        // 5. 急迫 I：一 tick 47%，带请求也不秒破（客户端也知道）
        // 已经有急迫 II 时原版不会换成更弱的急迫 I：先清掉
        server.runCommand("effect clear @a minecraft:haste");
        server.runCommand("effect give @a minecraft:haste infinite 0 true");
        context.waitFor(c -> {
            var haste = c.player.getEffect(net.minecraft.world.effect.MobEffects.HASTE);
            return haste != null && haste.getAmplifier() == 0;
        }, 100);
        float slow = context.computeOnClient(c -> c.level.getBlockState(HASTE_ONE).getDestroyProgress(c.player, c.level, HASTE_ONE));
        if (context.computeOnClient(c -> FastBlockBreaking.canBreakNow(c.level.getBlockState(HASTE_ONE), slow))) {
            throw new AssertionError(tag + " deepslate at " + slow + " per tick counted as fast-breakable");
        }
        context.runOnClient(c -> {
            ClientPlayNetworking.send(new FastBreakPayload(FastBreakPayload.VERSION, HASTE_ONE));
            c.gameMode.startDestroyBlock(HASTE_ONE, Direction.NORTH);
        });
        expectStays(context, server, tag + " haste I (" + slow + " per tick)", HASTE_ONE, Blocks.DEEPSLATE);
        GT.log(tag + " OK: manual mining, obsidian, a request for another block and haste I all stay vanilla");
    }

    /** 开始挖了一格（不继续挖）：过几 tick 还在，然后停下 */
    private static void expectStays(ClientGameTestContext context, TestServerContext server, String what, BlockPos pos, Block block) {
        context.waitTicks(4);
        boolean stays = server.computeOnServer(s -> s.overworld().getBlockState(pos).is(block));
        context.runOnClient(c -> c.gameMode.stopDestroyBlock());
        context.waitTicks(2);
        if (!stays) throw new AssertionError("[fastbreak] " + what + ": " + pos.toShortString() + " was broken at once");
    }

    private static int pickDamage(TestServerContext server) {
        return server.computeOnServer(s -> {
            var inventory = s.getPlayerList().getPlayers().getFirst().getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.is(Items.NETHERITE_PICKAXE)) return stack.getDamageValue();
            }
            return -1;
        });
    }

    /** 客户端发出一个秒破请求（FastBreakPacketCounterMixin 调用） */
    public static void countRequest() {
        SENT[0]++;
    }
}
