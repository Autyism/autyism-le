package com.autyism.ale.preview;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.selection.Box;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 投影的只读方块视图：所有区域合在一起，坐标以整个投影的最小角为 (0, 0, 0)。
 * 给方块模型和流体的网格生成用：亮度固定满亮，群系颜色取平原，没有方块实体（方块实体单独渲染）。
 */
public final class SchematicView implements BlockAndTintGetter {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private record Region(int x0, int y0, int z0, int sx, int sy, int sz, LitematicaBlockStateContainer container) {
    }

    private final Region[] regions;
    private final int sizeX, sizeY, sizeZ;
    /** 每个区域在投影坐标里的起点（用于方块实体的位置换算） */
    private final Map<String, BlockPos> regionOrigins;
    @Nullable
    private final Biome biome;

    private SchematicView(Region[] regions, int sizeX, int sizeY, int sizeZ, Map<String, BlockPos> regionOrigins) {
        this.regions = regions;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.regionOrigins = regionOrigins;
        this.biome = plains();
    }

    public static SchematicView of(LitematicaSchematic schematic) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        List<String> names = new ArrayList<>();
        List<int[]> mins = new ArrayList<>();
        List<LitematicaBlockStateContainer> containers = new ArrayList<>();
        for (Map.Entry<String, Box> e : schematic.getAreas().entrySet()) {
            Box box = e.getValue();
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(e.getKey());
            if (box == null || box.getPos1() == null || box.getPos2() == null || container == null) continue;
            BlockPos a = box.getPos1(), b = box.getPos2();
            int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
            Vec3i size = container.getSize();
            minX = Math.min(minX, x0);
            minY = Math.min(minY, y0);
            minZ = Math.min(minZ, z0);
            maxX = Math.max(maxX, x0 + size.getX());
            maxY = Math.max(maxY, y0 + size.getY());
            maxZ = Math.max(maxZ, z0 + size.getZ());
            names.add(e.getKey());
            mins.add(new int[]{x0, y0, z0});
            containers.add(container);
        }
        if (names.isEmpty()) {
            return new SchematicView(new Region[0], 1, 1, 1, Map.of());
        }
        Region[] regions = new Region[names.size()];
        Map<String, BlockPos> origins = new java.util.HashMap<>();
        for (int i = 0; i < regions.length; i++) {
            int[] m = mins.get(i);
            Vec3i size = containers.get(i).getSize();
            regions[i] = new Region(m[0] - minX, m[1] - minY, m[2] - minZ, size.getX(), size.getY(), size.getZ(), containers.get(i));
            origins.put(names.get(i), new BlockPos(m[0] - minX, m[1] - minY, m[2] - minZ));
        }
        return new SchematicView(regions, maxX - minX, maxY - minY, maxZ - minZ, origins);
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    /** 区域在投影坐标里的起点（没有这个区域时为 null） */
    @Nullable
    public BlockPos regionOrigin(String region) {
        return this.regionOrigins.get(region);
    }

    public BlockState get(int x, int y, int z) {
        for (Region r : this.regions) {
            int lx = x - r.x0, ly = y - r.y0, lz = z - r.z0;
            if (lx < 0 || ly < 0 || lz < 0 || lx >= r.sx || ly >= r.sy || lz >= r.sz) continue;
            BlockState state = r.container.get(lx, ly, lz);
            if (state != null && !state.isAir()) return state;
        }
        return AIR;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return get(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) return 1.0F;
        return switch (direction) {
            case DOWN -> 0.5F;
            case UP -> 1.0F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return true;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (this.biome != null) {
            try {
                return resolver.getColor(this.biome, pos.getX(), pos.getZ());
            } catch (Throwable ignored) {
            }
        }
        if (resolver == BiomeColors.WATER_COLOR_RESOLVER) return 0x3F76E4;
        if (resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER) return 0x77AB2F;
        if (resolver == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) return 0xA0A69C;
        return 0x91BD59;
    }

    @Override
    public int getHeight() {
        return 4096;
    }

    @Override
    public int getMinY() {
        return -2048;
    }

    @Nullable
    private static Biome plains() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level != null) {
                return mc.level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS).value();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
