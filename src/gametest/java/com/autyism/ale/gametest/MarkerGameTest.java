package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 蓝色 W（含水不对）和红色 D（朝向不对）在有好几个属性同时不对时也要显示：
 * 铁轨形状 + 含水、楼梯朝向 + 含水、楼梯形状 + 含水、台阶上下 + 含水、活板门朝向 + 开关 + 含水、栅栏连接 + 含水……
 * 每格单独渲染，数截图里 W 的蓝色像素和 D 的红色像素（减去不画字母时的数量）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class MarkerGameTest implements FabricClientGameTest {
    private static final int X0 = 300, STEP = 6, Y = 64, Z = 0;
    /** 判定“画了这个字母”的最少像素数 */
    private static final int MIN_PIXELS = 120;

    private record Case(String name, BlockState schematic, BlockState world, boolean wantW, boolean wantD) {
    }

    private static List<Case> cases() {
        List<Case> list = new ArrayList<>();
        BlockState rail = Blocks.RAIL.defaultBlockState();
        list.add(new Case("rail-shape+water", rail.setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH).setValue(BlockStateProperties.WATERLOGGED, true),
                rail.setValue(RailBlock.SHAPE, RailShape.EAST_WEST), true, true));
        list.add(new Case("rail-shape-only", rail.setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),
                rail.setValue(RailBlock.SHAPE, RailShape.EAST_WEST), false, true));
        list.add(new Case("rail-water-only", rail.setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH).setValue(BlockStateProperties.WATERLOGGED, true),
                rail.setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH), true, false));
        BlockState powered = Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.NORTH_SOUTH);
        list.add(new Case("powered-rail-power+water", powered.setValue(BlockStateProperties.WATERLOGGED, true),
                powered.setValue(PoweredRailBlock.POWERED, true), true, false));
        list.add(new Case("detector-rail-shape+water", Blocks.DETECTOR_RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, RailShape.NORTH_SOUTH)
                .setValue(BlockStateProperties.WATERLOGGED, true), Blocks.DETECTOR_RAIL.defaultBlockState().setValue(BlockStateProperties.RAIL_SHAPE_STRAIGHT, RailShape.EAST_WEST), true, true));
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        list.add(new Case("stairs-facing+water", stairs.setValue(BlockStateProperties.WATERLOGGED, true), stairs.setValue(StairBlock.FACING, Direction.EAST), true, true));
        list.add(new Case("stairs-shape+water", stairs.setValue(BlockStateProperties.WATERLOGGED, true), stairs.setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT), true, false));
        list.add(new Case("stairs-water-only", stairs.setValue(BlockStateProperties.WATERLOGGED, true), stairs, true, false));
        list.add(new Case("stairs-facing-only", stairs, stairs.setValue(StairBlock.FACING, Direction.EAST), false, true));
        list.add(new Case("stairs-facing+shape", stairs, stairs.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.SHAPE, StairsShape.INNER_RIGHT), false, true));
        BlockState slab = Blocks.OAK_SLAB.defaultBlockState();
        list.add(new Case("slab-type+water", slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM).setValue(BlockStateProperties.WATERLOGGED, true),
                slab.setValue(SlabBlock.TYPE, SlabType.TOP), true, false));
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH);
        list.add(new Case("trapdoor-facing+open+water", trapdoor.setValue(BlockStateProperties.WATERLOGGED, true),
                trapdoor.setValue(TrapDoorBlock.FACING, Direction.SOUTH).setValue(TrapDoorBlock.OPEN, true), true, true));
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        list.add(new Case("fence-side+water", fence.setValue(FenceBlock.NORTH, true).setValue(BlockStateProperties.WATERLOGGED, true), fence, true, false));
        return list;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("markers")) return;
        List<Case> cases = cases();
        int xMax = X0 + STEP * (cases.size() - 1);
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 4, -6, xMax + 4, 8, 72);
            ViewShots.begin(context, sp);
            // 先站到用例旁边，客户端载入这一片区块后投影才会载入
            ViewShots.lookAt(context, sp, new BlockPos(X0, Y, Z));
            context.waitTicks(20);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_WRONG_STATE.setBooleanValue(true);
                AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(true);
                AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(true);
            });
            sp.getServer().runOnServer(s -> {
                for (int i = 0; i < cases.size(); i++) ViewShots.set(s.overworld(), new BlockPos(X0 + STEP * i, Y, Z), cases.get(i).schematic);
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, new BlockPos(X0, Y, Z), new BlockPos(xMax, Y, Z), new BlockPos(X0, Y, Z), "ale_markers");
            GT.waitSchematicBlock(context, new BlockPos(X0, Y, Z), Blocks.RAIL);
            sp.getServer().runOnServer(s -> {
                for (int i = 0; i < cases.size(); i++) ViewShots.set(s.overworld(), new BlockPos(X0 + STEP * i, Y, Z), cases.get(i).world);
            });
            context.waitTicks(10);

            List<String> failures = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                Case cs = cases.get(i);
                BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                ViewShots.lookAt(context, sp, pos);
                ViewShots.onlyLayerX(context, pos.getX());
                markers(context, false);
                ViewShots.rebuild(context, placement);
                Path without = GT.shot(context, "ale-markers-" + cs.name + "-off");
                markers(context, true);
                ViewShots.rebuild(context, placement);
                Path with = GT.shot(context, "ale-markers-" + cs.name);
                int blue = RenderGameTest.countBlue(with) - RenderGameTest.countBlue(without);
                int red = RenderGameTest.countRed(with) - RenderGameTest.countRed(without);
                boolean hasW = blue >= MIN_PIXELS, hasD = red >= MIN_PIXELS;
                boolean ok = hasW == cs.wantW && hasD == cs.wantD;
                GT.log(String.format(java.util.Locale.ROOT, "[markers] %-28s W: %5d px (want %s)  D: %5d px (want %s)  %s",
                        cs.name, blue, cs.wantW ? "yes" : "no", red, cs.wantD ? "yes" : "no", ok ? "ok" : "WRONG"));
                if (!ok) failures.add(cs.name);
            }
            if (!failures.isEmpty() && !GTFilter.measureOnly()) throw new AssertionError("[markers] wrong W / D markers: " + failures);
            GT.log("[markers] " + (failures.isEmpty() ? "OK: W and D shown for every wrong property, also together" : "measured only, wrong: " + failures));
            // 世界关掉之前恢复（渲染层等设置会存进同名世界的数据）
            ViewShots.end(context);
        } finally {
            ViewShots.end(context);
            GT.removeAllPlacements(context);
            markers(context, true);
        }
    }

    private static void markers(ClientGameTestContext context, boolean on) {
        context.runOnClient(c -> {
            AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(on);
            AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(on);
        });
    }
}
