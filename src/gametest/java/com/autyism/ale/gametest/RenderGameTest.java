package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.config.Configs;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * 渲染相关需求的截图测试。
 * 需求 6：只差含水 → 黄色错误标记 + 蓝色 W；朝向错误 → 黄色 + 红色 D。用截图中蓝色 / 红色像素数量判断。
 */
@SuppressWarnings("UnstableApiUsage")
public final class RenderGameTest implements FabricClientGameTest {
    private static final BlockPos W_POS = new BlockPos(70, 64, 0);
    private static final BlockPos FACING_POS = new BlockPos(73, 64, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("render")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 64, -6, 80, 8, 70);
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 71.5 64 4.5 180 25");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 71.5) < 0.01, 200);
            context.runOnClient(c -> {
                GT.setGuiHidden(c, true);
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Visuals.SCHEMATIC_OVERLAY_TYPE_WRONG_STATE.setBooleanValue(true);
            });
            BlockState stairsSchem = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH)
                    .setValue(BlockStateProperties.WATERLOGGED, true);
            sp.getServer().runOnServer(s -> {
                s.overworld().setBlockAndUpdate(W_POS, stairsSchem);
                s.overworld().setBlockAndUpdate(FACING_POS, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
            });
            context.waitTicks(5);
            var placement = GT.captureAndPlace(context, sp, W_POS, FACING_POS, W_POS, "ale_render_w");
            // 世界里：同朝向但不含水；另一个朝向不同
            sp.getServer().runOnServer(s -> {
                s.overworld().setBlockAndUpdate(W_POS, stairsSchem.setValue(BlockStateProperties.WATERLOGGED, false));
                s.overworld().setBlockAndUpdate(W_POS.above(), Blocks.AIR.defaultBlockState());
                s.overworld().setBlockAndUpdate(FACING_POS, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
            });
            context.waitTicks(40);
            Path shot = context.takeScreenshot("ale-waterlogged-w");
            int blueOn = countBlue(shot);
            context.runOnClient(c -> AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(false));
            context.runOnClient(c -> fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(placement.getSchematic()));
            context.waitTicks(40);
            Path shotOff = context.takeScreenshot("ale-waterlogged-w-off");
            int blueOff = countBlue(shotOff);
            context.runOnClient(c -> AleConfigs.Generic.WATERLOGGED_MARKER.setBooleanValue(true));
            GT.log("[render] blue pixels: marker on=" + blueOn + " off=" + blueOff);
            if (blueOn < 300 || blueOn < blueOff * 3 + 200) throw new AssertionError("[render] W marker not visible (blue on=" + blueOn + " off=" + blueOff + ")");
            GT.log("[render] waterlogged W marker OK");

            // 朝向不对的楼梯：红色 D
            context.runOnClient(c -> fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(placement.getSchematic()));
            context.waitTicks(40);
            Path dOn = context.takeScreenshot("ale-orientation-d");
            int redOn = countRed(dOn);
            context.runOnClient(c -> AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(false));
            context.runOnClient(c -> fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(placement.getSchematic()));
            context.waitTicks(40);
            Path dOff = context.takeScreenshot("ale-orientation-d-off");
            int redOff = countRed(dOff);
            context.runOnClient(c -> AleConfigs.Generic.ORIENTATION_MARKER.setBooleanValue(true));
            GT.log("[render] red pixels: D marker on=" + redOn + " off=" + redOff);
            if (redOn < 300 || redOn < redOff * 3 + 200) throw new AssertionError("[render] D marker not visible (red on=" + redOn + " off=" + redOff + ")");
            GT.log("[render] orientation D marker OK");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> GT.setGuiHidden(c, false));
        }
    }

    /** 统计截图里明显偏红的像素数量 */
    static int countRed(Path png) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            int n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (r > 170 && g < 80 && b < 80) n++;
                }
            }
            return n;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** 统计截图里明显偏蓝的像素数量 */
    static int countBlue(Path png) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            int n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (b > 170 && r < 90 && g < 140 && b > g + 60) n++;
                }
            }
            return n;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
