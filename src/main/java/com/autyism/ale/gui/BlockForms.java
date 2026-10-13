package com.autyism.ale.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同一个物品放出来的几种方块形态（告示牌 / 墙上的告示牌、悬挂告示牌 / 墙上的悬挂告示牌、火把 / 墙上的火把、
 * 旗帜 / 墙上的旗帜、头颅 / 墙上的头颅、珊瑚扇 / 墙上的珊瑚扇……）。选择方块的列表里它们合成一条，
 * 名字用物品的名字；替换时每个方块再换成新方块里对应的那种形态。
 */
public final class BlockForms {
    private static Map<Item, List<Block>> forms;

    private BlockForms() {
    }

    private static synchronized Map<Item, List<Block>> forms() {
        if (forms == null) {
            Map<Item, List<Block>> map = new HashMap<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                Item item = block.asItem();
                if (item != Items.AIR) map.computeIfAbsent(item, i -> new ArrayList<>()).add(block);
            }
            forms = map;
        }
        return forms;
    }

    /** 这个方块的物品能放出来的所有方块（至少包含它自己）；没有物品的方块只有它自己 */
    public static List<Block> formsOf(Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) return List.of(block);
        List<Block> list = forms().get(item);
        return list == null ? List.of(block) : list;
    }

    /** 合成一条时代表这一组的方块：物品默认放出来的那种（立式的告示牌、火把……） */
    public static Block primary(Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) return block;
        Block b = Block.byItem(item);
        return b == Blocks.AIR ? block : b;
    }

    /** 这一组有不止一种形态 */
    public static boolean hasSeveralForms(Block block) {
        return formsOf(block).size() > 1;
    }
}
