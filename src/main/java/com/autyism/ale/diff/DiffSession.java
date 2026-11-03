package com.autyism.ale.diff;

import com.autyism.ale.AleMod;
import com.autyism.ale.config.AleToolsConfigs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The schematic diff that is shown: one placement ("A", by default the old version) compared with another .litematic
 * file ("B", by default the new version). Loading and comparing run on a background thread; so does choosing which
 * boxes to draw (the nearest {@code schematicDiffMaxBoxes}) whenever the player or the placement moved.
 * <p>
 * Positions are kept in A's schematic coordinates and turned into world positions with the placement's current origin,
 * rotation and mirror, so the diff follows the placement when it is moved. (Sub-regions moved on their own inside the
 * placement are not followed.)
 */
public final class DiffSession {
    /** Kinds as shown (after "swap"): what the newer version added, removed, changed */
    public static final int ADDED = 0, REMOVED = 1, CHANGED = 2;
    private static final Minecraft mc = Minecraft.getInstance();
    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ALE schematic diff " + THREADS.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    /** Boxes to draw this frame: world positions and kinds (ADDED / REMOVED / CHANGED) */
    public record RenderList(long[] positions, int[] kinds, int count) {
    }

    @Nullable
    private static DiffSession current;
    @Nullable
    private static volatile RenderList renderList;

    final SchematicPlacement placement;
    final Path otherFile;
    @Nullable
    volatile LitematicaSchematic other;
    SchematicDiff.Alignment alignment = SchematicDiff.Alignment.ORIGIN;
    Vec3i offset = Vec3i.ZERO;
    /** false: the placement is the old version, the file the new one */
    boolean swapped;
    @Nullable
    volatile SchematicDiff.Result result;
    @Nullable
    volatile String error;
    private volatile int generation;
    @Nullable
    private Future<?> computing;
    private volatile boolean renderBusy;
    private long renderKey = Long.MIN_VALUE;
    private int renderGeneration = -1;
    private long ticks;

    private DiffSession(SchematicPlacement placement, Path otherFile) {
        this.placement = placement;
        this.otherFile = otherFile;
    }

    // ---------------- starting and stopping ----------------

    /** Compare the placement with a schematic file (replaces any diff shown before) */
    public static DiffSession start(SchematicPlacement placement, Path file) {
        stop();
        DiffSession s = new DiffSession(placement, file);
        current = s;
        s.recompute(true);
        return s;
    }

    public static void stop() {
        DiffSession s = current;
        current = null;
        renderList = null;
        if (s != null) {
            s.generation++;
            if (s.computing != null) s.computing.cancel(true);
        }
    }

    @Nullable
    public static DiffSession current() {
        return current;
    }

    @Nullable
    public static RenderList renderList() {
        return renderList;
    }

    public boolean computing() {
        return this.computing != null && !this.computing.isDone();
    }

    @Nullable
    public SchematicDiff.Result result() {
        return this.result;
    }

    @Nullable
    public String error() {
        return this.error;
    }

    public SchematicPlacement placement() {
        return this.placement;
    }

    public Path otherFile() {
        return this.otherFile;
    }

    public SchematicDiff.Alignment alignment() {
        return this.alignment;
    }

    public Vec3i offset() {
        return this.offset;
    }

    public boolean swapped() {
        return this.swapped;
    }

    public void setAlignment(SchematicDiff.Alignment a) {
        if (a == this.alignment) return;
        this.alignment = a;
        recompute(false);
    }

    public void setOffset(Vec3i o) {
        if (o.equals(this.offset)) return;
        this.offset = o;
        recompute(false);
    }

    public void setSwapped(boolean s) {
        this.swapped = s;
        this.renderKey = Long.MIN_VALUE; // kinds change: new boxes
    }

