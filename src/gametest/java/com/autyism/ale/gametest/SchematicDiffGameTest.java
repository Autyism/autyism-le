package com.autyism.ale.gametest;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.diff.DiffListScreen;
import com.autyism.ale.diff.DiffPickScreen;
import com.autyism.ale.diff.DiffSession;
import com.autyism.ale.diff.SchematicDiff;
import com.autyism.ale.locate.ToolsHud;
import com.autyism.ale.locate.WrongBlockGuide;
import com.autyism.ale.render.ToolBoxes;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Schematic diff ("diff"): version 1 of a small build, version 2 with 3 blocks removed, 2 added and 4 changed (2 other
 * blocks, 2 other states). The counts have to be exact, in both directions (swap), the boxes sit on exactly those 9
 * world positions; a copy of version 2 saved with another origin only lines up with region alignment or a manual offset.
 * Through the real screens: "Compare with..." in the placement's settings, the picker, the list (nearest first); a
 * click on a row points the wrong block guide there, and it goes away when the new block is placed. Hide / show.
 */
@SuppressWarnings("UnstableApiUsage")
public final class SchematicDiffGameTest implements FabricClientGameTest {
    private static final int X0 = 800, Y0 = 64, Z0 = 40;
    private static final BlockPos MIN = new BlockPos(X0, Y0, Z0), MAX = new BlockPos(X0 + 4, Y0 + 3, Z0 + 4);

    private static BlockState v1(BlockPos p) {
        int x = p.getX() - X0, y = p.getY() - Y0, z = p.getZ() - Z0;
        boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
        switch (y) {
            case 0:
                return Blocks.STONE.defaultBlockState();
            case 1:
                return edge ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            case 2:
                if (x == 2 && z == 0) return stairs(Direction.SOUTH);
                if (x == 4 && z == 4) return stairs(Direction.EAST);
                return (x == 0 || x == 4) && (z == 0 || z == 4) ? Blocks.BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            default:
                return x == 2 && z == 2 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
    }

    private static BlockState stairs(Direction d) {
        return Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /** Version 2: what changes against version 1 (position -> new state) */
    private static Map<BlockPos, BlockState> changes() {
        Map<BlockPos, BlockState> m = new HashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        // removed
        m.put(new BlockPos(X0, Y0 + 1, Z0), air);
        m.put(new BlockPos(X0 + 4, Y0 + 1, Z0 + 4), air);
        m.put(new BlockPos(X0 + 2, Y0 + 3, Z0 + 2), air);
        // added
        m.put(new BlockPos(X0 + 2, Y0 + 1, Z0 + 2), Blocks.GOLD_BLOCK.defaultBlockState());
        m.put(new BlockPos(X0 + 1, Y0 + 2, Z0 + 2), Blocks.DIAMOND_BLOCK.defaultBlockState());
        // changed: two other blocks, two other states
        m.put(new BlockPos(X0, Y0, Z0), Blocks.COBBLESTONE.defaultBlockState());
        m.put(new BlockPos(X0 + 4, Y0, Z0 + 4), Blocks.BRICKS.defaultBlockState());
        m.put(new BlockPos(X0 + 2, Y0 + 2, Z0), stairs(Direction.NORTH));
        m.put(new BlockPos(X0 + 4, Y0 + 2, Z0 + 4), stairs(Direction.WEST));
        return m;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("diff")) return;
        List<String> failures = new ArrayList<>();
        Map<BlockPos, BlockState> changes = changes();
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 8, Z0 - 8, X0 + 12, Z0 + 16, 80);
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("time set noon");
            tp(context, sp, X0 + 2.5, Y0, Z0 + 10.5, 180, 15);
            // version 2 first (saved twice: origin at its corner, and origin 2 blocks west of it), then version 1
            build(sp, changes, true);
            Path dir = context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());
            capture(context, sp, MIN, "ale_diff_v2");
            capture(context, sp, MIN.offset(-2, 0, 0), "ale_diff_v2_moved");
            build(sp, changes, false);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_diff_v1");
            GT.waitSchematicBlock(context, new BlockPos(X0 + 2, Y0 + 3, Z0 + 2), Blocks.GLASS);
            context.runOnClient(c -> {
                AleToolsConfigs.DIFF.setBooleanValue(true);
                AleToolsConfigs.DIFF_SHOW.setBooleanValue(true);
            });

