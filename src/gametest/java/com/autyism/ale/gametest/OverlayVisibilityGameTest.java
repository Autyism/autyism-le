package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 水、气泡柱、含水方块、冰、染色玻璃、粘液块、海带、岩浆、蜘蛛网、光源方块……上的错误标记（覆盖层）
 * 要和普通方块上的一样清楚。
 * <p>
 * 每个用例是一格：投影里是一种方块，世界里是另一种。同样的角度、只渲染这一层，比较开 / 关覆盖层的两张截图，
 * 得到覆盖层的“可见度”，再和同类型标记的普通方块（参照）比。透过玻璃显示投影关 / 开两种情况都测。
 */
@SuppressWarnings("UnstableApiUsage")
public final class OverlayVisibilityGameTest implements FabricClientGameTest {
    private static final int X0 = 100, STEP = 8, Y = 64, Z = 0;

    /** 一个用例：名字、参照用例名（参照本身为 null）、投影里的方块、世界里的方块（相对用例位置） */
    private record Case(String name, String ref, BiConsumer<ServerLevel, BlockPos> schematic, BiConsumer<ServerLevel, BlockPos> world) {
    }

    private static BiConsumer<ServerLevel, BlockPos> one(BlockState state) {
        return (level, pos) -> ViewShots.set(level, pos, state);
    }

