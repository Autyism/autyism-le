package com.autyism.ale.locate;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.config.AleToolsConfigs.LocateMode;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.InfoUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The wrong block guide: a hotkey picks the nearest schematic position that is wrong (wrong block, wrong state, extra
 * block) or missing, and an arrow on the screen plus a box in the world (seen through walls) lead the player there.
 * <ul>
 * <li>Pressing again goes to the next one: the nearest from where the player is now that was not shown in this round.
 * When every one around was shown, the round starts over (the one shown now is still skipped unless it is the only one).</li>
 * <li>"Previous" goes back to the ones shown before (those fixed in the meantime are skipped), "clear" drops the guide.</li>
 * <li>The guide goes away by itself as soon as the block is right (or no longer what the mode looks for).</li>
 * <li>The diff list can point the guide at one of its entries ({@link #pointAt}); it goes away when the world has the
 * block of that version.</li>
 * </ul>
 * The positions come from {@link LocateIndex}; a key press searches synchronously for at most {@link #KEY_BUDGET_NS}
 * and keeps searching in the following ticks when the area around the player has not been scanned yet.
 */
public final class WrongBlockGuide {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final long KEY_BUDGET_NS = 8_000_000L;
    /** After the last use the index stays warm for 5 minutes, then it is dropped */
    private static final long KEEP_WARM_TICKS = 20L * 60 * 5;
    private static final int HISTORY = 64;

    /** Where the guide points; {@code expected} is set when the diff list pointed it there */
    public record Target(BlockPos pos, int kind, @Nullable BlockState expected, @Nullable BlockState other) {
        public boolean fromDiff() {
            return this.expected != null;
        }
    }

    @Nullable
    private static volatile Target target;
    private static boolean pending;
    private static final LongOpenHashSet SHOWN = new LongOpenHashSet();
    private static final ArrayDeque<BlockPos> HISTORY_STACK = new ArrayDeque<>();
    private static long ticks;
    private static long lastUse = Long.MIN_VALUE / 2;
    private static Object worldRef;

    private WrongBlockGuide() {
    }

    @Nullable
    public static Target target() {
        return target;
    }

    /** Still looking (the area near the player is not scanned yet) */
    public static boolean searching() {
        return pending;
    }

    // ---------------- hotkeys ----------------

    public static void next() {
        if (!begin()) return;
        Target t = target;
        if (t != null && !t.fromDiff()) {
            SHOWN.add(t.pos().asLong());
            pushHistory(t.pos());
        }
        pending = true;
        search(KEY_BUDGET_NS);
    }

    public static void previous() {
        if (!begin()) return;
        while (!HISTORY_STACK.isEmpty()) {
            BlockPos pos = HISTORY_STACK.pop();
            int kind = liveKind(pos);
            if (kind != 0 && accepted(kind)) {
                pending = false;
                target = new Target(pos, kind, null, null);
                return;
            }
        }
        InfoUtils.printActionbarMessage("autyism-le.message.locate.no_previous");
    }

    public static void clear(boolean byUser) {
        target = null;
        pending = false;
        SHOWN.clear();
        HISTORY_STACK.clear();
        if (byUser) InfoUtils.printActionbarMessage("autyism-le.message.locate.cleared");
    }

    /** Point the guide at a position from the diff list; it goes away when the world has {@code expected} there */
    public static void pointAt(BlockPos pos, BlockState expected, BlockState other, int kind) {
        lastUse = ticks;
        pending = false;
        target = new Target(pos.immutable(), kind, expected, other);
    }

    private static boolean begin() {
        if (!AleToolsConfigs.LOCATE.getBooleanValue()) {
            InfoUtils.printActionbarMessage("autyism-le.message.locate.disabled");
            return false;
        }
        if (mc.level == null || mc.player == null) return false;
        lastUse = ticks;
        LocateIndex.active = true;
        LocateIndex.refreshPlacements();
        if (LocateIndex.boxes().isEmpty()) {
            InfoUtils.printActionbarMessage("autyism-le.message.locate.no_placement");
            return false;
        }
        return true;
    }

    private static void pushHistory(BlockPos pos) {
        if (!HISTORY_STACK.isEmpty() && HISTORY_STACK.peek().equals(pos)) return;
        HISTORY_STACK.push(pos);
        while (HISTORY_STACK.size() > HISTORY) HISTORY_STACK.removeLast();
    }

    // ---------------- search ----------------

    /** Scan and search for up to budgetNs; picks the target once the answer is certain */
    private static void search(long budgetNs) {
        long deadline = System.nanoTime() + budgetNs;
        Vec3 eye = eye();
        BlockPos current = target != null && !target.fromDiff() ? target.pos() : null;
        while (true) {
            LocateIndex.Found f = LocateIndex.nearest(eye.x, eye.y, eye.z, filter(current, true));
            if (f.exact()) {
                finish(f, eye, current);
                return;
            }
            // something closer is not scanned yet (for example a chunk that loaded after the scan went by)
            if (LocateIndex.sweepDone()) LocateIndex.restartSweep();
            long left = deadline - System.nanoTime();
            if (left <= 0) return;
            long before = LocateIndex.scannedSections;
            LocateIndex.tick(left);
            if (LocateIndex.scannedSections == before) return; // out of time: go on next tick
        }
    }

    private static void finish(LocateIndex.Found f, Vec3 eye, @Nullable BlockPos current) {
        pending = false;
        if (f.pos() == null && !SHOWN.isEmpty()) {
            // every one around was shown: start the round over, still skipping the one shown now
            SHOWN.clear();
            f = LocateIndex.nearest(eye.x, eye.y, eye.z, filter(current, true));
        }
        if (f.pos() == null && current != null && liveKind(current) != 0 && accepted(liveKind(current))) {
            InfoUtils.printActionbarMessage("autyism-le.message.locate.only_one");
            return; // keep the only one
        }
        if (f.pos() == null) {
            target = null;
            InfoUtils.printActionbarMessage("autyism-le.message.locate.none");
            return;
        }
        int kind = liveKind(f.pos());
        target = new Target(f.pos(), kind != 0 ? kind : f.kind(), null, null);
    }

    private static LocateIndex.Filter filter(@Nullable BlockPos current, boolean skipShown) {
        LocateMode mode = AleToolsConfigs.locateMode();
        var range = DataManager.getRenderLayerRange();
        Set<Pair<BlockState, BlockState>> ignored = ignoredPairs();
        boolean ignoreFluids = fi.dy.masa.litematica.config.Configs.Visuals.IGNORE_EXISTING_FLUIDS.getBooleanValue();
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        long cur = current != null ? current.asLong() : Long.MIN_VALUE;
        return (x, y, z, cached) -> {
            if (!range.isPositionWithinRange(x, y, z)) return false;
            long key = BlockPos.asLong(x, y, z);
            if (key == cur || (skipShown && SHOWN.contains(key))) return false;
            m.set(x, y, z);
            BlockState want = ws.getBlockState(m), have = mc.level.getBlockState(m);
            int kind = LocateIndex.classify(want, have);
            if (kind != cached) LocateIndex.markBlockChanged(m); // the cache is behind: scan this section again
            if (kind == 0 || !accepted(kind)) return false;
            if (kind == LocateIndex.EXTRA && ignoreFluids && have.liquid()) return false;
            return ignored.isEmpty() || !ignored.contains(Pair.of(want, have));
        };
    }

    /** The state pairs the player told Litematica's verifiers to ignore */
    private static Set<Pair<BlockState, BlockState>> ignoredPairs() {
        Set<Pair<BlockState, BlockState>> all = null;
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (!p.hasVerifier()) continue;
            Set<Pair<BlockState, BlockState>> s = p.getSchematicVerifier().getIgnoredMismatches();
            if (s == null || s.isEmpty()) continue;
            if (all == null) all = new java.util.HashSet<>();
            all.addAll(s);
        }
        return all == null ? Set.of() : all;
    }

    static boolean accepted(int kind) {
        LocateMode mode = AleToolsConfigs.locateMode();
        return kind == LocateIndex.MISSING ? mode.wantsMissing() : mode.wantsWrong();
    }

    /** What is wrong at pos right now (0 = correct) */
    static int liveKind(BlockPos pos) {
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        if (ws == null || mc.level == null) return 0;
        return LocateIndex.classify(ws.getBlockState(pos), mc.level.getBlockState(pos));
    }

    static Vec3 eye() {
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        return camera.getEyePosition();
    }

    // ---------------- tick ----------------

    public static void tick(Minecraft c) {
        ticks++;
        boolean enabled = AleToolsConfigs.LOCATE.getBooleanValue();
        if (c.level != worldRef) {
            worldRef = c.level;
            clear(false);
        }
        boolean inUse = enabled && c.level != null && c.player != null
                && (target != null || pending || ticks - lastUse < KEEP_WARM_TICKS);
        if (!inUse) {
            if (LocateIndex.active) {
                LocateIndex.active = false;
                LocateIndex.reset();
            }
            if (!enabled) clear(false);
            return;
        }
        LocateIndex.active = true;
        LocateIndex.refreshPlacements();
        if (pending) search(LocateIndex.TICK_BUDGET_NS);
        else LocateIndex.tick(LocateIndex.TICK_BUDGET_NS);
        validate();
    }

    private static void validate() {
        Target t = target;
        if (t == null) return;
        if (!mc.level.hasChunk(t.pos().getX() >> 4, t.pos().getZ() >> 4)) return; // can't tell from here
        if (t.fromDiff()) {
            if (mc.level.getBlockState(t.pos()) == t.expected()) {
                target = null;
                InfoUtils.printActionbarMessage("autyism-le.message.locate.done");
            }
            return;
        }
        if (!insideBoxes(t.pos())) {
            target = null;
            return;
        }
        int kind = liveKind(t.pos());
        if (kind == 0 || !accepted(kind)) {
            target = null;
            InfoUtils.printActionbarMessage("autyism-le.message.locate.done");
        } else if (kind != t.kind()) {
            target = new Target(t.pos(), kind, null, null);
        }
    }

    private static boolean insideBoxes(BlockPos p) {
        for (int[] b : LocateIndex.boxes()) {
            if (p.getX() >= b[0] && p.getX() <= b[3] && p.getY() >= b[1] && p.getY() <= b[4] && p.getZ() >= b[2] && p.getZ() <= b[5]) return true;
        }
        return false;
    }

    // ---------------- text for the HUD ----------------

    /** What the schematic wants at the target */
    public static BlockState expectedState(Target t) {
        if (t.fromDiff()) return t.expected();
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        return ws != null ? ws.getBlockState(t.pos()) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    public static BlockState foundState(Target t) {
        return mc.level != null ? mc.level.getBlockState(t.pos()) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
    }

    /** The properties that differ between two states of the same block, "name=value" */
    public static List<String> differingProperties(BlockState a, BlockState b) {
        return differingProperties(a, b, " / ");
    }

    /** The same with another separator between the two values ("facing=south -> north") */
    public static List<String> differingProperties(BlockState a, BlockState b, String separator) {
        List<String> out = new ArrayList<>();
        if (a.getBlock() != b.getBlock()) return out;
        for (var prop : a.getProperties()) {
            Object va = a.getValue(prop), vb = b.getValue(prop);
            if (!va.equals(vb)) out.add(prop.getName() + "=" + valueName(prop, va) + separator + valueName(prop, vb));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(net.minecraft.world.level.block.state.properties.Property prop, Object value) {
        return prop.getName((Comparable) value);
    }
}
