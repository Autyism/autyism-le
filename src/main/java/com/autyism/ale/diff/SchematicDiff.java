package com.autyism.ale.diff;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Block by block comparison of two schematics, in the coordinates of schematic A (relative to its origin, before the
 * placement's rotation / mirror). Only block states are compared (not block entity contents or entities).
 * <ul>
 * <li>ONLY_A: a block in A, air in B; ONLY_B: air in A, a block in B; CHANGED: different blocks or states.</li>
 * <li>Alignment ORIGIN: both schematics' origins on the same spot. REGION: each region of B is moved so that its
 * lowest corner meets the lowest corner of the region of A with the same name (else the one at the same index, else the
 * first one). A manual offset (in A's coordinates) is added on top of either.</li>
 * <li>Where regions of one schematic overlap, the first region that has a position counts.</li>
 * </ul>
 * Counts are always exact; at most {@link #MAX_ENTRIES} entries are kept for the list and the boxes.
 */
public final class SchematicDiff {
    public static final int ONLY_A = 0, ONLY_B = 1, CHANGED = 2;
    public static final int MAX_ENTRIES = 1_000_000;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    public enum Alignment {
        ORIGIN, REGION
    }

    /** One region in schematic A coordinates: lowest corner and size */
    private record Region(String name, int x, int y, int z, int sx, int sy, int sz, LitematicaBlockStateContainer blocks) {
        boolean contains(int px, int py, int pz) {
            return px >= x && py >= y && pz >= z && px < x + sx && py < y + sy && pz < z + sz;
        }

        BlockState at(int px, int py, int pz) {
            BlockState s = this.blocks.get(px - x, py - y, pz - z);
            return s == null ? AIR : s;
        }
    }

    /** The result; positions are BlockPos.asLong in schematic A coordinates */
    public record Result(long[] positions, BlockState[] statesA, BlockState[] statesB, byte[] kinds, int size,
                         int onlyA, int onlyB, int changed, boolean truncated) {
        public int total() {
            return this.onlyA + this.onlyB + this.changed;
        }
    }

    private SchematicDiff() {
    }

    /** Runs on a worker thread; returns null when cancelled */
    public static Result compute(LitematicaSchematic a, LitematicaSchematic b, Alignment alignment, Vec3i manual, BooleanSupplier cancelled) {
        List<Region> ra = regions(a, null, Vec3i.ZERO);
        List<Region> rb = regions(b, alignment == Alignment.REGION ? ra : null, manual);
        Builder out = new Builder();
        // every position of A's regions
        for (int i = 0; i < ra.size(); i++) {
            Region r = ra.get(i);
            for (int y = r.y; y < r.y + r.sy; y++) {
                if (cancelled.getAsBoolean()) return null;
                for (int z = r.z; z < r.z + r.sz; z++) {
                    for (int x = r.x; x < r.x + r.sx; x++) {
                        if (inEarlier(ra, i, x, y, z)) continue;
                        BlockState sa = r.at(x, y, z), sb = at(rb, x, y, z);
                        out.compare(x, y, z, sa, sb);
                    }
                }
            }
        }
        // the positions of B's regions that A has no region for
        for (int j = 0; j < rb.size(); j++) {
            Region r = rb.get(j);
            for (int y = r.y; y < r.y + r.sy; y++) {
                if (cancelled.getAsBoolean()) return null;
                for (int z = r.z; z < r.z + r.sz; z++) {
                    for (int x = r.x; x < r.x + r.sx; x++) {
                        if (inAny(ra, x, y, z) || inEarlier(rb, j, x, y, z)) continue;
                        out.compare(x, y, z, AIR, r.at(x, y, z));
                    }
                }
            }
        }
        return out.build();
    }

    private static boolean inEarlier(List<Region> list, int index, int x, int y, int z) {
        for (int k = 0; k < index; k++) {
            if (list.get(k).contains(x, y, z)) return true;
        }
        return false;
    }

    private static boolean inAny(List<Region> list, int x, int y, int z) {
        for (Region r : list) {
            if (r.contains(x, y, z)) return true;
        }
        return false;
    }

    private static BlockState at(List<Region> list, int x, int y, int z) {
        for (Region r : list) {
            if (r.contains(x, y, z)) return r.at(x, y, z);
        }
        return AIR;
    }

    /**
     * Regions of a schematic with their lowest corner relative to its origin; when alignTo is given, each one is moved
     * onto the matching region of the other schematic. Then the offset is added.
     */
    private static List<Region> regions(LitematicaSchematic s, List<Region> alignTo, Vec3i offset) {
        List<Region> out = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, BlockPos> e : s.getAreaSizes().entrySet()) {
            String name = e.getKey();
            BlockPos size = e.getValue();
            BlockPos pos = s.getSubRegionPosition(name);
            LitematicaBlockStateContainer blocks = s.getSubRegionContainer(name);
            if (size == null || pos == null || blocks == null) continue;
            // a negative size means the region goes from its position towards smaller coordinates
            int x = size.getX() < 0 ? pos.getX() + size.getX() + 1 : pos.getX();
            int y = size.getY() < 0 ? pos.getY() + size.getY() + 1 : pos.getY();
            int z = size.getZ() < 0 ? pos.getZ() + size.getZ() + 1 : pos.getZ();
            Vec3i dim = blocks.getSize();
            if (alignTo != null && !alignTo.isEmpty()) {
                Region match = null;
                for (Region r : alignTo) {
                    if (r.name.equals(name)) match = r;
                }
                if (match == null) match = index < alignTo.size() ? alignTo.get(index) : alignTo.getFirst();
                x = match.x;
                y = match.y;
                z = match.z;
            }
            out.add(new Region(name, x + offset.getX(), y + offset.getY(), z + offset.getZ(), dim.getX(), dim.getY(), dim.getZ(), blocks));
            index++;
        }
        return out;
    }

    private static final class Builder {
        long[] pos = new long[1024];
        BlockState[] sa = new BlockState[1024], sb = new BlockState[1024];
        byte[] kind = new byte[1024];
        int size, onlyA, onlyB, changed;
        boolean truncated;

        void compare(int x, int y, int z, BlockState a, BlockState b) {
            if (a == b) return;
            boolean airA = a.isAir(), airB = b.isAir();
            int k;
            if (airA && airB) return;
            if (airB) {
                k = ONLY_A;
                onlyA++;
            } else if (airA) {
                k = ONLY_B;
                onlyB++;
            } else {
                k = CHANGED;
                changed++;
            }
            if (size >= MAX_ENTRIES) {
                truncated = true;
                return;
            }
            if (size == pos.length) {
                int n = Math.min(MAX_ENTRIES, pos.length * 2);
                pos = java.util.Arrays.copyOf(pos, n);
                sa = java.util.Arrays.copyOf(sa, n);
                sb = java.util.Arrays.copyOf(sb, n);
                kind = java.util.Arrays.copyOf(kind, n);
            }
            pos[size] = BlockPos.asLong(x, y, z);
            sa[size] = a;
            sb[size] = b;
            kind[size] = (byte) k;
            size++;
        }

        Result build() {
            return new Result(pos, sa, sb, kind, size, onlyA, onlyB, changed, truncated);
        }
    }
}
