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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

/**
 * 玩家反馈（10-28）：服务器上容器填得和投影一模一样，F 也不自己消失。他在末地建的是大投影：很多容器还没装，全都画着 F。
 * 投影里一大片装着东西的木桶，世界里同样的木桶全是空的（都画 F），只把玩家脚下的一个、和离玩家 12 格的一个装得一模一样，
 * 两个的 F 都要在 10 秒内消失（旧版本里一个都没去查）。
 * 连本地测试服务器 localhost:25599（不是玩家的服务器）。只在 -Pgt=manyfill 时跑。边长：-Dale.manyfill.side（默认 32，即 1024 个）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ManyFillGameTest implements FabricClientGameTest {
    private static final String NAME = "ale_manyfill";
    private static final String ITEMS = "{Items:[{Slot:0b,id:\"minecraft:stone\",count:5},{Slot:4b,id:\"minecraft:oak_log\",count:9}]}";
    private static final int LIMIT = 200;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("manyfill")) return;
        int side = Integer.getInteger("ale.manyfill.side", 32);
        BlockPos min = new BlockPos(40, -60, 40), max = min.offset(side - 1, 0, side - 1);
        BlockPos target = min.offset(side / 2, 0, side / 2);
        BlockPos target2 = target.offset(Math.min(12, side / 2 - 1), 0, 0);
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("forceload add " + min.getX() + " " + min.getZ() + " " + max.getX() + " " + max.getZ());
            context.waitTicks(40);
            sp.getServer().runCommand("fill " + xyz(min) + " " + xyz(max) + " barrel" + ITEMS);
            context.waitTicks(10);
            if (!sp.getServer().computeOnServer(s -> s.overworld().getBlockState(target).is(Blocks.BARREL))) throw new AssertionError("[manyfill] barrels not placed in single player");
            GT.captureAndPlace(context, sp, min, max, min, NAME);
            GT.removeAllPlacements(context);
        }
        ContainerCheckServuxGameTest.connect(context);
        try {
            ContainerCheckServuxGameTest.command(context, "gamemode creative");
            context.runOnClient(c -> c.options.renderDistance().set(12));
            ContainerCheckServuxGameTest.command(context, "fill " + xyz(min.offset(0, 0, 0)) + " " + xyz(max.offset(0, 5, 0)) + " air");
            ContainerCheckServuxGameTest.command(context, "fill " + xyz(min) + " " + xyz(max) + " barrel");
            ContainerCheckServuxGameTest.command(context, "tp @s " + (target.getX() + 0.5) + " -58 " + (target.getZ() + 0.5) + " 0 80");
            context.waitFor(c -> c.level.getBlockState(target).is(Blocks.BARREL) && c.player.getY() > -59, 200);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false);
                AleConfigs.Generic.CONTAINER_CHECK.setBooleanValue(false);
                LitematicaSchematic schematic = LitematicaSchematic.createFromFile(DataManager.getSchematicsBaseDirectory(), NAME + ".litematic");
                if (schematic == null) throw new AssertionError("[manyfill] could not read " + NAME);
                SchematicPlacement p = SchematicPlacement.createFor(schematic, min, NAME, true, true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
            });
            boolean drawn = ContainerCheckServuxGameTest.waitFor(context, () -> context.computeOnClient(c -> ContainerVerifier.isFillCandidate(target)), 400);
            context.waitTicks(200);
            GT.log("[manyfill] " + side * side + " empty barrels with F; target F drawn=" + drawn);
            ContainerCheckServuxGameTest.command(context, "item replace block " + xyz(target) + " container.0 with minecraft:stone 5");
            ContainerCheckServuxGameTest.command(context, "item replace block " + xyz(target) + " container.4 with minecraft:oak_log 9");
            ContainerCheckServuxGameTest.command(context, "item replace block " + xyz(target2) + " container.0 with minecraft:stone 5");
            ContainerCheckServuxGameTest.command(context, "item replace block " + xyz(target2) + " container.4 with minecraft:oak_log 9");
            int[] t = {0};
            boolean gone = ContainerCheckServuxGameTest.waitFor(context, () -> {
                if (t[0] % 100 == 0) GT.log("[manyfill] t" + t[0] + " candidate=" + context.computeOnClient(c -> ContainerVerifier.isFillCandidate(target))
                        + " " + context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.debug(target)));
                t[0]++;
                return context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(target) && ContainerVerifier.isKnownToMatch(target2));
            }, 1200);
            GT.log("[manyfill] next to the player: " + context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(target))
                    + ", 12 blocks away: " + context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(target2)));
            GT.log("[manyfill] the two filled exactly: F gone=" + gone + " after " + t[0] + " ticks (limit " + LIMIT + ")");
            context.waitTicks(10);
            GT.shot(context, "ale-manyfill");
            if (!gone || t[0] > LIMIT) throw new AssertionError("[manyfill] F took " + t[0] + " ticks (gone=" + gone + ")");
            GT.log("[manyfill] OK");
        } finally {
            GT.removeAllPlacements(context);
            ContainerCheckServuxGameTest.disconnect(context);
        }
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