    /** Load (first time) and compare on the worker thread */
    private void recompute(boolean load) {
        int gen = ++this.generation;
        if (this.computing != null) this.computing.cancel(true);
        LitematicaSchematic a = this.placement.getSchematic();
        SchematicDiff.Alignment align = this.alignment;
        Vec3i off = this.offset;
        this.result = null;
        this.error = null;
        this.computing = WORKER.submit(() -> {
            try {
                LitematicaSchematic b = this.other;
                if (b == null || load) {
                    Path f = this.otherFile;
                    b = LitematicaSchematic.createFromFile(f.getParent(), f.getFileName().toString());
                    if (b == null) {
                        this.error = StringUtils.translate("autyism-le.message.diff.read_failed", f.getFileName().toString());
                        return;
                    }
                    this.other = b;
                }
                long t0 = System.nanoTime();
                SchematicDiff.Result r = SchematicDiff.compute(a, b, align, off, () -> this.generation != gen || Thread.currentThread().isInterrupted());
                if (r == null || this.generation != gen) return;
                this.result = r;
                AleMod.LOGGER.info("Schematic diff {} vs {}: {} only in A, {} only in B, {} changed ({} ms)", a.getMetadata().getName(),
                        this.otherFile.getFileName(), r.onlyA(), r.onlyB(), r.changed(), (System.nanoTime() - t0) / 1_000_000);
            } catch (Throwable t) {
                AleMod.LOGGER.error("Schematic diff failed", t);
                this.error = String.valueOf(t);
            }
        });
    }

    // ---------------- kinds and positions ----------------

    /** Kind as shown for an entry's stored kind */
    public int shownKind(int storedKind) {
        if (storedKind == SchematicDiff.CHANGED) return CHANGED;
        boolean onlyA = storedKind == SchematicDiff.ONLY_A;
        // not swapped: A is the old version, so "only in A" was removed
        return onlyA != this.swapped ? REMOVED : ADDED;
    }

    public int count(int shown) {
        SchematicDiff.Result r = this.result;
        if (r == null) return 0;
        return switch (shown) {
            case ADDED -> this.swapped ? r.onlyA() : r.onlyB();
            case REMOVED -> this.swapped ? r.onlyB() : r.onlyA();
            default -> r.changed();
        };
    }

    /** The newer version's state of an entry (what the world should get), rotated like the placement */
    public BlockState newState(int i) {
        SchematicDiff.Result r = this.result;
        BlockState s = this.swapped ? r.statesA()[i] : r.statesB()[i];
        return s.mirror(this.placement.getMirror()).rotate(this.placement.getRotation());
    }

    public BlockState oldState(int i) {
        SchematicDiff.Result r = this.result;
        BlockState s = this.swapped ? r.statesB()[i] : r.statesA()[i];
        return s.mirror(this.placement.getMirror()).rotate(this.placement.getRotation());
    }

    public BlockPos worldPos(long schematicPos) {
        return worldPos(BlockPos.of(schematicPos), this.placement.getOrigin(), this.placement.getMirror(), this.placement.getRotation());
    }

    static BlockPos worldPos(BlockPos p, BlockPos origin, Mirror mirror, Rotation rotation) {
        return PositionUtils.getTransformedBlockPos(p, mirror, rotation).offset(origin);
    }

    // ---------------- tick: keep the boxes near the player ----------------

    public static void tick(Minecraft c) {
        DiffSession s = current;
        if (s == null) return;
        if (c.level == null || !DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().contains(s.placement)) {
            stop();
            return;
        }
        if (!AleToolsConfigs.DIFF.getBooleanValue()) return;
        s.ticks++;
        SchematicDiff.Result r = s.result;
        if (r == null || s.renderBusy) return;
        if (!AleToolsConfigs.DIFF_SHOW.getBooleanValue()) return;
        Entity camera = c.getCameraEntity() != null ? c.getCameraEntity() : c.player;
        if (camera == null) return;
        Vec3 eye = camera.getEyePosition();
        BlockPos origin = s.placement.getOrigin();
        Mirror mirror = s.placement.getMirror();
        Rotation rotation = s.placement.getRotation();
        int max = AleToolsConfigs.DIFF_MAX_BOXES.getIntegerValue();
        // a new pick when the player moved 4 blocks, the placement changed, the result changed or the limit changed
        long key = (((long) Math.floor(eye.x / 4)) * 73856093L) ^ (((long) Math.floor(eye.y / 4)) * 19349663L) ^ (((long) Math.floor(eye.z / 4)) * 83492791L)
                ^ origin.asLong() * 31 ^ mirror.ordinal() * 7L ^ rotation.ordinal() * 13L ^ (long) max << 40 ^ (s.swapped ? 1 : 0);
        if (key == s.renderKey && s.renderGeneration == s.generation) return;
        s.renderKey = key;
        s.renderGeneration = s.generation;
        s.renderBusy = true;
        int gen = s.generation;
        boolean swapped = s.swapped;
        WORKER.submit(() -> {
            try {
                RenderList list = nearest(r, eye, origin, mirror, rotation, max, swapped, null);
                if (current == s && s.generation == gen) renderList = list;
            } finally {
                s.renderBusy = false;
            }
        });
    }

