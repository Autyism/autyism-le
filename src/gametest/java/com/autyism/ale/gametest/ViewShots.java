package com.autyism.ale.gametest;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.util.LayerMode;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * 一格一格拍截图的测试工具：每个用例从同样的角度看一个方块，用 Litematica 的“渲染层”只显示这一格的投影，
 * 比较打开 / 关闭某个效果的两张截图。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ViewShots {
    private ViewShots() {
    }

    /** 不触发方块更新、不调用 onPlace（流体不流、铁轨不自动连接），拆掉的容器不掉东西 */
    public static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    public static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, QUIET);
    }

    /** 旁观模式、隐藏界面、世界暂停（流体不流、不刷怪），动画贴图停住 */
    public static void begin(ClientGameTestContext context, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode spectator @a");
        sp.getServer().runCommand("time set noon");
        sp.getServer().runCommand("tick freeze");
        context.runOnClient(c -> {
            GT.setGuiHidden(c, true);
            c.particleEngine.clearParticles();
        });
        GTFilter.stillFrames = true;
    }

    /**
     * 恢复客户端的设置。世界暂停不用解除：测试世界马上关掉，新世界本来就不暂停
     * （关世界时再发“解除暂停”的提示消息，会在连接关闭时出错，影响下一个测试）
     */
    public static void end(ClientGameTestContext context) {
        GTFilter.stillFrames = false;
        context.runOnClient(c -> {
            GT.setGuiHidden(c, false);
            var range = DataManager.getRenderLayerRange();
            range.setLayerMode(LayerMode.ALL);
        });
    }

    /** 站到方块 (x, y, z) 南边稍高处，朝北往下看着它 */
    public static void lookAt(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos target) {
        double x = target.getX() + 0.5, y = target.getY() + 2.0, z = target.getZ() + 3.5;
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f 180 46", x, y, z));
        context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - x) < 0.01 && Math.abs(c.player.getZ() - z) < 0.01, 200);
    }

    /** 只渲染 x = layerX 这一层的投影 */
    public static void onlyLayerX(ClientGameTestContext context, int layerX) {
        context.runOnClient(c -> {
            var range = DataManager.getRenderLayerRange();
            range.setLayerMode(LayerMode.SINGLE_LAYER);
            range.setAxis(Direction.Axis.X);
            range.setLayerSingle(layerX);
        });
    }

    /** 设置改变后重建投影的渲染数据并等它完成 */
    public static void rebuild(ClientGameTestContext context, SchematicPlacement placement) {
        context.runOnClient(c -> {
            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(placement.getSchematic());
            fi.dy.masa.litematica.render.LitematicaRenderer.getInstance().updateConfigState();
        });
        context.waitTicks(5);
        // Litematica 在后台线程重新放投影方块（设置的回调也会触发）：等它做完，再给区块重画几 tick。
        // 固定等 15 tick 偶尔不够，会截到投影刚被清空的那一帧
        for (int i = 0; i < 200 && context.computeOnClient(c -> fi.dy.masa.litematica.schematic.placement.PlacementManagerDaemonHandler.INSTANCE.hasAnyTasks()); i++) {
            context.waitTick();
        }
        context.waitTicks(10);
    }

    /**
     * 两张截图的差别：{颜色差总量 / 1000（每个像素 RGB 三个通道差的和，忽略小于 6 的噪点）, 明显变化的像素数（差 > 24）}
     */
    public static long[] diff(Path a, Path b) {
        try {
            BufferedImage ia = ImageIO.read(a.toFile()), ib = ImageIO.read(b.toFile());
            long energy = 0, area = 0;
            for (int y = 0; y < ia.getHeight(); y++) {
                for (int x = 0; x < ia.getWidth(); x++) {
                    int p = ia.getRGB(x, y), q = ib.getRGB(x, y);
                    int d = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF))
                            + Math.abs((p & 0xFF) - (q & 0xFF));
                    if (d > 6) energy += d;
                    if (d > 24) area++;
                }
            }
            return new long[]{energy / 1000, area};
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
