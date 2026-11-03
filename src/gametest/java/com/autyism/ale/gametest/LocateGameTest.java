package com.autyism.ale.gametest;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.locate.LocateIndex;
import com.autyism.ale.locate.ToolsHud;
import com.autyism.ale.locate.WrongBlockGuide;
import com.autyism.ale.render.ToolBoxes;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.util.LayerMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The wrong block guide ("locate"): in a small built schematic one block is missing, one is the wrong block, one has
 * the wrong state and one is extra. "Next" has to visit them nearest first (from the eye), start the round over after
 * the last one, "previous" goes back, the modes and the layer range filter, the arrow and the box are drawn, and the
 * guide goes away when the block is fixed. "locatebig": a 128 x 32 x 128 placement (half a million blocks), once mostly
 * correct and once with everything missing: the key press and every tick stay within their time budget.
 */
@SuppressWarnings("UnstableApiUsage")
public final class LocateGameTest implements FabricClientGameTest {
    private static final int X0 = 700, Y0 = 64, Z0 = 40;
    private static final BlockPos MIN = new BlockPos(X0, Y0, Z0), MAX = new BlockPos(X0 + 6, Y0 + 3, Z0 + 6);
    private static final BlockPos MISSING = new BlockPos(X0 + 1, Y0 + 1, Z0 + 6);
    private static final BlockPos WRONG_BLOCK = new BlockPos(X0 + 5, Y0, Z0 + 5);
    private static final BlockPos WRONG_STATE = new BlockPos(X0 + 6, Y0 + 2, Z0 + 6);
    private static final BlockPos EXTRA = new BlockPos(X0 + 3, Y0 + 3, Z0 + 3);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (GTFilter.enabled("locate")) small(context);
        if (GTFilter.enabled("locatebig")) big(context);
    }

    // ---------------- the small schematic ----------------

    private static BlockState source(BlockPos p) {
        if (p.equals(EXTRA)) return Blocks.AIR.defaultBlockState();
        if (p.equals(WRONG_STATE)) return Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        return switch (p.getY() - Y0) {
            case 0 -> Blocks.STONE.defaultBlockState();
            case 1 -> Blocks.OAK_PLANKS.defaultBlockState();
            case 2 -> Blocks.BRICKS.defaultBlockState();
            default -> Blocks.SMOOTH_STONE.defaultBlockState();
        };
    }

    private static void small(ClientGameTestContext context) {
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 6, Z0 - 6, X0 + 12, Z0 + 16, 80);
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("time set noon");
            tp(context, sp, X0 + 3.5, Y0, Z0 + 12.5, 180, 12);
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) ViewShots.set(level, p.immutable(), source(p));
            });
            context.waitTicks(5);
            GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_locate");
            GT.waitSchematicBlock(context, WRONG_STATE, Blocks.OAK_STAIRS);
            // the world differs in four places; a wall (outside the placement) between the player and the blocks
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                ViewShots.set(level, MISSING, Blocks.AIR.defaultBlockState());
                ViewShots.set(level, WRONG_BLOCK, Blocks.GOLD_BLOCK.defaultBlockState());
                ViewShots.set(level, WRONG_STATE, Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
                ViewShots.set(level, EXTRA, Blocks.DIAMOND_BLOCK.defaultBlockState());
                for (int x = X0 - 3; x <= X0 + 9; x++) {
                    for (int y = Y0; y <= Y0 + 5; y++) ViewShots.set(level, new BlockPos(x, y, Z0 + 9), Blocks.COBBLESTONE.defaultBlockState());
                }
            });
            context.waitTicks(10);
            context.runOnClient(c -> {
                AleToolsConfigs.LOCATE.setBooleanValue(true);
                AleToolsConfigs.LOCATE_MODE.setOptionListValue(AleToolsConfigs.LocateMode.BOTH);
                WrongBlockGuide.clear(false);
            });

            // expected order: nearest from the eye first
            Vec3 eye = context.computeOnClient(c -> c.player.getEyePosition());
            List<BlockPos> order = new ArrayList<>(List.of(MISSING, WRONG_BLOCK, WRONG_STATE, EXTRA));
            order.sort(Comparator.comparingDouble(p -> eye.distanceToSqr(Vec3.atCenterOf(p))));
            GT.log("[locate] expected order " + order);
            for (int i = 0; i < order.size(); i++) expect(context, "next " + (i + 1), order.get(i), failures);
            // after the last one the round starts over (the one shown now is skipped)
            expect(context, "next after the round", order.getFirst(), failures);
            // previous: back to the one before
            context.runOnClient(c -> WrongBlockGuide.previous());
            context.waitTicks(2);
            check(context, "previous", order.getLast(), failures);

            // the arrow, the text and the box
            long arrows = ToolsHud.arrowFrames, boxes = ToolBoxes.guideFrames;
            context.waitTicks(10);
            boolean drawn = ToolsHud.arrowFrames > arrows && ToolBoxes.guideFrames > boxes && ToolBoxes.lastGuideBoxes == 1;
            List<String> lines = ToolsHud.lastLines;
            String foundName = Blocks.DIAMOND_BLOCK.getName().getString();
            boolean text = lines.size() >= 3 && lines.get(2).contains(foundName);
            GT.log("[locate] hud: arrows +" + (ToolsHud.arrowFrames - arrows) + " boxes +" + (ToolBoxes.guideFrames - boxes) + " lines " + lines
                    + " angle " + String.format(Locale.ROOT, "%.2f", ToolsHud.lastArrowAngle) + (drawn && text ? " ok" : " WRONG"));
            if (!drawn || !text) failures.add("hud");
            GT.shot(context, "ale-locate-guide");

            // a target to the left: the arrow points left (screen angle about pi)
            tp(context, sp, X0 + 3.5, Y0, Z0 + 12.5, 90, 0);
            context.runOnClient(c -> WrongBlockGuide.pointAt(WRONG_STATE, source(WRONG_STATE), Blocks.AIR.defaultBlockState(), 2));
            context.waitTicks(5);
            double angle = ToolsHud.lastArrowAngle;
            GT.log("[locate] looking west, target to the north-east: arrow angle " + String.format(Locale.ROOT, "%.2f", angle));
            // looking west (yaw 90), north is to the right: the target is behind-right, so the arrow points right-ish (|angle| < pi/2)
            if (Math.abs(angle) > Math.PI / 2) failures.add("arrow direction");
            GT.shot(context, "ale-locate-arrow-side");
            tp(context, sp, X0 + 3.5, Y0, Z0 + 12.5, 180, 12);

            // missing only: just the missing block, and it stays when pressed again (the only one)
            context.runOnClient(c -> {
                WrongBlockGuide.clear(false);
                AleToolsConfigs.LOCATE_MODE.setOptionListValue(AleToolsConfigs.LocateMode.MISSING_ONLY);
            });
            expect(context, "missing only", MISSING, failures);
            expect(context, "missing only again", MISSING, failures);
            // fixing it: the guide goes away
            sp.getServer().runOnServer(s -> ViewShots.set(s.overworld(), MISSING, source(MISSING)));
            context.waitTicks(10);
            boolean gone = context.computeOnClient(c -> WrongBlockGuide.target() == null);
            GT.log("[locate] fixed the missing block: guide " + (gone ? "gone ok" : "STILL THERE"));
            if (!gone) failures.add("fixed");

            // wrong only, in the top layer only: the extra block
            context.runOnClient(c -> {
                WrongBlockGuide.clear(false);
                AleToolsConfigs.LOCATE_MODE.setOptionListValue(AleToolsConfigs.LocateMode.WRONG_ONLY);
                var range = DataManager.getRenderLayerRange();
                range.setLayerMode(LayerMode.SINGLE_LAYER);
                range.setAxis(Direction.Axis.Y);
                range.setLayerSingle(Y0 + 3);
            });
            expect(context, "wrong only, top layer", EXTRA, failures);
            context.runOnClient(c -> {
                DataManager.getRenderLayerRange().setLayerMode(LayerMode.ALL);
                WrongBlockGuide.clear(false);
                AleToolsConfigs.LOCATE_MODE.setOptionListValue(AleToolsConfigs.LocateMode.BOTH);
            });
            // a block placed wrong after the scan is found (the section is scanned again)
            BlockPos late = new BlockPos(X0 + 3, Y0 + 1, Z0 + 6);
            sp.getServer().runOnServer(s -> ViewShots.set(s.overworld(), late, Blocks.GLASS.defaultBlockState()));
            context.waitTicks(10);
            List<BlockPos> rest = new ArrayList<>(List.of(late, WRONG_BLOCK, WRONG_STATE, EXTRA));
            rest.sort(Comparator.comparingDouble(p -> eye.distanceToSqr(Vec3.atCenterOf(p))));
            expect(context, "after a new wrong block", rest.getFirst(), failures);

            if (!failures.isEmpty()) throw new AssertionError("[locate] wrong: " + failures);
            GT.log("[locate] OK: nearest first, round starts over, previous, modes, layer range, arrow + box, fixed block clears, rescans changed sections");
        } finally {
            context.runOnClient(c -> {
                WrongBlockGuide.clear(false);
                AleToolsConfigs.LOCATE_MODE.setOptionListValue(AleToolsConfigs.LocateMode.BOTH);
                DataManager.getRenderLayerRange().setLayerMode(LayerMode.ALL);
            });
            GT.removeAllPlacements(context);
        }
    }

    private static void tp(ClientGameTestContext context, TestSingleplayerContext sp, double x, int y, double z, float yaw, float pitch) {
        sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f", x, y, z, yaw, pitch));
        context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - x) < 0.01 && Math.abs(c.player.getZ() - z) < 0.01
                && Math.abs(net.minecraft.util.Mth.wrapDegrees(c.player.getYRot() - yaw)) < 0.5f, 200);
    }

    /** Press "next" and wait for the guide to settle on a target */
    private static void expect(ClientGameTestContext context, String step, BlockPos want, List<String> failures) {
        context.runOnClient(c -> WrongBlockGuide.next());
        for (int i = 0; i < 200 && context.computeOnClient(c -> WrongBlockGuide.searching()); i++) context.waitTick();
        check(context, step, want, failures);
    }

    private static void check(ClientGameTestContext context, String step, BlockPos want, List<String> failures) {
        BlockPos got = context.computeOnClient(c -> WrongBlockGuide.target() == null ? null : WrongBlockGuide.target().pos());
        boolean ok = want.equals(got);
        GT.log("[locate] " + step + ": " + got + (ok ? " ok" : " WRONG (want " + want + ")"));
        if (!ok) failures.add(step);
    }

    // ---------------- big schematic: time budget ----------------

    private static void big(ClientGameTestContext context) {
        final int bx = 1000, by = 64, bz = 1000, sx = 128, sy = 32, sz = 128;
        BlockPos min = new BlockPos(bx, by, bz), max = new BlockPos(bx + sx - 1, by + sy - 1, bz + sz - 1);
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runCommand("gamemode spectator @a");
            sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", bx + 20, by + sy + 4, bz + 20));
            context.waitTicks(40);
            long t0 = System.nanoTime();
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (BlockPos p : BlockPos.betweenClosed(min, max)) {
                    int y = p.getY() - by;
                    BlockState st = y % 8 == 7 ? Blocks.AIR.defaultBlockState() : (((p.getX() + p.getZ()) & 3) == 0 ? Blocks.OAK_PLANKS : Blocks.STONE).defaultBlockState();
                    level.setBlock(p, st, 2 | 16);
                }
            });
            GT.log("[locatebig] built " + (sx * sy * sz) + " blocks in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
            context.waitTicks(40);
            var placement = GT.captureAndPlace(context, sp, min, max, min, "ale_locate_big");
            GT.waitSchematicBlock(context, new BlockPos(bx + 31, by + 1, bz + 30), Blocks.STONE);
            // three missing blocks: near, middle, far from where the player will stand
            BlockPos near = new BlockPos(bx + 22, by + 5, bz + 22), mid = new BlockPos(bx + 60, by + 5, bz + 60), far = new BlockPos(bx + 120, by + 2, bz + 120);
            sp.getServer().runOnServer(s -> {
                for (BlockPos p : List.of(near, mid, far)) s.overworld().setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
            });
            context.waitTicks(20);
            sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", bx + 20, by + sy + 4, bz + 20));
            context.waitTicks(20);
            // start cold: nothing scanned yet (the index stays warm for a while after the last use)
            context.runOnClient(c -> {
                WrongBlockGuide.clear(false);
                LocateIndex.reset();
                LocateIndex.longestTickNs = 0;
            });
            long pressNs = context.computeOnClient(c -> {
                LocateIndex.reset(); // (in the same tick as the press: no scanning in between)
                LocateIndex.longestTickNs = 0;
                long s0 = System.nanoTime();
                WrongBlockGuide.next();
                return System.nanoTime() - s0;
            });
            int waited = 0;
            while (waited < 1200 && context.computeOnClient(c -> WrongBlockGuide.searching())) {
                context.waitTick();
                waited++;
            }
            BlockPos got = context.computeOnClient(c -> WrongBlockGuide.target() == null ? null : WrongBlockGuide.target().pos());
            long longest = LocateIndex.longestTickNs;
            GT.log(String.format(Locale.ROOT, "[locatebig] mostly correct: key press %.2f ms, found %s after %d ticks, longest tick %.2f ms, %d sections scanned",
                    pressNs / 1e6, got, waited, longest / 1e6, LocateIndex.scannedSections));
            if (!near.equals(got)) failures.add("nearest missing (got " + got + ")");
            if (pressNs > 25_000_000L) failures.add("key press too slow");
            if (longest > 12_000_000L) failures.add("tick too slow");
            // let the whole area be scanned and look at the per tick time once more
            for (int i = 0; i < 400 && !context.computeOnClient(c -> LocateIndex.complete()); i++) context.waitTick();
            GT.log(String.format(Locale.ROOT, "[locatebig] whole area scanned: complete=%s, %d sections, %d differences cached, longest tick %.2f ms",
                    LocateIndex.complete(), LocateIndex.scannedSections, LocateIndex.cachedCount(), LocateIndex.longestTickNs / 1e6));
            if (LocateIndex.longestTickNs > 12_000_000L) failures.add("tick too slow (full scan)");

            // the same schematic placed where nothing is built: half a million missing blocks
            context.runOnClient(c -> {
                placement.setOrigin(new BlockPos(bx + 300, by, bz), msg -> {});
                WrongBlockGuide.clear(false);
            });
            sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", bx + 300 + 64, by + sy + 4, bz + 64));
            context.waitTicks(60);
            context.runOnClient(c -> {
                LocateIndex.reset();
                LocateIndex.longestTickNs = 0;
            });
            long press2 = context.computeOnClient(c -> {
                LocateIndex.reset(); // (in the same tick as the press: no scanning in between)
                LocateIndex.longestTickNs = 0;
                long s0 = System.nanoTime();
                WrongBlockGuide.next();
                return System.nanoTime() - s0;
            });
            waited = 0;
            while (waited < 1200 && context.computeOnClient(c -> WrongBlockGuide.searching() || WrongBlockGuide.target() == null)) {
                context.waitTick();
                waited++;
            }
            BlockPos got2 = context.computeOnClient(c -> WrongBlockGuide.target() == null ? null : WrongBlockGuide.target().pos());
            for (int i = 0; i < 600 && !context.computeOnClient(c -> LocateIndex.complete()); i++) context.waitTick();
            GT.log(String.format(Locale.ROOT, "[locatebig] all missing: key press %.2f ms, found %s after %d ticks, longest tick %.2f ms, %d differences cached",
                    press2 / 1e6, got2, waited, LocateIndex.longestTickNs / 1e6, LocateIndex.cachedCount()));
            if (got2 == null) failures.add("nothing found where everything is missing");
            if (press2 > 25_000_000L || LocateIndex.longestTickNs > 12_000_000L) failures.add("too slow with everything missing");
            if (!failures.isEmpty()) throw new AssertionError("[locatebig] wrong: " + failures);
            GT.log("[locatebig] OK");
        } finally {
            context.runOnClient(c -> WrongBlockGuide.clear(false));
            GT.removeAllPlacements(context);
        }
    }
}
