package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.render.EntityInfoOverlay;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * 需求 5a：看向投影中的流体 / 实体时也显示“投影 vs 世界”对比框（按住 Litematica 的信息显示热键）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class InfoOverlayGameTest implements FabricClientGameTest {
    private static final BlockPos WATER = new BlockPos(132, 64, 0);
    private static final BlockPos WALL = new BlockPos(136, 65, -1);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("info")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 126, -6, 142, 8, 72);
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 134.5 64 4.5");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 134.5) < 0.01, 200);
            context.waitTicks(20);
            context.runOnClient(c -> {
                GT.setGuiHidden(c, false);
                Configs.InfoOverlays.BLOCK_INFO_OVERLAY_ENABLED.setBooleanValue(true);
            });
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int x = 130; x <= 134; x++) for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(new BlockPos(x, 63, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(WATER, Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(WALL, Blocks.STONE.defaultBlockState());
                ItemFrame frame = new ItemFrame(level, WALL.south(), Direction.SOUTH);
                frame.setItem(new ItemStack(Items.DIAMOND));
                level.addFreshEntity(frame);
            });
            context.waitTicks(10);
            GT.log("[info] dbg server frames=" + sp.getServer().computeOnServer(sv -> {
                var all = sv.overworld().getEntities((Entity) null, new AABB(130, 64, -1, 138, 67, 2), e -> true);
                StringBuilder sb = new StringBuilder(String.valueOf(all.size()));
                for (Entity e : all) sb.append(" ").append(e.getType().toShortString()).append("@").append(e.position());
                return sb.toString();
            }));
            var placement = GT.captureAndPlace(context, sp, new BlockPos(130, 64, -1), new BlockPos(137, 66, 1), new BlockPos(130, 64, -1), "ale_info");
            GT.log("[info] dbg schematic entity infos=" + context.computeOnClient(c -> {
                var sch = placement.getSchematic();
                int n = 0;
                for (String r : sch.getAreas().keySet()) n += sch.getEntityListForRegion(r).size();
                return n + " ignoreEntities=" + placement.ignoreEntities();
            }));
            // 世界：没有水；展示框里换成金锭
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlockAndUpdate(WATER, Blocks.AIR.defaultBlockState());
                for (Entity e : level.getEntities((Entity) null, new AABB(WALL).inflate(2), e -> e instanceof ItemFrame)) {
                    ((ItemFrame) e).setItem(new ItemStack(Items.GOLD_INGOT));
                }
            });
            context.waitTicks(20);
            List<Integer> keys = context.computeOnClient(c -> Hotkeys.RENDER_INFO_OVERLAY.getKeybind().getKeys());
            GTFilter.clientBestWorld = true;

            // 1) 流体：看向投影里的水源
            look(sp, context, 132.5, 65.6, 3.5, WATER);
            int fluid = withKeyDiff(context, keys, "fluid");
            // 2) 实体：看向展示框
            look(sp, context, 136.5, 64.0, 3.0, WALL.south());
            context.waitTicks(5);
            GT.log("[info] dbg " + context.computeOnClient(c -> {
                var ws = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
                var all = com.autyism.ale.materials.MaterialExtras.schematicEntities(ws, new AABB(126, 60, -6, 142, 72, 8));
                StringBuilder sb = new StringBuilder("schematic entities=" + all.size() + " count=" + ws.getRegularEntityCount() + " debug=" + ws.getEntityDebug()
                        + " chunk8,-1=" + ws.getEntitiesByChunk(8, -1, e -> true).size() + " chunk8,0=" + ws.getEntitiesByChunk(8, 0, e -> true).size()
                        + " clientFrames=" + c.level.getEntities((Entity) null, new AABB(126, 60, -6, 142, 72, 8), e -> e instanceof ItemFrame).size());
                for (Entity e : all) sb.append(" ").append(e.getType().toShortString()).append("@").append(e.position()).append(" bb=").append(e.getBoundingBox());
                sb.append(" eye=").append(c.player.getEyePosition()).append(" look=").append(c.player.getViewVector(1f));
                return sb.toString();
            }));
            boolean targeted = context.computeOnClient(c -> EntityInfoOverlay.findTargetedSchematicEntity() instanceof ItemFrame);
            int entity = withKeyDiff(context, keys, "entity");
            GT.log("[info] fluid box diff=" + fluid + " entity targeted=" + targeted + " entity box diff=" + entity);
            if (fluid < 1500) throw new AssertionError("[info] no info box for the schematic fluid (diff " + fluid + ")");
            if (!targeted || entity < 1500) throw new AssertionError("[info] no info box for the schematic entity (targeted=" + targeted + ", diff " + entity + ")");
            // 关掉功能：实体不再显示
            context.runOnClient(c -> AleConfigs.Generic.INFO_FLUIDS_ENTITIES.setBooleanValue(false));
            int entityOff = withKeyDiff(context, keys, "entity-off");
            context.runOnClient(c -> AleConfigs.Generic.INFO_FLUIDS_ENTITIES.setBooleanValue(true));
            GT.log("[info] entity box diff with feature off=" + entityOff);
            GT.log("[info] OK: fluid and entity comparison boxes shown");
        } finally {
            GTFilter.clientBestWorld = false;
            GT.removeAllPlacements(context);
        }
    }

    private static void look(TestSingleplayerContext sp, ClientGameTestContext context, double x, double y, double z, BlockPos target) {
        double dx = target.getX() + 0.5 - x, dy = target.getY() + 0.5 - (y + 1.62), dz = target.getZ() + 0.5 - z;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f %.2f %.2f", x, y, z, yaw, pitch));
        context.waitTicks(10);
    }

    private static int withKeyDiff(ClientGameTestContext context, List<Integer> keys, String name) {
        GT.log("[info] " + name + ": screenshot without key, keys=" + keys);
        Path without = context.takeScreenshot("ale-info-" + name + "-nokey");
        for (int k : keys) context.getInput().holdKey(k);
        GT.log("[info] " + name + ": keys held");
        context.waitTicks(3);
        Path with = context.takeScreenshot("ale-info-" + name);
        GT.log("[info] " + name + ": screenshot with key");
        for (int k : keys) context.getInput().releaseKey(k);
        context.waitTicks(2);
        return diff(without, with);
    }

    private static int diff(Path a, Path b) {
        try {
            BufferedImage ia = ImageIO.read(a.toFile()), ib = ImageIO.read(b.toFile());
            int n = 0;
            for (int y = 0; y < ia.getHeight(); y++)
                for (int x = 0; x < ia.getWidth(); x++) {
                    int p = ia.getRGB(x, y), q = ib.getRGB(x, y);
                    int d = Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) + Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF)) + Math.abs((p & 0xFF) - (q & 0xFF));
                    if (d > 40) n++;
                }
            return n;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