            // counts, both ways
            DiffSession s = start(context, placement, dir.resolve("ale_diff_v2.litematic"));
            counts(context, s, "placement = old", 2, 3, 4, failures);
            context.runOnClient(c -> s.setSwapped(true));
            counts(context, s, "swapped", 3, 2, 4, failures);
            context.runOnClient(c -> s.setSwapped(false));
            // the boxes are on exactly the 9 world positions, with the right kinds
            for (int i = 0; i < 40 && boxes(context) < 9; i++) context.waitTick();
            Set<BlockPos> drawn = new HashSet<>();
            Map<BlockPos, Integer> kinds = new HashMap<>();
            context.runOnClient(c -> {
                DiffSession.RenderList list = DiffSession.renderList();
                for (int i = 0; list != null && i < list.count(); i++) {
                    drawn.add(BlockPos.of(list.positions()[i]));
                    kinds.put(BlockPos.of(list.positions()[i]), list.kinds()[i]);
                }
            });
            boolean placesOk = drawn.equals(changes.keySet());
            boolean kindsOk = placesOk && kinds.get(new BlockPos(X0, Y0 + 1, Z0)) == DiffSession.REMOVED
                    && kinds.get(new BlockPos(X0 + 2, Y0 + 1, Z0 + 2)) == DiffSession.ADDED
                    && kinds.get(new BlockPos(X0 + 2, Y0 + 2, Z0)) == DiffSession.CHANGED;
            GT.log("[diff] boxes: " + drawn.size() + " positions " + (placesOk && kindsOk ? "ok" : "WRONG " + kinds));
            if (!placesOk || !kindsOk) failures.add("box positions");
            long frames = ToolBoxes.diffFrames;
            context.waitTicks(5);
            if (ToolBoxes.diffFrames <= frames || ToolBoxes.lastDiffBoxes != 9) failures.add("boxes not drawn (" + ToolBoxes.lastDiffBoxes + ")");
            String summary = ToolsHud.lastDiffSummary;
            GT.log("[diff] HUD summary: " + summary);
            if (!summary.contains("+2") || !summary.contains("-3") || !summary.contains("~4")) failures.add("summary");
            GT.shot(context, "ale-diff-world");
            // hide / show
            context.runOnClient(c -> AleToolsConfigs.DIFF_SHOW.setBooleanValue(false));
            context.waitTicks(5);
            boolean hidden = ToolBoxes.lastDiffBoxes == 0;
            context.runOnClient(c -> AleToolsConfigs.DIFF_SHOW.setBooleanValue(true));
            context.waitTicks(5);
            GT.log("[diff] hidden: " + (hidden ? "no boxes ok" : "STILL DRAWN") + ", shown again: " + ToolBoxes.lastDiffBoxes);
            if (!hidden || ToolBoxes.lastDiffBoxes != 9) failures.add("hide/show");

            // the copy saved with another origin: lines up with region alignment, or with a manual offset
            DiffSession moved = start(context, placement, dir.resolve("ale_diff_v2_moved.litematic"));
            int originTotal = total(context, moved);
            GT.log("[diff] moved origin, aligned by origin: " + originTotal + " differences");
            if (originTotal <= 9) failures.add("origin alignment should not line up");
            context.runOnClient(c -> moved.setAlignment(SchematicDiff.Alignment.REGION));
            counts(context, moved, "aligned by region", 2, 3, 4, failures);
            context.runOnClient(c -> {
                moved.setAlignment(SchematicDiff.Alignment.ORIGIN);
                moved.setOffset(new Vec3i(-2, 0, 0));
            });
            counts(context, moved, "origin + manual offset -2 0 0", 2, 3, 4, failures);

