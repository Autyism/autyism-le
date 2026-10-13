package com.autyism.ale.gametest;

import com.autyism.ale.api.ExactRailPlacement;
import com.autyism.ale.gametest.mixin.GuiOverlayAccessor;
import com.autyism.ale.rail.ExactRailPayload;
import com.autyism.ale.rail.ServerRails;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.util.WorldUtils;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻松放置按投影形状放铁轨（服务端也装 ALE）：
 * 1. 单人游戏（内置服务端）：四种铁轨的单独上坡、弯道、旁边已有铁轨时的上坡、不拉动已放好的铁轨、
 *    动力铁轨照常充能、打印机接口、新版轻松放置代码；上坡那一侧没有方块时不放、物品不少、有提示，方块放好后再放就对了；
 *    不合格的请求和别的位置的请求不影响放置，也不会留到下一次。
 * 2. 同一个进程里的独立服务端（真实网络连接，没有 Litematica 的放置协议）：照样按投影形状放。
 * 3. 同一个服务端不收形状请求（相当于服务端没装 ALE）：客户端一个请求都不发，轻松放置和原来一样（原版的形状）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class RailGameTest implements FabricClientGameTest {
    /** 客户端发出的形状请求数（RailPacketCounterMixin） */
    public static final AtomicInteger SENT = new AtomicInteger();

    private static final int FLOOR = 100;
    private static final BlockPos MIN = new BlockPos(-3, FLOOR, -3);
    private static final BlockPos MAX = new BlockPos(51, FLOOR + 3, 4);
    private static final String SCHEMATIC = "ale_rails";
    /** 上坡那一侧的支撑一开始不在世界里 */
    private static final BlockPos NEEDS_SUPPORT = rel(28, 0);
    private static final BlockPos NEEDS_SUPPORT_BLOCK = rel(28, -1);
    /** 不在投影里：手动发不合格 / 别的位置的请求 */
    private static final BlockPos INVALID_POS = rel(44, 0);
    private static final BlockPos OTHER_POS = rel(48, 0);
    private static final BlockPos OTHER_REQUEST_POS = rel(48, 3);

    private enum How { EASY_PLACE, POST_REWRITE, API }

    /** 一节要放的铁轨：位置、投影里的状态、周围一开始就在世界里的方块（支撑、已经放好的铁轨） */
    private record Case(String name, BlockPos pos, BlockState rail, Map<BlockPos, BlockState> around, How how) {
    }

    private static BlockPos rel(int x, int z) {
        return new BlockPos(x, FLOOR + 1, z);
    }

    private static BlockState rail(Block block, RailShape shape) {
        BlockState state = block.defaultBlockState();
        return state.hasProperty(BlockStateProperties.RAIL_SHAPE) ? state.setValue(BlockStateProperties.RAIL_SHAPE, shape)
                : state.setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, shape);
    }

    private static Map<BlockPos, BlockState> around(Object... posAndState) {
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        for (int i = 0; i < posAndState.length; i += 2) map.put((BlockPos) posAndState[i], (BlockState) posAndState[i + 1]);
        return map;
    }

    private static List<Case> cases() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        List<Case> list = new ArrayList<>();
        list.add(new Case("rail-ascending", rel(0, 0), rail(Blocks.RAIL, RailShape.ASCENDING_NORTH), around(rel(0, -1), stone), How.EASY_PLACE));
        list.add(new Case("powered-ascending", rel(4, 0), rail(Blocks.POWERED_RAIL, RailShape.ASCENDING_EAST), around(rel(5, 0), stone), How.EASY_PLACE));
        list.add(new Case("detector-ascending", rel(8, 0), rail(Blocks.DETECTOR_RAIL, RailShape.ASCENDING_SOUTH), around(rel(8, 1), stone), How.EASY_PLACE));
        list.add(new Case("activator-ascending", rel(12, 0), rail(Blocks.ACTIVATOR_RAIL, RailShape.ASCENDING_WEST), around(rel(11, 0), stone), How.EASY_PLACE));
        list.add(new Case("curve", rel(16, 0), rail(Blocks.RAIL, RailShape.SOUTH_EAST), around(), How.EASY_PLACE));
        // 原版：挨着的平铁轨把它连成平的
        list.add(new Case("slope-after-flat", rel(20, 0), rail(Blocks.RAIL, RailShape.ASCENDING_NORTH),
                around(rel(20, -1), stone, rel(20, 1), rail(Blocks.RAIL, RailShape.NORTH_SOUTH)), How.EASY_PLACE));
        // 原版：新铁轨把旁边已经放好的南北向铁轨拉成东西向
        list.add(new Case("beside-placed-rail", rel(25, 0), rail(Blocks.RAIL, RailShape.EAST_WEST),
                around(rel(24, 0), rail(Blocks.RAIL, RailShape.NORTH_SOUTH)), How.EASY_PLACE));
        list.add(new Case("powered-by-block", rel(32, 0), rail(Blocks.POWERED_RAIL, RailShape.ASCENDING_NORTH).setValue(BlockStateProperties.POWERED, true),
                around(rel(32, -1), stone, rel(33, 0), Blocks.REDSTONE_BLOCK.defaultBlockState()), How.EASY_PLACE));
        list.add(new Case("api", rel(36, 0), rail(Blocks.ACTIVATOR_RAIL, RailShape.ASCENDING_SOUTH), around(rel(36, 1), stone), How.API));
        list.add(new Case("post-rewrite-curve", rel(40, 0), rail(Blocks.RAIL, RailShape.NORTH_WEST), around(), How.POST_REWRITE));
        return list;
    }

    private static Case byName(String name) {
        return cases().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("rails")) return;
        try {
            singleplayer(context);
            dedicatedServer(context);
            GT.log("[rails] OK: rails go down in the schematic's shape with ALE on both sides (single player and a dedicated server), "
                    + "nothing is bent or placed where it can't stay, bad requests change nothing, and without ALE on the server the client sends nothing and places like before");
        } finally {
            context.runOnClient(c -> {
                Configs.Generic.EASY_PLACE_MODE.setBooleanValue(false);
                Configs.Generic.EASY_PLACE_POST_REWRITE.setBooleanValue(false);
            });
            GT.removeAllPlacements(context);
        }
    }

    // ---------------------------------------------------------------- single player

    private static void singleplayer(ClientGameTestContext context) {
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            TestServerContext server = sp.getServer();
            build(server, true, true);
            GT.captureAndPlace(context, sp, MIN, MAX, MIN, SCHEMATIC);
            removeRails(server, cases());
            server.runOnServer(s -> {
                ViewShots.set(s.overworld(), NEEDS_SUPPORT, Blocks.AIR.defaultBlockState());
                ViewShots.set(s.overworld(), NEEDS_SUPPORT_BLOCK, Blocks.AIR.defaultBlockState());
            });
            GT.waitSchematicBlock(context, byName("rail-ascending").pos(), Blocks.RAIL);
            preparePlayer(context, server);
            context.waitFor(c -> ExactRailPlacement.isAvailable(), 100);
            GT.log("[rails] single player: exact placement available on the client: true");

            for (Case c : cases()) placeAndCheck(context, server, c, true);
            needsSupport(context, server);
            badRequests(context, server);
            GT.log("[rails] single player OK, requests sent: " + SENT.get());
        }
    }

    /** 上坡那一侧没有方块：不放、物品不少、动作栏提示；方块放好后再放就是投影的形状 */
    private static void needsSupport(ClientGameTestContext context, TestServerContext server) {
        BlockState wanted = rail(Blocks.RAIL, RailShape.ASCENDING_NORTH);
        int railsBefore = countItem(server, Items.RAIL);
        easyPlace(context, server, NEEDS_SUPPORT, false);
        context.waitTicks(6);
        BlockState placed = server.computeOnServer(s -> s.overworld().getBlockState(NEEDS_SUPPORT));
        BlockState client = context.computeOnClient(c -> c.level.getBlockState(NEEDS_SUPPORT));
        int railsAfter = countItem(server, Items.RAIL);
        int drops = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(MIN).minmax(new net.minecraft.world.phys.AABB(MAX)).inflate(2)).size());
        String message = context.computeOnClient(c -> {
            Component m = ((GuiOverlayAccessor) c.gui).aleGt$overlayMessage();
            return m == null ? "" : m.getString();
        });
        GT.log("[rails] needs-support (no block on the high side yet): server=" + placed + " client=" + client + " rails in inventory "
                + railsBefore + " -> " + railsAfter + ", dropped items " + drops + ", action bar: \"" + message + "\"");
        if (!placed.isAir() || !client.isAir()) throw new AssertionError("[rails] an ascending rail without support was placed: " + placed);
        if (railsAfter != railsBefore) throw new AssertionError("[rails] a rail item was used although nothing was placed");
        if (drops != 0) throw new AssertionError("[rails] items dropped");
        if (!message.contains("north")) throw new AssertionError("[rails] no hint about the missing block on the high side: \"" + message + "\"");

        // 方块放好后再放（Litematica 2 秒内不会再点同一格）
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(NEEDS_SUPPORT_BLOCK, Blocks.STONE.defaultBlockState()));
        context.waitTicks(60);
        easyPlace(context, server, NEEDS_SUPPORT, false);
        expectState(context, server, "needs-support (after the block)", NEEDS_SUPPORT, wanted);
    }

    /** 不合格的请求、别的位置的请求：照原版放，而且请求不会留到下一次 */
    private static void badRequests(ClientGameTestContext context, TestServerContext server) {
        // 动力铁轨不能拐弯：服务端不收这个请求，照原版放（看向南 → 南北向）
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:powered_rail 16");
        aim(context, server, INVALID_POS);
        context.waitFor(c -> c.player.getMainHandItem().is(Items.POWERED_RAIL), 100);
        context.runOnClient(c -> {
            ClientPlayNetworking.send(new ExactRailPayload(ExactRailPayload.VERSION, INVALID_POS, Blocks.POWERED_RAIL, "south_east"));
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, clickFloor(INVALID_POS));
        });
        expectState(context, server, "invalid request (curved powered rail)", INVALID_POS, rail(Blocks.POWERED_RAIL, RailShape.NORTH_SOUTH));

        // 请求说的是另一格：这次放置照原版；请求也不留给之后在那一格的放置
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(OTHER_REQUEST_POS.north(), Blocks.STONE.defaultBlockState()));
        server.runCommand("item replace entity @a weapon.mainhand with minecraft:rail 16");
        aim(context, server, OTHER_POS);
        context.waitFor(c -> c.player.getMainHandItem().is(Items.RAIL), 100);
        context.runOnClient(c -> {
            ClientPlayNetworking.send(new ExactRailPayload(ExactRailPayload.VERSION, OTHER_REQUEST_POS, Blocks.RAIL, "ascending_north"));
            c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, clickFloor(OTHER_POS));
        });
        expectState(context, server, "request for another position", OTHER_POS, rail(Blocks.RAIL, RailShape.NORTH_SOUTH));
        aim(context, server, OTHER_REQUEST_POS);
        context.runOnClient(c -> c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, clickFloor(OTHER_REQUEST_POS)));
        expectState(context, server, "later placement at the requested position", OTHER_REQUEST_POS, rail(Blocks.RAIL, RailShape.NORTH_SOUTH));
    }

    // ---------------------------------------------------------------- dedicated server

    private static final List<String> SERVER_CASES = List.of("rail-ascending", "curve", "slope-after-flat", "beside-placed-rail");

    private static void dedicatedServer(ClientGameTestContext context) {
        Properties properties = new Properties();
        // 不用 25565（本机真正的服务器用这个端口）
        properties.setProperty("server-port", "25571");
        properties.setProperty("view-distance", "4");
        properties.setProperty("simulation-distance", "4");
        List<Case> serverCases = cases().stream().filter(c -> SERVER_CASES.contains(c.name())).toList();
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            build(server, false, false);
            // ALE 在服务端：照投影的形状放
            try (TestServerConnection connection = server.connect()) {
                joined(context, server, connection);
                context.waitFor(c -> ExactRailPlacement.isAvailable(), 200);
                GT.log("[rails] dedicated server with ALE: exact placement available on the client: true");
                for (Case c : serverCases) placeAndCheck(context, server, c, true);
            }

            // 服务端不收形状请求（相当于服务端没装 ALE）
            ServerPlayNetworking.unregisterGlobalReceiver(ExactRailPayload.TYPE.id());
            try {
                build(server, false, false);
                try (TestServerConnection connection = server.connect()) {
                    joined(context, server, connection);
                    context.waitTicks(40);
                    boolean available = context.computeOnClient(c -> ExactRailPlacement.isAvailable());
                    int sentBefore = SENT.get();
                    GT.log("[rails] dedicated server without ALE: exact placement available on the client: " + available);
                    if (available) throw new AssertionError("[rails] client thinks the server has ALE");
                    for (Case c : serverCases) placeAndCheck(context, server, c, false);
                    if (SENT.get() != sentBefore) throw new AssertionError("[rails] the client sent " + (SENT.get() - sentBefore) + " requests to a server without ALE");
                    GT.log("[rails] dedicated server without ALE: requests sent 0, Easy Place placed rails the vanilla way");
                }
            } finally {
                ServerRails.registerReceiver();
            }
        }
    }

    private static void joined(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
        connection.getClientWorld().waitForChunksRender();
        GT.waitServer(context, () -> server.computeOnServer(s -> !s.getPlayerList().getPlayers().isEmpty()
                && s.getPlayerList().getPlayers().getFirst().connection.hasClientLoaded()), 400, "[rails] the player never finished joining");
        GT.removeAllPlacements(context);
        placeFromFile(context);
        GT.waitSchematicBlock(context, byName("rail-ascending").pos(), Blocks.RAIL);
        preparePlayer(context, server);
    }

    // ---------------------------------------------------------------- helpers

    /** 地面、支撑、已经放好的铁轨；withRails：要放的铁轨也放上（截取投影用） */
    private static void build(TestServerContext server, boolean withRails, boolean withNeedsSupport) {
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (BlockPos pos : BlockPos.betweenClosed(MIN, MAX)) {
                ViewShots.set(level, pos, pos.getY() == FLOOR ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            for (Case c : cases()) {
                c.around().forEach((pos, state) -> ViewShots.set(level, pos, state));
                if (withRails) ViewShots.set(level, c.pos(), c.rail());
            }
            if (withNeedsSupport) {
                ViewShots.set(level, NEEDS_SUPPORT_BLOCK, Blocks.STONE.defaultBlockState());
                ViewShots.set(level, NEEDS_SUPPORT, rail(Blocks.RAIL, RailShape.ASCENDING_NORTH));
            }
        });
    }

    private static void removeRails(TestServerContext server, List<Case> cases) {
        server.runOnServer(s -> {
            for (Case c : cases) ViewShots.set(s.overworld(), c.pos(), Blocks.AIR.defaultBlockState());
        });
    }

    private static void placeFromFile(ClientGameTestContext context) {
        context.runOnClient(c -> {
            Path dir = DataManager.getSchematicsBaseDirectory();
            LitematicaSchematic schematic = LitematicaSchematic.createFromFile(dir, SCHEMATIC + ".litematic");
            if (schematic == null) throw new AssertionError("[rails] could not read " + SCHEMATIC);
            SchematicPlacement placement = SchematicPlacement.createFor(schematic, MIN, SCHEMATIC, true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
        });
    }

    /** 生存模式（放置会消耗物品）、能飞（悬在铁轨上方往下看）、四种铁轨；轻松放置打开 */
    private static void preparePlayer(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("gamemode survival @a");
        server.runCommand("clear @a");
        server.runCommand("give @a minecraft:rail 64");
        server.runCommand("give @a minecraft:powered_rail 16");
        server.runCommand("give @a minecraft:detector_rail 16");
        server.runCommand("give @a minecraft:activator_rail 16");
        server.runOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.runOnClient(c -> {
            Configs.Generic.EASY_PLACE_MODE.setBooleanValue(true);
            Configs.Generic.EASY_PLACE_FIRST.setBooleanValue(true);
            Configs.Generic.EASY_PLACE_POST_REWRITE.setBooleanValue(false);
            Configs.Generic.EASY_PLACE_SWAP_INTERVAL.setIntegerValue(0);
        });
        context.waitFor(c -> c.player != null && c.player.getAbilities().flying && c.player.getInventory().countItem(Items.ACTIVATOR_RAIL) > 0, 100);
    }

    /** 悬在 pos 正上方两格，往下看 */
    private static void aim(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
        double x = pos.getX() + 0.5, y = pos.getY() + 2.0, z = pos.getZ() + 0.5;
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 0 90", x, y, z));
        context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - x) < 0.01 && Math.abs(c.player.getZ() - z) < 0.01
                && Math.abs(c.player.getY() - y) < 0.01 && Math.abs(c.player.getXRot() - 90) < 0.01, 200);
        context.waitTicks(2);
    }

    /** 点 pos 下面那块地面的顶面 */
    private static BlockHitResult clickFloor(BlockPos pos) {
        return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, pos.below(), false);
    }

    private static void easyPlace(ClientGameTestContext context, TestServerContext server, BlockPos pos, boolean postRewrite) {
        aim(context, server, pos);
        context.runOnClient(c -> {
            Configs.Generic.EASY_PLACE_POST_REWRITE.setBooleanValue(postRewrite);
            if (postRewrite) EasyPlaceUtils.handleEasyPlaceWithMessage();
            else WorldUtils.handleEasyPlace(c);
            Configs.Generic.EASY_PLACE_POST_REWRITE.setBooleanValue(false);
        });
    }

    private static void placeAndCheck(ClientGameTestContext context, TestServerContext server, Case c, boolean exact) {
        int sentBefore = SENT.get();
        switch (c.how()) {
            case EASY_PLACE -> easyPlace(context, server, c.pos(), false);
            case POST_REWRITE -> easyPlace(context, server, c.pos(), true);
            case API -> {
                String item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(c.rail().getBlock().asItem()).toString();
                server.runCommand("item replace entity @a weapon.mainhand with " + item + " 16");
                aim(context, server, c.pos());
                context.waitFor(cl -> cl.player.getMainHandItem().is(c.rail().getBlock().asItem()), 100);
                InteractionResult result = context.computeOnClient(cl -> ExactRailPlacement.place(c.pos(), c.rail(),
                        () -> cl.gameMode.useItemOn(cl.player, InteractionHand.MAIN_HAND, clickFloor(c.pos()))));
                GT.log("[rails] api: ExactRailPlacement.place returned " + result);
            }
        }
        int sent = SENT.get() - sentBefore;
        if (exact) {
            expectState(context, server, c.name() + " (requests sent: " + sent + ")", c.pos(), c.rail());
            if (sent != 1) throw new AssertionError("[rails] " + c.name() + ": expected exactly one request, sent " + sent);
            List<String> changed = changedAround(server, c);
            if (!changed.isEmpty()) throw new AssertionError("[rails] " + c.name() + ": blocks around changed: " + changed);
        } else {
            // 服务端没装 ALE：照原版。上坡、弯道放成玩家面朝的南北向；旁边已经放好的铁轨被原版连过来
            waitPlaced(context, server, c.pos());
            context.waitTicks(4);
            BlockState placed = server.computeOnServer(s -> s.overworld().getBlockState(c.pos()));
            List<String> changed = changedAround(server, c);
            GT.log("[rails] " + c.name() + " without ALE on the server: " + placed + (changed.isEmpty() ? "" : ", around changed: " + changed));
            if (!placed.is(c.rail().getBlock())) throw new AssertionError("[rails] " + c.name() + ": nothing placed without ALE on the server");
            if (placed == c.rail() && changed.isEmpty()) {
                throw new AssertionError("[rails] " + c.name() + ": came out exactly like the schematic without ALE on the server (test case shows nothing)");
            }
        }
    }

    /** 周围一开始就在的方块（支撑、已经放好的铁轨）里变了的 */
    private static List<String> changedAround(TestServerContext server, Case c) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> e : c.around().entrySet()) {
            BlockState now = server.computeOnServer(s -> s.overworld().getBlockState(e.getKey()));
            if (now != e.getValue()) changed.add(e.getKey().toShortString() + " " + e.getValue() + " -> " + now);
        }
        return changed;
    }

    private static void waitPlaced(ClientGameTestContext context, TestServerContext server, BlockPos pos) {
        GT.waitServer(context, () -> !server.computeOnServer(s -> s.overworld().getBlockState(pos)).isAir(), 40,
                "[rails] nothing placed at " + pos.toShortString());
    }

    /** 服务端和客户端的世界里 pos 都是 want（客户端预测的和服务端放的一样） */
    private static void expectState(ClientGameTestContext context, TestServerContext server, String name, BlockPos pos, BlockState want) {
        waitPlaced(context, server, pos);
        context.waitTicks(4);
        BlockState placed = server.computeOnServer(s -> s.overworld().getBlockState(pos));
        BlockState client = context.computeOnClient(c -> c.level.getBlockState(pos));
        GT.log("[rails] " + name + ": " + placed + (client == placed ? "" : " (client sees " + client + ")") + (placed == want ? " ok" : " WRONG, want " + want));
        if (placed != want) throw new AssertionError("[rails] " + name + ": placed " + placed + ", want " + want);
        if (client != placed) throw new AssertionError("[rails] " + name + ": the client sees " + client + ", the server has " + placed);
    }

    private static int countItem(TestServerContext server, net.minecraft.world.item.Item item) {
        return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getInventory().countItem(item));
    }
}
