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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * 需求 5b：投影里的实体渲染成半透明（与投影方块一样的 ghostBlockAlpha），和真实实体区分开。
 * 世界里没有这些实体（只有投影里有），背后是红色混凝土墙：
 * 半透明截图必须与“没有投影实体”的截图不同（实体可见），也必须与“不透明”截图明显不同（能透出后面的墙）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class TranslucentEntityGameTest implements FabricClientGameTest {
    private static final BlockPos MIN = new BlockPos(110, 64, 0);
    private static final BlockPos MAX = new BlockPos(118, 66, 2);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("entities")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 104, -6, 124, 10, 72);
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 114.5 64.5 7.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 114.5) < 0.01, 200);
            context.runOnClient(c -> GT.setGuiHidden(c, true));
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int x = 104; x <= 124; x++)
                    for (int y = 64; y <= 70; y++) level.setBlockAndUpdate(new BlockPos(x, y, -1), Blocks.RED_CONCRETE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(116, 65, 0), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(110, 64, 1), Blocks.CHEST.defaultBlockState());
                ItemFrame frame = new ItemFrame(level, new BlockPos(116, 65, 1), Direction.SOUTH);
                frame.setItem(new ItemStack(Items.DIAMOND_SWORD));
                level.addFreshEntity(frame);
                ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
                stand.setPos(112.5, 64, 1.5);
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                level.addFreshEntity(stand);
                var cart = EntityType.MINECART.create(level, EntitySpawnReason.COMMAND);
                cart.setPos(114.5, 64, 1.5);
                level.addFreshEntity(cart);
                var boat = EntityType.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
                boat.setPos(117.5, 64, 1.5);
                level.addFreshEntity(boat);
            });
            context.waitTicks(20);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_entities");
            // 世界里删除这些实体，只留投影里的
            sp.getServer().runOnServer(s -> {
                for (Entity e : s.overworld().getEntities((Entity) null, new AABB(104, 60, -6, 124, 80, 10), e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
                    e.discard();
                }
            });
            context.waitTicks(40);

            Path base = shot(context, placement, false, false, "base");
            Path opaque = shot(context, placement, true, false, "opaque");
            Path translucent = shot(context, placement, true, true, "translucent");
            int visible = diff(base, translucent);
            int differsFromOpaque = diff(opaque, translucent);
            int opaqueVisible = diff(base, opaque);
            GT.log("[entities] diff base-vs-opaque=" + opaqueVisible + " base-vs-translucent=" + visible + " opaque-vs-translucent=" + differsFromOpaque);
            if (opaqueVisible < 3000) throw new AssertionError("[entities] schematic entities not rendered at all (" + opaqueVisible + ")");
            if (visible < opaqueVisible / 4) throw new AssertionError("[entities] translucent entities nearly invisible (" + visible + ")");
            if (differsFromOpaque < opaqueVisible / 3) throw new AssertionError("[entities] translucent rendering looks the same as opaque (" + differsFromOpaque + ")");
            GT.log("[entities] OK: schematic entities rendered semi-transparent");
            // 投影方块半透明模式：方块实体（箱子）也跟着半透明，且不报错
            sp.getServer().runOnServer(s -> s.overworld().setBlockAndUpdate(new BlockPos(110, 64, 1), Blocks.AIR.defaultBlockState()));
            context.runOnClient(c -> Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(false));
            Path beOpaque = shot(context, placement, true, true, "be-opaque");
            context.runOnClient(c -> Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(true));
            Path beTranslucent = shot(context, placement, true, true, "be-translucent");
            context.runOnClient(c -> Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.setBooleanValue(false));
            GT.log("[entities] block entity opaque-vs-translucent diff=" + diff(beOpaque, beTranslucent));
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                GT.setGuiHidden(c, false);
                AleConfigs.Generic.TRANSLUCENT_ENTITIES.setBooleanValue(true);
                Configs.Visuals.RENDER_SCHEMATIC_ENTITIES.setBooleanValue(true);
            });
        }
    }

    private static Path shot(ClientGameTestContext context, SchematicPlacement placement, boolean entities, boolean translucent, String name) {
        context.runOnClient(c -> {
            Configs.Visuals.RENDER_SCHEMATIC_ENTITIES.setBooleanValue(entities);
            AleConfigs.Generic.TRANSLUCENT_ENTITIES.setBooleanValue(translucent);
            fi.dy.masa.litematica.render.LitematicaRenderer.getInstance().updateConfigState();
            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(placement.getSchematic());
        });
        context.waitTicks(20);
        return GT.shot(context, "ale-entities-" + name);
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
