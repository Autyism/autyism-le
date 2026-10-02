package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * 需求 12：投影渲染（错误标记、半透明投影方块）透过玻璃/染色玻璃也要可见。
 * 方法：玩家与投影之间隔一面玻璃墙，比较“有投影渲染”和“无投影渲染”两张截图的差异像素数。
 */
@SuppressWarnings("UnstableApiUsage")
public final class GlassGameTest implements FabricClientGameTest {
    private static final BlockPos WRONG = new BlockPos(90, 64, 0);   // 朝向错误 → 黄色错误标记
    private static final BlockPos MISSING = new BlockPos(92, 64, 0); // 世界里没有 → 投影方块 + 缺失标记

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("glass")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 84, -6, 98, 10, 72);
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 91.5 64 6.5 180 15");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 91.5) < 0.01, 200);
            context.runOnClient(c -> c.options.hideGui = true);
            sp.getServer().runOnServer(s -> {
                s.overworld().setBlockAndUpdate(WRONG, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                s.overworld().setBlockAndUpdate(MISSING, Blocks.GOLD_BLOCK.defaultBlockState());
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, WRONG, MISSING, WRONG, "ale_glass");
            sp.getServer().runOnServer(s -> {
                s.overworld().setBlockAndUpdate(WRONG, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                s.overworld().setBlockAndUpdate(MISSING, Blocks.AIR.defaultBlockState());
            });

            for (Block glass : new Block[]{Blocks.GLASS, Blocks.RED_STAINED_GLASS}) {
                sp.getServer().runOnServer(s -> {
                    for (int x = 86; x <= 96; x++)
                        for (int y = 64; y <= 67; y++) s.overworld().setBlockAndUpdate(new BlockPos(x, y, 3), glass.defaultBlockState());
                });
                context.waitTicks(10);
                String name = glass == Blocks.GLASS ? "glass" : "stained";
                // 错误标记（只开标记，不画投影方块）
                int overlayOff = visibleDiff(context, placement, false, true, false, name + "-overlay-off");
                int overlayOn = visibleDiff(context, placement, true, true, false, name + "-overlay-on");
                // 半透明投影方块（只画方块，不画标记）
                int ghostOff = visibleDiff(context, placement, false, false, true, name + "-ghost-off");
                int ghostOn = visibleDiff(context, placement, true, false, true, name + "-ghost-on");
                GT.log("[glass] " + name + ": overlay diff off=" + overlayOff + " on=" + overlayOn + " | translucent ghost diff off=" + ghostOff + " on=" + ghostOn);
                if (overlayOn < 2000) throw new AssertionError("[glass] " + name + ": overlay not visible through glass (diff " + overlayOn + ")");
                if (ghostOn < 2000) throw new AssertionError("[glass] " + name + ": translucent ghost blocks not visible through glass (diff " + ghostOn + ")");
            }
            GT.log("[glass] OK: overlays and translucent schematic blocks visible through glass and stained glass");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                c.options.hideGui = false;
                AleConfigs.Generic.RENDER_THROUGH_GLASS.setBooleanValue(true);
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.setBooleanValue(true);
                Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(false);
            });
        }
    }

    /** 开启指定的投影渲染后，与完全不渲染投影时的截图差异像素数 */
    private static int visibleDiff(ClientGameTestContext context, SchematicPlacement placement, boolean throughGlass,
                                   boolean overlay, boolean ghost, String name) {
        set(context, placement, throughGlass, false, false);
        Path base = context.takeScreenshot("ale-glass-" + name + "-base");
        set(context, placement, throughGlass, overlay, ghost);
        Path with = context.takeScreenshot("ale-glass-" + name);
        return diff(base, with);
    }

    private static void set(ClientGameTestContext context, SchematicPlacement placement, boolean throughGlass, boolean overlay, boolean ghost) {
        context.runOnClient(c -> {
            AleConfigs.Generic.RENDER_THROUGH_GLASS.setBooleanValue(throughGlass);
            Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(overlay);
            Configs.Visuals.ENABLE_SCHEMATIC_BLOCKS.setBooleanValue(ghost);
            Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(ghost);
            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(placement.getSchematic());
            fi.dy.masa.litematica.render.LitematicaRenderer.getInstance().updateConfigState();
        });
        context.waitTicks(30);
    }

    private static int diff(Path a, Path b) {
        try {
            BufferedImage ia = ImageIO.read(a.toFile()), ib = ImageIO.read(b.toFile());
            int n = 0;
            for (int y = 0; y < ia.getHeight(); y++) {
                for (int x = 0; x < ia.getWidth(); x++) {
                    int p = ia.getRGB(x, y), q = ib.getRGB(x, y);
                    int d = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF)) + Math.abs((p & 0xFF) - (q & 0xFF));
                    if (d > 40) n++;
                }
            }
            return n;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
