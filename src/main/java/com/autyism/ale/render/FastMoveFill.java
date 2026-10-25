package com.autyism.ale.render;

//? if >=1.21.11 <26.1 {
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.util.PositionUtils;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
//?}
import net.fabricmc.loader.api.FabricLoader;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * 装了 Litefastica（快速移动投影）时，它接管的投影由它自己的网格来画（默认是所有投影，关掉“接管所有投影”后也还有大投影），
 * 不经过 Litematica 的区块渲染，F 要在它的覆盖层里画（见 OverlayMeshBuilderMixin）。
 * 它的网格按投影本身来建：移动投影时投影世界要过一会儿才写好（大投影停手后才提交），所以容器里该放什么直接从投影文件里读，
 * 不看投影世界。它的类都按名字找，它更新后改了名就找不到，只是这些投影上没有 F，不会出错。
 */
public final class FastMoveFill {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("litematicafastmove");
    private static final MethodHandle MARK_CHUNK_DIRTY = LOADED ? markChunkDirtyHandle() : null;

    private FastMoveFill() {
    }

    /** 世界里这个区块的 F 可能变了（容器确认填好了 / 又不对了）：它画的投影在这里的区段也重建 */
    public static void markChunkDirty(int chunkX, int chunkZ) {
        if (MARK_CHUNK_DIRTY == null) return;
        try {
            MARK_CHUNK_DIRTY.invokeExact(chunkX, chunkZ);
        } catch (Throwable ignored) {
        }
    }

    private static MethodHandle markChunkDirtyHandle() {
        try {
            Class<?> manager = Class.forName("ml.mypals.litematicafastmove.mesh.PlacementMeshManager");
            return MethodHandles.publicLookup().findStatic(manager, "markChunkDirty", MethodType.methodType(void.class, int.class, int.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    //? if >=1.21.11 <26.1 {
    /**
     * 建网格时的投影、它当时的原点（网格里的坐标按这个原点算；世界里的位置 = 网格坐标 + 现在的原点 - 这个原点）
     * 和它的投影视图（按网格坐标取投影里的方块）
     */
    public record Build(SchematicPlacement placement, BlockPos origin, BlockGetter view) {
        public BlockPos toWorld(BlockPos buildPos) {
            BlockPos now = this.placement.getOrigin();
            return buildPos.offset(now.getX() - this.origin.getX(), now.getY() - this.origin.getY(), now.getZ() - this.origin.getZ());
        }
    }

    // OverlayMeshBuilder.schematic -> DetachedBlockView.accessor -> PlacementBlockAccessor.placement / origin
    private static Field schematicField, accessorField, placementField, originField;
    private static boolean lookupFailed;

    /** 这个覆盖层构建器正在建的投影；找不到（它改了结构）时 null */
    @Nullable
    public static Build build(Object overlayBuilder) {
        if (lookupFailed) return null;
        try {
            if (schematicField == null) {
                Field s = overlayBuilder.getClass().getDeclaredField("schematic");
                s.setAccessible(true);
                Field a = s.getType().getDeclaredField("accessor");
                a.setAccessible(true);
                Field p = a.getType().getField("placement");
                Field o = a.getType().getField("origin");
                accessorField = a;
                placementField = p;
                originField = o;
                schematicField = s;
            }
            Object view = schematicField.get(overlayBuilder);
            Object accessor = view == null ? null : accessorField.get(view);
            if (accessor == null) return null;
            return new Build((SchematicPlacement) placementField.get(accessor), (BlockPos) originField.get(accessor), (BlockGetter) view);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            lookupFailed = true;
            return null;
        }
    }

    /**
     * 投影文件里这一格（网格坐标）的方块实体，按投影里存的数据建出来；不是方块实体、或者没存数据时 null。
     * 只在区段建网格的线程里调用：投影文件的数据这时只读不写
     */
    @Nullable
    public static BlockEntity schematicBlockEntity(Build build, BlockPos buildPos, BlockPos worldPos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        SchematicPlacement placement = build.placement();
        LitematicaSchematic schematic = placement.getSchematic();
        for (SubRegionPlacement sub : placement.getAllSubRegionsPlacements()) {
            if (!sub.isEnabled()) continue;
            String name = sub.getName();
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(name);
            Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion(name);
            BlockPos size = schematic.getAreaSize(name);
            if (container == null || blockEntities == null || size == null) continue;
            BlockPos local = regionLocal(buildPos, build.origin(), placement, sub, size);
            if (local == null) continue;
            BlockState stored = container.get(local.getX(), local.getY(), local.getZ());
            if (stored.getBlock() != state.getBlock()) continue;
            CompoundData data = blockEntities.get(local);
            // 镜像的大箱子：Litematica 放进投影世界时两半的内容对调（“修正箱子镜像”），这里也一样
            if (stored.is(net.minecraft.world.level.block.Blocks.CHEST) && placement.getMirror() != Mirror.NONE
                    && stored.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && fi.dy.masa.litematica.config.Configs.Generic.FIX_CHEST_MIRROR.getBooleanValue()) {
                Direction facing = stored.getValue(ChestBlock.FACING);
                if (facing.getAxis() != Direction.Axis.Y) {
                    ChestType type = stored.getValue(ChestBlock.TYPE).getOpposite();
                    Direction toAdj = type == ChestType.LEFT ? facing.getCounterClockWise(Direction.Axis.Y) : facing.getClockWise(Direction.Axis.Y);
                    CompoundData adj = blockEntities.get(local.relative(toAdj));
                    if (adj != null) data = adj;
                }
            }
            if (data == null) return null;
            try {
                BlockEntity be = entityBlock.newBlockEntity(worldPos, state);
                if (be == null) return null;
                be.loadWithComponents(fi.dy.masa.malilib.util.nbt.NbtView.getReader(data, level.registryAccess()).getReader());
                return be;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /** 网格坐标在这个子区域里的格子（和 Litematica 把投影放进投影世界时的换算一样，反过来算）；不在里面时 null */
    @Nullable
    private static BlockPos regionLocal(BlockPos buildPos, BlockPos origin, SchematicPlacement placement, SubRegionPlacement sub, BlockPos size) {
        BlockPos regionPos = sub.getPos();
        BlockPos posEndRel = PositionUtils.getRelativeEndPositionFromAreaSize(size).offset(regionPos);
        BlockPos posMinRel = PositionUtils.getMinCorner(regionPos, posEndRel);
        Mirror mirror = placement.getMirror();
        Rotation rotation = placement.getRotation();
        BlockPos regionPosTransformed = PositionUtils.getTransformedBlockPos(regionPos, mirror, rotation);
        BlockPos rel = buildPos.subtract(origin).subtract(regionPosTransformed);
        rel = PositionUtils.getReverseTransformedBlockPos(rel, sub.getMirror(), sub.getRotation());
        rel = PositionUtils.getReverseTransformedBlockPos(rel, mirror, rotation);
        BlockPos local = rel.subtract(posMinRel.subtract(regionPos));
        if (local.getX() < 0 || local.getY() < 0 || local.getZ() < 0
                || local.getX() >= Math.abs(size.getX()) || local.getY() >= Math.abs(size.getY()) || local.getZ() >= Math.abs(size.getZ())) {
            return null;
        }
        return local;
    }
    //?}
}
