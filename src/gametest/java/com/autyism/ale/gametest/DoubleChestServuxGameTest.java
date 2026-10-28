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

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家反馈（10-28）：服务器上容器和投影一模一样，F 也要打开一次才消失。服务器上没测过的：大箱子，和带组件的物品
 * （改了名字的、附魔书、药水、潜影盒里装着东西的）。投影里一个大箱子、一个木桶，世界里放一模一样的，F 都要自己消失。
 * 连本地测试服务器 localhost:25599（不是玩家的服务器）。只在 -Pgt=dcservux 时跑。
 */
@SuppressWarnings("UnstableApiUsage")
public final class DoubleChestServuxGameTest implements FabricClientGameTest {
    private static final BlockPos LEFT = new BlockPos(30, -60, 20), RIGHT = new BlockPos(31, -60, 20), BARREL = new BlockPos(34, -60, 20);
    private static final String NAME = "ale_dcservux";
    private static final String LEFT_ITEMS = "{Items:[{Slot:0b,id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:custom_name\":\"Bob\"}},"
            + "{Slot:5b,id:\"minecraft:enchanted_book\",count:1,components:{\"minecraft:stored_enchantments\":{\"minecraft:sharpness\":3}}},"
            + "{Slot:26b,id:\"minecraft:redstone\",count:64}]}";
    private static final String RIGHT_ITEMS = "{Items:[{Slot:3b,id:\"minecraft:potion\",count:1,components:{\"minecraft:potion_contents\":{potion:\"minecraft:strength\"}}},"
            + "{Slot:13b,id:\"minecraft:shulker_box\",count:1,components:{\"minecraft:container\":[{slot:0,item:{id:\"minecraft:stone\",count:12}}]}}]}";
    private static final String BARREL_ITEMS = "{Items:[{Slot:0b,id:\"minecraft:firework_rocket\",count:16,components:{\"minecraft:fireworks\":{flight_duration:2}}},"
            + "{Slot:8b,id:\"minecraft:white_wool\",count:30}]}";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("dcservux")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            place(c -> sp.getServer().runCommand(c));
            context.waitTicks(10);
            GT.captureAndPlace(context, sp, LEFT, BARREL, LEFT, NAME);
            GT.removeAllPlacements(context);
        }
        List<String> failures = new ArrayList<>();
        ContainerCheckServuxGameTest.connect(context);
        try {
            ContainerCheckServuxGameTest.command(context, "gamemode creative");
            ContainerCheckServuxGameTest.command(context, "fill 28 -60 18 36 -55 22 air");
            place(c -> ContainerCheckServuxGameTest.command(context, c));
            ContainerCheckServuxGameTest.command(context, "tp @s 32.5 -60 15.5 0 30");
            context.waitFor(c -> c.level.getBlockState(BARREL).is(Blocks.BARREL) && c.player.getZ() < 16, 200);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER.setBooleanValue(true);
                AleConfigs.Generic.FILL_MARKER_SKIP.setBooleanValue(false);
                LitematicaSchematic schematic = LitematicaSchematic.createFromFile(DataManager.getSchematicsBaseDirectory(), NAME + ".litematic");
                if (schematic == null) throw new AssertionError("[dcservux] could not read " + NAME);
                SchematicPlacement p = SchematicPlacement.createFor(schematic, LEFT, NAME, true, true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
            });
            int[] t = {0};
            boolean gone = ContainerCheckServuxGameTest.waitFor(context, () -> {
                if (t[0] % 100 == 0) {
                    for (BlockPos p : new BlockPos[]{LEFT, RIGHT, BARREL}) {
                        GT.log("[dcservux] t" + t[0] + " " + p.toShortString() + ": known=" + context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(p))
                                + " candidate=" + context.computeOnClient(c -> ContainerVerifier.isFillCandidate(p))
                                + " " + context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.debug(p)));
                    }
                }
                t[0]++;
                return context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(LEFT) && ContainerVerifier.isKnownToMatch(RIGHT)
                        && ContainerVerifier.isKnownToMatch(BARREL));
            }, 400);
            GT.log("[dcservux] same as the schematic, never opened: F gone on all=" + gone + " after " + t[0] + " ticks");
            if (!gone) {
                for (BlockPos p : new BlockPos[]{LEFT, RIGHT, BARREL}) {
                    boolean known = context.computeOnClient(c -> ContainerVerifier.isKnownToMatch(p));
                    if (!known) failures.add("F-stays-" + p.toShortString());
                }
            }
            context.waitTicks(20);
            GT.shot(context, "ale-dcservux");
        } finally {
            GT.removeAllPlacements(context);
            ContainerCheckServuxGameTest.disconnect(context);
        }
        if (!failures.isEmpty()) throw new AssertionError("[dcservux] " + failures);
        GT.log("[dcservux] OK");
    }

    /** Puts the double chest and the barrel with their items down through commands (same text in single player and on the server) */
    private static void place(java.util.function.Consumer<String> run) {
        run.accept("setblock " + xyz(LEFT) + " chest[facing=north,type=left]" + LEFT_ITEMS);
        run.accept("setblock " + xyz(RIGHT) + " chest[facing=north,type=right]" + RIGHT_ITEMS);
        run.accept("setblock " + xyz(BARREL) + " barrel[facing=up]" + BARREL_ITEMS);
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