    /** The {@code max} entries nearest to eye (world positions); sortedOut (if given) gets the entry indices in that order */
    static RenderList nearest(SchematicDiff.Result r, Vec3 eye, BlockPos origin, Mirror mirror, Rotation rotation, int max, boolean swapped,
                              @Nullable int[] sortedOut) {
        int n = r.size();
        int keep = Math.min(max, n);
        // (only the worker thread gets here: the arrays are kept for the next pick, a big diff would otherwise
        // allocate tens of MB every few steps the player takes)
        if (scratchDist.length < n) scratchDist = new double[n];
        double[] dist = scratchDist;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < n; i++) {
            BlockPos w = worldPos(m.set(r.positions()[i]), origin, mirror, rotation);
            double dx = w.getX() + 0.5 - eye.x, dy = w.getY() + 0.5 - eye.y, dz = w.getZ() + 0.5 - eye.z;
            dist[i] = dx * dx + dy * dy + dz * dz;
        }
        int[] chosen;
        if (sortedOut != null) {
            for (int i = 0; i < n; i++) sortedOut[i] = i;
            it.unimi.dsi.fastutil.ints.IntArrays.quickSort(sortedOut, 0, n, (x, y) -> Double.compare(dist[x], dist[y]));
            chosen = sortedOut;
        } else {
            // the nearest `keep`: a heap holding the farthest of those kept on top
            it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue heap = new it.unimi.dsi.fastutil.ints.IntHeapPriorityQueue(keep + 1,
                    (x, y) -> Double.compare(dist[y], dist[x]));
            for (int i = 0; i < n && keep > 0; i++) {
                if (heap.size() < keep) heap.enqueue(i);
                else if (dist[i] < dist[heap.firstInt()]) {
                    heap.dequeueInt();
                    heap.enqueue(i);
                }
            }
            chosen = new int[heap.size()];
            for (int i = chosen.length - 1; i >= 0; i--) chosen[i] = heap.dequeueInt();
        }
        long[] pos = new long[keep];
        int[] kinds = new int[keep];
        for (int i = 0; i < keep; i++) {
            int e = chosen[i];
            pos[i] = worldPos(BlockPos.of(r.positions()[e]), origin, mirror, rotation).asLong();
            int k = r.kinds()[e];
            kinds[i] = k == SchematicDiff.CHANGED ? CHANGED : ((k == SchematicDiff.ONLY_A) != swapped ? REMOVED : ADDED);
        }
        return new RenderList(pos, kinds, keep);
    }

    private static double[] scratchDist = new double[0];

    /** All entry indices sorted by distance from the player (for the list screen; runs on the worker thread) */
    Future<int[]> sortedByDistance() {
        SchematicDiff.Result r = this.result;
        Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        Vec3 eye = camera != null ? camera.getEyePosition() : Vec3.ZERO;
        BlockPos origin = this.placement.getOrigin();
        Mirror mirror = this.placement.getMirror();
        Rotation rotation = this.placement.getRotation();
        boolean swapped = this.swapped;
        return WORKER.submit(() -> {
            if (r == null) return new int[0];
            int[] order = new int[r.size()];
            nearest(r, eye, origin, mirror, rotation, 0, swapped, order);
            return order;
        });
    }

    // ---------------- summary and list ----------------

    /** One line for the HUD: counts per kind; null when no diff is shown */
    @Nullable
    public static String summaryLine() {
        DiffSession s = current;
        if (s == null) return null;
        if (s.error != null) return "§c" + StringUtils.translate("autyism-le.hud.diff.error");
        if (s.result == null) return StringUtils.translate("autyism-le.hud.diff.computing");
        String line = StringUtils.translate("autyism-le.hud.diff.summary", s.count(ADDED), s.count(REMOVED), s.count(CHANGED));
        if (!AleToolsConfigs.DIFF_SHOW.getBooleanValue()) line += " §7" + StringUtils.translate("autyism-le.hud.diff.hidden");
        return line;
    }

    /** Open the list of the diff shown (from a hotkey, or from the placement's settings screen) */
    public static void openList(@Nullable net.minecraft.client.gui.screens.Screen parent) {
        DiffSession s = current;
        if (s == null) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "autyism-le.message.diff.none");
            return;
        }
        DiffListScreen screen = new DiffListScreen(s);
        screen.setParent(parent);
        GuiBase.openGui(screen);
    }
}
