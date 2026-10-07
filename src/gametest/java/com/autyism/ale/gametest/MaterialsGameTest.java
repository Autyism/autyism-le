package com.autyism.ale.gametest;

import com.autyism.ale.verifier.ContainerVerifier;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求 9：
 * a) 材料列表的“容器内容物”按钮：汇总所有容器 + 物品展示框 + 盔甲架里的物品；
 * b) 验证器：容器内容与投影不一致的位置被标出；
 * c) 投影里的实体（物品展示框、盔甲架、矿车、船）计入材料列表。
 */
@SuppressWarnings("UnstableApiUsage")
public final class MaterialsGameTest implements FabricClientGameTest {
    private static final BlockPos MIN = new BlockPos(50, 64, 0);
    private static final BlockPos MAX = new BlockPos(58, 66, 4);
    private static final BlockPos CHEST = new BlockPos(51, 64, 1);
    private static final BlockPos BARREL = new BlockPos(53, 64, 1);
    private static final BlockPos WALL = new BlockPos(55, 65, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("materials")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 44, -6, 64, 10, 72);
            sp.getServer().runCommand("tp @a 54.5 64 7.5 180 20");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 54.5) < 0.01, 200);
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
                    chest.setItem(0, new ItemStack(Items.STONE, 64));
                    chest.setItem(5, new ItemStack(Items.DIAMOND, 1));
                    ItemStack shulker = new ItemStack(Items.WHITE_SHULKER_BOX);
                    NonNullList<ItemStack> inside = NonNullList.withSize(27, ItemStack.EMPTY);
                    inside.set(0, new ItemStack(Items.IRON_INGOT, 5));
                    shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
                    chest.setItem(9, shulker);
                }
                level.setBlockAndUpdate(BARREL, Blocks.BARREL.defaultBlockState());
                if (level.getBlockEntity(BARREL) instanceof BarrelBlockEntity barrel) barrel.setItem(0, new ItemStack(Items.APPLE, 10));
                level.setBlockAndUpdate(WALL, Blocks.STONE.defaultBlockState());
                ItemFrame frame = new ItemFrame(level, WALL.south(), Direction.SOUTH);
                frame.setItem(new ItemStack(Items.DIAMOND_SWORD));
                level.addFreshEntity(frame);
                ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
                stand.setPos(57.5, 64, 2.5);
                stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                level.addFreshEntity(stand);
                var cart = EntityType.MINECART.create(level, EntitySpawnReason.COMMAND);
                cart.setPos(51.5, 65, 3.5);
                level.addFreshEntity(cart);
                var boat = EntityType.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
                boat.setPos(56.5, 64, 3.5);
                level.addFreshEntity(boat);
            });
            context.waitTicks(20);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_materials");
            GT.waitSchematicBlock(context, CHEST, Blocks.CHEST);
            context.waitTicks(20);

            // c) 实体计入材料列表
            Map<Item, Integer> totals = context.computeOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                Map<Item, Integer> m = new HashMap<>();
                for (MaterialListEntry e : list.getMaterialsAll()) m.merge(e.getStack().getItem(), e.getCountTotal(), Integer::sum);
                return m;
            });
            for (Item want : List.of(Items.ITEM_FRAME, Items.ARMOR_STAND, Items.MINECART, Items.OAK_BOAT)) {
                if (totals.getOrDefault(want, 0) != 1) throw new AssertionError("[materials] entity item " + want + " count " + totals.get(want) + " in " + totals);
            }
            GT.log("[materials] entities in material list OK: item frame, armor stand, minecart, oak boat");

            // a) “容器内容物”按钮
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(5);
            GT.shot(context, "ale-material-list-entities");
            GT.clickButton(context, "Contents");
            context.waitTicks(5);
            GT.shot(context, "ale-container-contents");
            Map<Item, Integer> contents = context.computeOnClient(c -> {
                if (!(c.screen instanceof GuiMaterialList g)) throw new AssertionError("contents screen not open: " + c.screen);
                MaterialListBase list = g.getMaterialList();
                Map<Item, Integer> m = new HashMap<>();
                for (MaterialListEntry e : list.getMaterialsAll()) m.merge(e.getStack().getItem(), e.getCountTotal(), Integer::sum);
                return m;
            });
            Map<Item, Integer> expected = Map.of(Items.STONE, 64, Items.DIAMOND, 1, Items.WHITE_SHULKER_BOX, 1, Items.IRON_INGOT, 5,
                    Items.APPLE, 10, Items.DIAMOND_SWORD, 1, Items.IRON_HELMET, 1);
            for (var e : expected.entrySet()) {
                if (!e.getValue().equals(contents.get(e.getKey()))) throw new AssertionError("[materials] contents " + e.getKey() + "=" + contents.get(e.getKey()) + " in " + contents);
            }
            GT.log("[materials] container contents OK: " + contents);
            context.runOnClient(c -> c.setScreen(null));

            // b) 验证器：先完全一致 → 0；改掉桶里的东西 → 标出 1 处
            int before = verify(context, placement);
            if (before != 0) throw new AssertionError("[materials] verifier flagged " + before + " containers on an identical build");
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(BARREL) instanceof BarrelBlockEntity barrel) barrel.setItem(0, new ItemStack(Items.APPLE, 3));
            });
            context.waitTicks(5);
            int after = verify(context, placement);
            boolean flaggedBarrel = context.computeOnClient(c -> ContainerVerifier.getMismatches(placement.getSchematicVerifier()).contains(BARREL));
            if (after != 1 || !flaggedBarrel) throw new AssertionError("[materials] verifier container mismatches=" + after + " barrelFlagged=" + flaggedBarrel);
            GT.shot(context, "ale-verifier-container");
            // 补好内容 → 标记自动消失
            sp.getServer().runOnServer(s -> {
                if (s.overworld().getBlockEntity(BARREL) instanceof BarrelBlockEntity barrel) barrel.setItem(0, new ItemStack(Items.APPLE, 10));
            });
            context.waitTicks(60);
            int fixed = context.computeOnClient(c -> ContainerVerifier.countMismatches(placement.getSchematicVerifier()));
            if (fixed != 0) throw new AssertionError("[materials] mismatch not cleared after fixing the barrel: " + fixed);
            GT.log("[materials] verifier OK: identical=0, changed barrel flagged, cleared after fixing");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> c.setScreen(null));
        }
    }

    private static int verify(ClientGameTestContext context, SchematicPlacement placement) {
        context.runOnClient(c -> placement.getSchematicVerifier().startVerification(c.level, SchematicWorldHandler.getSchematicWorld(), placement, () -> {
        }));
        context.waitFor(c -> placement.getSchematicVerifier().isFinished(), 400);
        // 单人世界的容器内容是异步从内置服务端读取的，等几 tick 让挂起的位置结算
        context.waitTicks(10);
        return context.computeOnClient(c -> ContainerVerifier.countMismatches(placement.getSchematicVerifier()));
    }
}