    private static List<Case> cases() {
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        List<Case> list = new ArrayList<>();
        // 参照：普通方块上的四种标记
        list.add(new Case("ref-wrong-block", null, one(Blocks.STONE.defaultBlockState()), one(Blocks.COBBLESTONE.defaultBlockState())));
        list.add(new Case("ref-wrong-state", null, one(Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 5)),
                one(Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, 0))));
        list.add(new Case("ref-extra", null, one(Blocks.AIR.defaultBlockState()), one(Blocks.STONE.defaultBlockState())));
        list.add(new Case("ref-missing", null, one(Blocks.STONE.defaultBlockState()), one(Blocks.AIR.defaultBlockState())));
        // 水
        list.add(new Case("water-level", "ref-wrong-state", one(water), one(water.setValue(LiquidBlock.LEVEL, 2))));
        list.add(new Case("water-extra", "ref-extra", one(Blocks.AIR.defaultBlockState()), one(water)));
        list.add(new Case("water-missing", "ref-missing", one(water), one(Blocks.AIR.defaultBlockState())));
        list.add(new Case("bubble-column-in-world", "ref-wrong-block", (level, pos) -> {
            ViewShots.set(level, pos.below(), Blocks.SOUL_SAND.defaultBlockState());
            ViewShots.set(level, pos, water);
        }, (level, pos) -> {
            ViewShots.set(level, pos.below(), Blocks.SOUL_SAND.defaultBlockState());
            ViewShots.set(level, pos, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BlockStateProperties.DRAG, false));
        }));
        list.add(new Case("water-instead-of-bubble-column", "ref-wrong-block", (level, pos) -> {
            ViewShots.set(level, pos.below(), Blocks.SOUL_SAND.defaultBlockState());
            ViewShots.set(level, pos, Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(BlockStateProperties.DRAG, false));
        }, (level, pos) -> {
            ViewShots.set(level, pos.below(), Blocks.SOUL_SAND.defaultBlockState());
            ViewShots.set(level, pos, water);
        }));
        // 水池底部（上面和四周都是水）
        list.add(new Case("deep-water", "ref-wrong-block", (level, pos) -> pool(level, pos, Blocks.STONE.defaultBlockState()), (level, pos) -> pool(level, pos, water)));
        // 含水方块（世界里含水，投影里不含水）
        list.add(new Case("waterlogged-stairs", "ref-wrong-state", one(stairs), one(stairs.setValue(BlockStateProperties.WATERLOGGED, true))));
        list.add(new Case("kelp-vs-seagrass", "ref-wrong-block", one(Blocks.SEAGRASS.defaultBlockState()), one(Blocks.KELP.defaultBlockState())));
        // 半透明方块
        list.add(new Case("ice", "ref-wrong-block", one(Blocks.PACKED_ICE.defaultBlockState()), one(Blocks.ICE.defaultBlockState())));
        list.add(new Case("stained-glass", "ref-wrong-block", one(Blocks.BLUE_STAINED_GLASS.defaultBlockState()), one(Blocks.RED_STAINED_GLASS.defaultBlockState())));
        list.add(new Case("slime", "ref-wrong-block", one(Blocks.HONEY_BLOCK.defaultBlockState()), one(Blocks.SLIME_BLOCK.defaultBlockState())));
        list.add(new Case("glass", "ref-wrong-block", one(Blocks.TINTED_GLASS.defaultBlockState()), one(Blocks.GLASS.defaultBlockState())));
        // 其他
        list.add(new Case("lava-level", "ref-wrong-state", one(Blocks.LAVA.defaultBlockState()), one(Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2))));
        list.add(new Case("cobweb", "ref-wrong-block", one(Blocks.STONE.defaultBlockState()), one(Blocks.COBWEB.defaultBlockState())));
        list.add(new Case("light-block", "ref-wrong-state", one(Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 7)),
                one(Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15))));
        return list;
    }

    /** 3×3×2 的水池，中间最底下一格放 center */
    private static void pool(ServerLevel level, BlockPos pos, BlockState center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    ViewShots.set(level, p, dx == 0 && dy == 0 && dz == 0 ? center : Blocks.WATER.defaultBlockState());
                }
            }
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("overlayvis")) return;
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
                Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.setBooleanValue(true);
                Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.setBooleanValue(true);
                Configs.Visuals.ENABLE_SCHEMATIC_FLUIDS.setBooleanValue(true);
                // 只看覆盖层本身：ALE 的 W / D 字母另有测试
                AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(false);
                AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(false);
            });
            // 先在世界里摆出投影的样子并截取成投影，再换成世界里的方块
            sp.getServer().runOnServer(s -> {
                for (int i = 0; i < cases.size(); i++) cases.get(i).schematic.accept(s.overworld(), new BlockPos(X0 + STEP * i, Y, Z));
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, new BlockPos(X0 - 2, Y - 1, Z - 2),
                    new BlockPos(xMax + 2, Y + 2, Z + 2), new BlockPos(X0 - 2, Y - 1, Z - 2), "ale_overlayvis");
            GT.waitSchematicBlock(context, new BlockPos(X0, Y, Z), Blocks.STONE);
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int i = 0; i < cases.size(); i++) {
                    BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                    for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 1, 1))) ViewShots.set(level, p.immutable(), Blocks.AIR.defaultBlockState());
                    ViewShots.set(level, pos.below(), Blocks.BEDROCK.defaultBlockState());
                    cases.get(i).world.accept(level, pos);
                }
            });
            context.waitTicks(10);

            Map<String, long[]> off = new HashMap<>(), on = new HashMap<>();
            for (int i = 0; i < cases.size(); i++) {
                Case cs = cases.get(i);
                BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                ViewShots.lookAt(context, sp, pos);
                ViewShots.onlyLayerX(context, pos.getX());
                // 没有覆盖层 / 有覆盖层，各在透过玻璃显示关、开时拍一张（这个开关不需要重建）
                context.runOnClient(c -> Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(false));
                ViewShots.rebuild(context, placement);
                Path baseOff = glassShot(context, false, "ale-overlayvis-" + cs.name + "-none-glassoff");
                Path baseOn = glassShot(context, true, "ale-overlayvis-" + cs.name + "-none-glasson");
                context.runOnClient(c -> Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true));
                ViewShots.rebuild(context, placement);
                Path withOff = glassShot(context, false, "ale-overlayvis-" + cs.name + "-glassoff");
                Path withOn = glassShot(context, true, "ale-overlayvis-" + cs.name + "-glasson");
                off.put(cs.name, ViewShots.diff(baseOff, withOff));
                on.put(cs.name, ViewShots.diff(baseOn, withOn));
            }

            List<String> failures = new ArrayList<>();
            for (Case cs : cases) {
                long[] a = off.get(cs.name), b = on.get(cs.name);
                String line = String.format(java.util.Locale.ROOT, "[overlayvis] %-32s glass-off: %5d (%6d px)  glass-on: %5d (%6d px)", cs.name, a[0], a[1], b[0], b[1]);
                if (cs.ref != null) {
                    double relOff = ratio(a[0], off.get(cs.ref)[0]), relOn = ratio(b[0], on.get(cs.ref)[0]);
                    line += String.format(java.util.Locale.ROOT, "  vs %s: glass-off %.2f, glass-on %.2f", cs.ref, relOff, relOn);
                    // 透过玻璃显示开着（默认）时，要和普通方块上的标记差不多清楚
                    if (relOn < 0.6) failures.add(cs.name + " " + String.format(java.util.Locale.ROOT, "%.2f", relOn));
                }
                GT.log(line);
            }
            if (!failures.isEmpty() && !GTFilter.measureOnly()) {
                throw new AssertionError("[overlayvis] overlay much fainter than on a normal block (render through glass on): " + failures);
            }
            GT.log("[overlayvis] " + (failures.isEmpty() ? "OK: every overlay is about as visible as on a normal block" : "measured only, too faint: " + failures));
            // 世界关掉之前恢复（渲染层等设置会存进同名世界的数据）
            ViewShots.end(context);
        } finally {
            ViewShots.end(context);
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(true);
                AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(true);
                AleConfigs.Generic.RENDER_THROUGH_GLASS.setBooleanValue(true);
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
            });
        }
    }

    private static Path glassShot(ClientGameTestContext context, boolean throughGlass, String name) {
        context.runOnClient(c -> AleConfigs.Generic.RENDER_THROUGH_GLASS.setBooleanValue(throughGlass));
        context.waitTicks(2);
        return GT.shot(context, name);
    }

    private static double ratio(long a, long b) {
        return b <= 0 ? 0 : (double) a / b;
    }
}