            // through the screens: placement settings -> Compare with... -> picker -> list
            context.runOnClient(c -> {
                DiffSession.stop();
                c.setScreen(new GuiPlacementConfiguration(placement));
            });
            context.waitTicks(5);
            GT.clickButton(context, buttonLabel(context, "autyism-le.gui.button.diff.compare_with"));
            context.waitTicks(10);
            boolean picker = context.computeOnClient(c -> c.screen instanceof DiffPickScreen);
            GT.log("[diff] Compare with... opens the picker: " + (picker ? "ok" : "NO (" + context.computeOnClient(c -> String.valueOf(c.screen)) + ")"));
            if (!picker) throw new AssertionError("[diff] the picker did not open");
            GT.shot(context, "ale-diff-pick");
            context.runOnClient(c -> ((DiffPickScreen) c.screen).compareWith(dir.resolve("ale_diff_v2.litematic")));
            int[] rows = null;
            for (int i = 0; i < 200 && rows == null; i++) {
                context.waitTick();
                rows = context.computeOnClient(c -> c.screen instanceof DiffListScreen l ? l.rowsForTest() : null);
            }
            GT.log("[diff] list: " + (rows == null ? "none" : rows.length + " rows"));
            if (rows == null || rows.length != 9) failures.add("list rows");
            GT.shot(context, "ale-diff-list");
            // nearest first: the first row is the entry nearest to the player
            BlockPos first = rows == null ? null : context.computeOnClient(c -> {
                DiffSession cur = DiffSession.current();
                int[] r = ((DiffListScreen) c.screen).rowsForTest();
                return cur.worldPos(cur.result().positions()[r[0]]);
            });
            BlockPos nearest = context.computeOnClient(c -> {
                var eye = c.player.getEyePosition();
                BlockPos best = null;
                for (BlockPos p : changes.keySet()) {
                    if (best == null || eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(p)) < eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(best))) best = p;
                }
                return best;
            });
            if (first == null || !first.equals(nearest)) failures.add("list order (first " + first + ", nearest " + nearest + ")");
            // click the first row: the guide points there and the screen closes
            double[] at = context.computeOnClient(c -> {
                double scale = c.getWindow().getGuiScale();
                return new double[]{150 * scale, (92 + 4 + 5) * scale};
            });
            context.getInput().setCursorPos(at[0], at[1]);
            context.waitTick();
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(3);
            BlockPos guided = context.computeOnClient(c -> c.screen == null && WrongBlockGuide.target() != null && WrongBlockGuide.target().fromDiff()
                    ? WrongBlockGuide.target().pos() : null);
            GT.log("[diff] clicked the first row: guide at " + guided + (first != null && first.equals(guided) ? " ok" : " WRONG"));
            if (guided == null || !guided.equals(first)) failures.add("row click");
            context.waitTicks(10);
            GT.shot(context, "ale-diff-guide");
            // place the new version's block there: the guide goes away
            if (guided != null) {
                BlockState want = changes.get(guided);
                sp.getServer().runOnServer(srv -> ViewShots.set(srv.overworld(), guided, want));
                context.waitTicks(10);
                boolean gone = context.computeOnClient(c -> WrongBlockGuide.target() == null);
                GT.log("[diff] placed the new block: guide " + (gone ? "gone ok" : "STILL THERE"));
                if (!gone) failures.add("guide after placing");
            }
            if (!failures.isEmpty()) throw new AssertionError("[diff] wrong: " + failures);
            GT.log("[diff] OK: exact counts both ways, boxes on the right blocks, hide/show, region alignment and manual offset, screens, list order, row click guides");
        } finally {
            context.runOnClient(c -> {
                DiffSession.stop();
                WrongBlockGuide.clear(false);
                c.setScreen(null);
            });
            GT.removeAllPlacements(context);
        }
    }

    private static String buttonLabel(ClientGameTestContext context, String key) {
        return context.computeOnClient(c -> fi.dy.masa.malilib.util.StringUtils.translate(key));
    }

    private static void tp(ClientGameTestContext context, TestSingleplayerContext sp, double x, int y, double z, float yaw, float pitch) {
        sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f %.1f %.1f", x, y, z, yaw, pitch));
        context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - x) < 0.01 && Math.abs(c.player.getZ() - z) < 0.01, 200);
    }

    /** Version 1 in the world, or version 2 (with the changes) */
    private static void build(TestSingleplayerContext sp, Map<BlockPos, BlockState> changes, boolean v2) {
        sp.getServer().runOnServer(s -> {
            ServerLevel level = s.overworld();
            for (BlockPos p : BlockPos.betweenClosed(MIN, MAX)) {
                BlockPos q = p.immutable();
                ViewShots.set(level, q, v2 && changes.containsKey(q) ? changes.get(q) : v1(q));
            }
        });
    }

    /** Save MIN..MAX as a schematic file whose origin is at origin (only the file, no placement) */
    private static void capture(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos origin, String name) {
        Path dir = context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());
        boolean ok = sp.getServer().computeOnServer(server -> {
            var area = new fi.dy.masa.litematica.selection.AreaSelection();
            area.setName(name);
            area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(MIN, MAX, name), false);
            area.setExplicitOrigin(origin);
            var schematic = fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromWorld(server.overworld(), area,
                    new fi.dy.masa.litematica.schematic.LitematicaSchematic.SchematicSaveInfo(false, false), "ALE", m -> GT.log("capture: " + m));
            return schematic != null && schematic.writeToFile(dir, name, true);
        });
        if (!ok) throw new AssertionError("could not save " + name);
    }

    private static DiffSession start(ClientGameTestContext context, SchematicPlacement placement, Path file) {
        DiffSession s = context.computeOnClient(c -> DiffSession.start(placement, file));
        for (int i = 0; i < 400 && context.computeOnClient(c -> s.result() == null && s.error() == null); i++) context.waitTick();
        if (s.error() != null) throw new AssertionError("[diff] " + s.error());
        return s;
    }

    private static int total(ClientGameTestContext context, DiffSession s) {
        for (int i = 0; i < 400 && context.computeOnClient(c -> s.result() == null); i++) context.waitTick();
        return s.result() == null ? -1 : s.result().total();
    }

    private static void counts(ClientGameTestContext context, DiffSession s, String step, int added, int removed, int changed, List<String> failures) {
        for (int i = 0; i < 400 && context.computeOnClient(c -> s.result() == null); i++) context.waitTick();
        int a = s.count(DiffSession.ADDED), r = s.count(DiffSession.REMOVED), ch = s.count(DiffSession.CHANGED);
        boolean ok = a == added && r == removed && ch == changed;
        GT.log(String.format(Locale.ROOT, "[diff] %s: +%d -%d ~%d %s", step, a, r, ch, ok ? "ok" : "WRONG (want +" + added + " -" + removed + " ~" + changed + ")"));
        if (!ok) failures.add(step);
    }

    private static int boxes(ClientGameTestContext context) {
        return context.computeOnClient(c -> DiffSession.renderList() == null ? 0 : DiffSession.renderList().count());
    }
}
