package com.autyism.ale.locate;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the world differs from the schematic, cached per chunk section (16x16x16) and filled in a little at a time.
 * <ul>
 * <li>Only sections inside an enabled, rendered placement and inside chunks that both the client world and the schematic
 * world have loaded are looked at; the work goes outwards from the player in rings of chunks.</li>
 * <li>Each client tick scans for at most {@link #TICK_BUDGET_NS}; a key press may add a few milliseconds more for the
 * sections closest to the player. A million block schematic is gone through in a few seconds without a frame drop.</li>
 * <li>A block change in the client world ({@code Level.setBlock}), a schematic chunk being (re)built
 * ({@code WorldSchematic.scheduleChunkRenders}) and a chunk (un)loading drop the cached result of that section or chunk;
 * it is scanned again when the scan comes by. Moving, rotating, enabling or removing a placement starts over.</li>
 * <li>The layer range, the guide's mode and Litematica's ignore lists are applied when a target is picked, not when
 * scanning, so changing them needs no new scan.</li>
 * </ul>
 * Everything here runs on the client thread, except the dirty marks (Litematica may rebuild schematic chunks off thread).
 */
public final class LocateIndex {
    public static final int MISSING = 1, WRONG_BLOCK = 2, WRONG_STATE = 3, EXTRA = 4;
    /** Scan time per client tick */
    static final long TICK_BUDGET_NS = 2_000_000L;
    private static final int[] CLEAN = new int[0];
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final Minecraft mc = Minecraft.getInstance();

    /** Something uses the index: block changes are recorded only then (read by the mixins) */
    public static volatile boolean active;
    /** Section keys (SectionPos.asLong) whose cached result is out of date */
    private static final Set<Long> DIRTY_SECTIONS = ConcurrentHashMap.newKeySet();
    /** Chunk columns (ChunkPos.asLong) whose cached results are out of date */
    private static final Set<Long> DIRTY_COLUMNS = ConcurrentHashMap.newKeySet();

    /** Scanned sections: entries are (local index | kind << 12); CLEAN when nothing differs */
    private static final Long2ObjectOpenHashMap<int[]> RESULTS = new Long2ObjectOpenHashMap<>();
    /** Placement boxes {minX, minY, minZ, maxX, maxY, maxZ} */
    private static List<int[]> boxes = List.of();
    private static int minY, maxY;
    private static String signature = "";
    private static Object worldRef;

    // ring scan cursor
    private static int cursorRing, cursorIndex;
    private static long cursorCenter = Long.MIN_VALUE;
    private static boolean sweepDone;
    /** Sections scanned since the index was (re)started (for tests and the HUD) */
    public static long scannedSections;
    /** Longest scan time spent in one tick so far (ns), for the tests */
    public static long longestTickNs;

    private LocateIndex() {
    }

    // ---------------- change marks (any thread) ----------------

    public static void markBlockChanged(BlockPos pos) {
        DIRTY_SECTIONS.add(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
    }

    public static void markColumnChanged(int chunkX, int chunkZ) {
        DIRTY_COLUMNS.add(ChunkPos.asLong(chunkX, chunkZ));
    }

    // ---------------- state ----------------

    public static void reset() {
        RESULTS.clear();
        DIRTY_SECTIONS.clear();
        DIRTY_COLUMNS.clear();
        cursorRing = 0;
        cursorIndex = 0;
        sweepDone = false;
        scannedSections = 0;
    }

    /** All of the loaded placement area has been scanned and nothing is waiting to be scanned again */
    public static boolean complete() {
        return sweepDone && DIRTY_SECTIONS.isEmpty() && DIRTY_COLUMNS.isEmpty();
    }

    public static List<int[]> boxes() {
        return boxes;
    }

    /** Placements changed (moved, rotated, enabled, added, removed...) or another world: start over */
    static void refreshPlacements() {
        Object world = mc.level;
        StringBuilder sig = new StringBuilder();
        List<int[]> list = new ArrayList<>();
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (SchematicPlacement p : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (!p.isEnabled() || !p.isRenderingEnabled()) continue;
            sig.append(System.identityHashCode(p)).append('@').append(p.getOrigin()).append(p.getRotation()).append(p.getMirror()).append(';');
            for (Box box : p.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.RENDERING_ENABLED).values()) {
                BlockPos a = box.getPos1(), b = box.getPos2();
                if (a == null || b == null) continue;
                int[] r = {Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
                        Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())};
                list.add(r);
                lo = Math.min(lo, r[1]);
                hi = Math.max(hi, r[4]);
                sig.append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append(',').append(r[3]).append(',').append(r[4]).append(',').append(r[5]).append(';');
            }
        }
        String s = sig.toString();
        if (!s.equals(signature) || world != worldRef) {
            signature = s;
            worldRef = world;
            boxes = List.copyOf(list);
            minY = lo;
            maxY = hi;
            reset();
        }
    }

    // ---------------- scanning ----------------

    /** One client tick of scanning (nearest sections first) */
    static void tick(long budgetNs) {
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        if (ws == null || mc.level == null || mc.player == null || boxes.isEmpty()) return;
        applyDirty();
        long start = System.nanoTime();
        long deadline = start + budgetNs;
        // around the camera, like the search (they differ with a free camera)
        net.minecraft.world.entity.Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        int pcx = camera.getBlockX() >> 4, pcz = camera.getBlockZ() >> 4;
        long center = ChunkPos.asLong(pcx, pcz);
        if (center != cursorCenter) {
            cursorCenter = center;
            cursorRing = 0;
            cursorIndex = 0;
            sweepDone = false;
        }
        if (sweepDone) return;
        int radius = radius();
        int steps = 0;
        while (cursorRing <= radius) {
            int ringSize = cursorRing == 0 ? 1 : 8 * cursorRing;
            while (cursorIndex < ringSize) {
                long col = ringColumn(pcx, pcz, cursorRing, cursorIndex);
                int cx = ChunkPos.getX(col), cz = ChunkPos.getZ(col);
                if (!scanColumn(ws, cx, cz, deadline)) {
                    noteTime(start);
                    return; // out of time in the middle of this column: carry on here next tick
                }
                cursorIndex++;
                if ((++steps & 15) == 0 && System.nanoTime() > deadline) {
                    noteTime(start);
                    return;
                }
            }
            cursorRing++;
            cursorIndex = 0;
        }
        sweepDone = true;
        noteTime(start);
    }

    private static void noteTime(long start) {
        longestTickNs = Math.max(longestTickNs, System.nanoTime() - start);
    }

    /** Scan the unscanned sections of a column; false when the time ran out before all were done */
    private static boolean scanColumn(WorldSchematic ws, int cx, int cz, long deadline) {
        int x0 = cx << 4, z0 = cz << 4;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int[] b : boxes) {
            if (b[3] < x0 || b[0] > x0 + 15 || b[5] < z0 || b[2] > z0 + 15) continue;
            lo = Math.min(lo, b[1]);
            hi = Math.max(hi, b[4]);
        }
        if (lo > hi) return true;
        if (!mc.level.hasChunk(cx, cz) || !ws.hasChunk(cx, cz)) return true;
        ChunkAccess world = mc.level.getChunk(cx, cz);
        ChunkAccess schematic = ws.getChunk(cx, cz);
        for (int sy = lo >> 4; sy <= hi >> 4; sy++) {
            long key = SectionPos.asLong(cx, sy, cz);
            if (RESULTS.containsKey(key)) continue;
            if (System.nanoTime() > deadline) return false;
            RESULTS.put(key, scanSection(world, schematic, cx, sy, cz));
            scannedSections++;
        }
        return true;
    }

    private static int[] scanSection(ChunkAccess world, ChunkAccess schematic, int cx, int sy, int cz) {
        LevelChunkSection w = section(world, sy), s = section(schematic, sy);
        boolean wAir = w == null || w.hasOnlyAir(), sAir = s == null || s.hasOnlyAir();
        if (wAir && sAir) return CLEAN;
        int x0 = cx << 4, y0 = sy << 4, z0 = cz << 4;
        IntArrayList out = null;
        long[] seen = null;
        int overlapping = 0;
        for (int[] b : boxes) {
            if (b[3] < x0 || b[0] > x0 + 15 || b[4] < y0 || b[1] > y0 + 15 || b[5] < z0 || b[2] > z0 + 15) continue;
            if (++overlapping == 2) seen = new long[64];
        }
        for (int[] b : boxes) {
            if (b[3] < x0 || b[0] > x0 + 15 || b[4] < y0 || b[1] > y0 + 15 || b[5] < z0 || b[2] > z0 + 15) continue;
            int ax = Math.max(b[0], x0) & 15, bx = Math.min(b[3], x0 + 15) & 15;
            int ay = Math.max(b[1], y0) & 15, by = Math.min(b[4], y0 + 15) & 15;
            int az = Math.max(b[2], z0) & 15, bz = Math.min(b[5], z0 + 15) & 15;
            for (int y = ay; y <= by; y++) {
                for (int z = az; z <= bz; z++) {
                    for (int x = ax; x <= bx; x++) {
                        int idx = (y << 8) | (z << 4) | x;
                        if (seen != null) {
                            if ((seen[idx >> 6] & (1L << idx)) != 0) continue;
                            seen[idx >> 6] |= 1L << idx;
                        }
                        BlockState want = sAir ? AIR : s.getBlockState(x, y, z);
                        BlockState have = wAir ? AIR : w.getBlockState(x, y, z);
                        int kind = classify(want, have);
                        if (kind != 0) {
                            if (out == null) out = new IntArrayList();
                            out.add(idx | (kind << 12));
                        }
                    }
                }
            }
        }
        return out == null ? CLEAN : out.toIntArray();
    }

    @Nullable
    private static LevelChunkSection section(ChunkAccess chunk, int sy) {
        int i = chunk.getSectionIndexFromSectionY(sy);
        LevelChunkSection[] sections = chunk.getSections();
        return i >= 0 && i < sections.length ? sections[i] : null;
    }

    /** Same rules as Litematica's verifier: 0 = correct */
    public static int classify(BlockState want, BlockState have) {
        if (want == have) return 0;
        boolean wantAir = want.isAir(), haveAir = have.isAir();
        if (wantAir && haveAir) return 0;
        if (!wantAir) {
            if (haveAir) return MISSING;
            return want.getBlock() != have.getBlock() ? WRONG_BLOCK : WRONG_STATE;
        }
        return EXTRA;
    }

    /** What the cache says about one position: 0 = correct or not scanned, -1 = not scanned */
    static int cachedKind(BlockPos pos) {
        int[] e = RESULTS.get(SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
        if (e == null) return -1;
        int idx = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
        for (int v : e) {
            if ((v & 0xFFF) == idx) return v >>> 12;
        }
        return 0;
    }

    private static void applyDirty() {
        boolean any = false;
        if (!DIRTY_SECTIONS.isEmpty()) {
            for (Long key : DIRTY_SECTIONS.toArray(new Long[0])) {
                DIRTY_SECTIONS.remove(key);
                RESULTS.remove(key.longValue());
                any = true;
            }
        }
        if (!DIRTY_COLUMNS.isEmpty()) {
            for (Long col : DIRTY_COLUMNS.toArray(new Long[0])) {
                DIRTY_COLUMNS.remove(col);
                int cx = ChunkPos.getX(col), cz = ChunkPos.getZ(col);
                for (int sy = (minY >> 4); sy <= (maxY >> 4) && minY <= maxY; sy++) RESULTS.remove(SectionPos.asLong(cx, sy, cz));
                any = true;
            }
        }
        // (a chunk that just loaded has nothing cached yet, so this goes for any mark, not only for removed results)
        if (any) restartSweep();
    }

    /** Go round from the middle again (changed sections are mostly near the player; scanned ones are skipped quickly) */
    static void restartSweep() {
        sweepDone = false;
        cursorRing = 0;
        cursorIndex = 0;
    }

    static boolean sweepDone() {
        return sweepDone;
    }

    static int radius() {
        return Math.max(2, mc.options.getEffectiveRenderDistance()) + 1;
    }

    /** The i-th chunk column of the square ring r around (cx, cz) */
    static long ringColumn(int cx, int cz, int r, int i) {
        if (r == 0) return ChunkPos.asLong(cx, cz);
        int side = 2 * r;
        int edge = i / side, off = i % side;
        return switch (edge) {
            case 0 -> ChunkPos.asLong(cx - r + off, cz - r);
            case 1 -> ChunkPos.asLong(cx + r, cz - r + off);
            case 2 -> ChunkPos.asLong(cx + r - off, cz + r);
            default -> ChunkPos.asLong(cx - r, cz + r - off);
        };
    }

    // ---------------- nearest search ----------------

    /** One candidate the search may take */
    interface Filter {
        /** The candidate at (x, y, z) of this cached kind; return false to skip it */
        boolean accept(int x, int y, int z, int kind);
    }

    /** Result of a nearest search */
    record Found(@Nullable BlockPos pos, int kind, boolean exact) {
    }

    /**
     * The nearest cached difference that the filter takes. {@code exact} is false when a section that is closer than
     * the result (or any section, when nothing was found) has not been scanned yet.
     */
    static Found nearest(double px, double py, double pz, Filter filter) {
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        if (ws == null || mc.level == null || boxes.isEmpty()) return new Found(null, 0, true);
        int pcx = ((int) Math.floor(px)) >> 4, pcz = ((int) Math.floor(pz)) >> 4;
        int radius = radius();
        double best = Double.MAX_VALUE;
        int bx = 0, by = 0, bz = 0, bkind = 0;
        boolean found = false, unscanned = false;
        for (int r = 0; r <= radius; r++) {
            double ringMin = Math.max(0, (r - 1) * 16.0);
            if (found && ringMin * ringMin >= best) break;
            int ringSize = r == 0 ? 1 : 8 * r;
            for (int i = 0; i < ringSize; i++) {
                long col = ringColumn(pcx, pcz, r, i);
                int cx = ChunkPos.getX(col), cz = ChunkPos.getZ(col);
                int x0 = cx << 4, z0 = cz << 4;
                double dx = axisDist(px, x0, x0 + 16), dz = axisDist(pz, z0, z0 + 16);
                double colMin = dx * dx + dz * dz;
                if (colMin >= best) continue;
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int[] b : boxes) {
                    if (b[3] < x0 || b[0] > x0 + 15 || b[5] < z0 || b[2] > z0 + 15) continue;
                    lo = Math.min(lo, b[1]);
                    hi = Math.max(hi, b[4]);
                }
                if (lo > hi || !mc.level.hasChunk(cx, cz) || !ws.hasChunk(cx, cz)) continue;
                for (int sy = lo >> 4; sy <= hi >> 4; sy++) {
                    double dy = axisDist(py, sy << 4, (sy << 4) + 16);
                    if (colMin + dy * dy >= best) continue;
                    int[] e = RESULTS.get(SectionPos.asLong(cx, sy, cz));
                    if (e == null) {
                        unscanned = true;
                        continue;
                    }
                    for (int v : e) {
                        int idx = v & 0xFFF;
                        int x = x0 + (idx & 15), y = (sy << 4) + (idx >> 8), z = z0 + ((idx >> 4) & 15);
                        double ex = x + 0.5 - px, ey = y + 0.5 - py, ez = z + 0.5 - pz;
                        double d = ex * ex + ey * ey + ez * ez;
                        if (d >= best) continue;
                        int kind = v >>> 12;
                        if (!filter.accept(x, y, z, kind)) continue;
                        best = d;
                        bx = x;
                        by = y;
                        bz = z;
                        bkind = kind;
                        found = true;
                    }
                }
            }
        }
        return new Found(found ? new BlockPos(bx, by, bz) : null, bkind, !unscanned);
    }

    private static double axisDist(double p, double lo, double hi) {
        return p < lo ? lo - p : (p > hi ? p - hi : 0);
    }

    /** Number of cached differences (for the tests) */
    public static int cachedCount() {
        int n = 0;
        for (int[] e : RESULTS.values()) n += e.length;
        return n;
    }
}
