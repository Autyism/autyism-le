package com.autyism.ale.gametest;

import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.util.LayerMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Litematica's paste on a server with Servux and Autyism Server Tools (local test server, port 25599): the paste goes
 * to Servux and must work like single player. The target area is full of dirt, with a pig, an extra armor stand and a
 * barrel with items inside, first:
 * <ul>
 * <li>with one render layer only that layer is pasted (the rest stays dirt);</li>
 * <li>a full paste makes the area exactly the schematic (air included), in a few ticks;</li>
 * <li>the junk entities are gone, the schematic's armor stand is there once, the chest has its diamonds.</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class PasteServuxGameTest implements FabricClientGameTest {
    private static final String NAME = "ale_pasteservux";
    private static final BlockPos SP0 = new BlockPos(600, 64, 0);
    private static final int SX = 8, SY = 6, SZ = 8;
    private static final BlockPos T = new BlockPos(60, -60, 30);
    private static final BlockPos MARKER = new BlockPos(58, -60, 28);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("pasteservux")) return;
        // the schematic, made in single player
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, SP0.getX() - 2, SP0.getZ() - 2, SP0.getX() + SX + 2, SP0.getZ() + SZ + 2, SP0.getY() + SY + 2);
            ViewShots.begin(context, sp);
            ViewShots.lookAt(context, sp, SP0.offset(4, 0, 4));
            context.waitTicks(20);
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) level.setBlock(SP0.offset(x, 0, z), Blocks.STONE.defaultBlockState(), ViewShots.QUIET);
                for (int y = 1; y < 4; y++) {
                    level.setBlock(SP0.offset(0, y, 0), Blocks.OAK_PLANKS.defaultBlockState(), ViewShots.QUIET);
                    level.setBlock(SP0.offset(SX - 1, y, SZ - 1), Blocks.OAK_LOG.defaultBlockState(), ViewShots.QUIET);
                }
                level.setBlock(SP0.offset(3, 1, 3), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), ViewShots.QUIET);
                level.setBlock(SP0.offset(2, 1, 2), Blocks.CHEST.defaultBlockState(), ViewShots.QUIET);
                if (level.getBlockEntity(SP0.offset(2, 1, 2)) instanceof Container c) c.setItem(0, new ItemStack(Items.DIAMOND, 5));
                level.setBlock(SP0.offset(5, 2, 1), Blocks.GLASS.defaultBlockState(), ViewShots.QUIET);
            });
            sp.getServer().runCommand("summon armor_stand " + (SP0.getX() + 5.5) + " " + (SP0.getY() + 1) + " " + (SP0.getZ() + 5.5) + " {NoGravity:1b}");
            context.waitTicks(20);
            int stands = sp.getServer().computeOnServer(s -> s.overworld().getEntitiesOfClass(ArmorStand.class,
                    new AABB(SP0.getX(), SP0.getY(), SP0.getZ(), SP0.getX() + SX, SP0.getY() + SY, SP0.getZ() + SZ)).size());
            GT.log("[pasteservux] armor stands in the single player build: " + stands);
            GT.captureAndPlace(context, sp, SP0, SP0.offset(SX - 1, SY - 1, SZ - 1), SP0, NAME);
            GT.removeAllPlacements(context);
        }
        List<String> failures = new ArrayList<>();
        ContainerCheckServuxGameTest.connect(context);
        try {
            String box = xyz(T) + " " + xyz(T.offset(SX - 1, SY - 1, SZ - 1));
            ContainerCheckServuxGameTest.command(context, "gamemode creative");
            ContainerCheckServuxGameTest.command(context, "tp @s " + (T.getX() + 4) + " " + (T.getY() + 8) + " " + (T.getZ() - 6) + " 0 40");
            context.waitTicks(40);
            ContainerCheckServuxGameTest.command(context, "kill @e[type=!player,x=" + (T.getX() - 4) + ",y=" + (T.getY() - 4) + ",z=" + (T.getZ() - 4) + ",dx=20,dy=20,dz=20]");
            ContainerCheckServuxGameTest.command(context, "fill " + xyz(MARKER) + " " + xyz(MARKER) + " air");
            ContainerCheckServuxGameTest.command(context, "fill " + box + " dirt");
            ContainerCheckServuxGameTest.command(context, "setblock " + xyz(T.offset(4, 3, 4)) + " barrel{Items:[{Slot:0b,id:\"minecraft:emerald\",count:7}]}");
            ContainerCheckServuxGameTest.command(context, "summon pig " + (T.getX() + 6.5) + " " + (T.getY() + 2) + " " + (T.getZ() + 2.5) + " {NoAI:1b,Invulnerable:1b}");
            ContainerCheckServuxGameTest.command(context, "summon armor_stand " + (T.getX() + 1.5) + " " + (T.getY() + 2) + " " + (T.getZ() + 6.5) + " {Invulnerable:1b}");
            context.waitTicks(20);
            SchematicPlacement placement = context.computeOnClient(c -> {
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                Configs.Generic.PASTE_USING_SERVUX.setBooleanValue(true);
                Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(true);
                LitematicaSchematic schematic = LitematicaSchematic.createFromFile(DataManager.getSchematicsBaseDirectory(), NAME + ".litematic");
                if (schematic == null) throw new AssertionError("[pasteservux] could not read " + NAME);
                SchematicPlacement p = SchematicPlacement.createFor(schematic, T, NAME, true, true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
                DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(p);
                return p;
            });
            boolean servux = ContainerCheckServuxGameTest.waitFor(context,
                    () -> context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.available()), 400);
            GT.log("[pasteservux] Litematica found Servux: " + servux);
            if (!servux) failures.add("no-servux");
            GT.waitSchematicBlock(context, T, Blocks.STONE);

            // one render layer: y = T.y + 1
            context.runOnClient(c -> {
                var range = DataManager.getRenderLayerRange();
                range.setLayerMode(LayerMode.SINGLE_LAYER);
                range.setAxis(Direction.Axis.Y);
                range.setLayerSingle(T.getY() + 1);
                DataManager.getSchematicPlacementManager().pastePlacementToWorld(placement, false, true, c);
            });
            context.waitTicks(40);
            int[] layer = compare(context, T.getY() + 1, T.getY() + 1);
            int[] rest = compare(context, T.getY() + 2, T.getY() + SY - 1);
            boolean restDirt = context.computeOnClient(c -> {
                for (BlockPos p : BlockPos.betweenClosed(T.offset(0, 2, 0), T.offset(SX - 1, SY - 1, SZ - 1))) {
                    if (!c.level.getBlockState(p).is(Blocks.DIRT) && !p.equals(T.offset(4, 3, 4))) return false;
                }
                return true;
            });
            GT.log("[pasteservux] layer paste: layer wrong " + layer[0] + "/" + layer[1] + ", layers above untouched (dirt) " + restDirt);
            if (layer[0] != 0) failures.add("layer-not-pasted");
            if (!restDirt) failures.add("layer-paste-touched-other-layers");

            // everything
            long[] start = new long[1];
            context.runOnClient(c -> {
                DataManager.getRenderLayerRange().setLayerMode(LayerMode.ALL);
                start[0] = c.level.getGameTime();
                DataManager.getSchematicPlacementManager().pastePlacementToWorld(placement, false, true, c);
            });
            int ticks = 0;
            int[] all = compare(context, T.getY(), T.getY() + SY - 1);
            while (all[0] != 0 && ticks < 200) {
                context.waitTick();
                ticks++;
                all = compare(context, T.getY(), T.getY() + SY - 1);
            }
            GT.log("[pasteservux] full paste: wrong " + all[0] + "/" + all[1] + " after " + ticks + " ticks");
            if (all[0] != 0) failures.add("full-paste-wrong-blocks");
            if (ticks > 20) failures.add("full-paste-slow");
            context.waitTicks(20);
            int[] entities = context.computeOnClient(c -> {
                AABB area = new AABB(T.getX() - 1, T.getY() - 1, T.getZ() - 1, T.getX() + SX + 1, T.getY() + SY + 3, T.getZ() + SZ + 1);
                int stands = c.level.getEntitiesOfClass(ArmorStand.class, area).size();
                int pigs = c.level.getEntities((net.minecraft.world.entity.Entity) null, area, e -> EntityType.getKey(e.getType()).getPath().equals("pig")).size();
                return new int[]{stands, pigs};
            });
            GT.log("[pasteservux] entities in the area: armor stands " + entities[0] + " (want 1), pigs " + entities[1] + " (want 0)");
            if (entities[0] != 1) failures.add("armor-stands-" + entities[0]);
            if (entities[1] != 0) failures.add("pig-left");
            ContainerCheckServuxGameTest.command(context, "execute if data block " + xyz(T.offset(2, 1, 2)) + " Items[{Slot:0b,id:\"minecraft:diamond\",count:5}] run setblock " + xyz(MARKER) + " gold_block");
            context.waitTicks(20);
            boolean chest = context.computeOnClient(c -> c.level.getBlockState(MARKER).is(Blocks.GOLD_BLOCK));
            GT.log("[pasteservux] chest has its 5 diamonds: " + chest);
            if (!chest) failures.add("chest-contents");
            // the pasted chest holds exactly what the schematic says: its F goes away without opening it (10-28: it stayed)
            BlockPos pasted = T.offset(2, 1, 2);
            int[] t = {0};
            boolean fGone = ContainerCheckServuxGameTest.waitFor(context, () -> {
                t[0]++;
                return context.computeOnClient(c -> com.autyism.ale.verifier.ContainerVerifier.isKnownToMatch(pasted));
            }, 400);
            GT.log("[pasteservux] pasted chest: F gone=" + fGone + " after " + t[0] + " ticks; candidate="
                    + context.computeOnClient(c -> com.autyism.ale.verifier.ContainerVerifier.isFillCandidate(pasted)) + " "
                    + context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.debug(pasted)));
            if (!fGone) failures.add("pasted-chest-F-stays");
            GT.shot(context, "ale-pasteservux");
        } finally {
            context.runOnClient(c -> DataManager.getRenderLayerRange().setLayerMode(LayerMode.ALL));
            GT.removeAllPlacements(context);
            ContainerCheckServuxGameTest.disconnect(context);
        }
        if (!failures.isEmpty()) throw new AssertionError("[pasteservux] " + failures);
        GT.log("[pasteservux] OK");
    }

    /** {wrong, total}: positions from y0 to y1 where the client world differs from the schematic */
    private static int[] compare(ClientGameTestContext context, int y0, int y1) {
        return context.computeOnClient(c -> {
            var ws = SchematicWorldHandler.getSchematicWorld();
            int wrong = 0, total = 0;
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(T.getX(), y0, T.getZ()), new BlockPos(T.getX() + SX - 1, y1, T.getZ() + SZ - 1))) {
                BlockState want = ws == null ? null : ws.getBlockState(p);
                total++;
                if (want == null || !c.level.getBlockState(p).equals(want)) wrong++;
            }
            return new int[]{wrong, total};
        });
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}
