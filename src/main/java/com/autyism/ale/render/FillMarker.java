package com.autyism.ale.render;

import com.autyism.ale.config.AleConfigs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * “需要填充”标记：投影里这个容器（漏斗、投掷器、发射器、箱子、木桶、潜影盒、熔炉、酿造台、合成器……）装着东西时，
 * 在方块每一面画一个绿色的 “F”（Fill），一眼就能看出哪些容器要往里放东西，不用一个个打开看。
 * 只看投影里的内容，不需要世界里的数据；结果和 Litematica 的覆盖层一起存在区块的渲染数据里，不是每帧计算。
 * 可以设置跳过只装着列表里物品的容器（默认列表是各色潜影盒，打包机下面一排漏斗常常都装着空潜影盒）。
 */
public final class FillMarker {
    private FillMarker() {
    }

    /** “F” 的笔画（面内坐标 u 向右、v 向上，0..1）：左边竖线、顶上一横、中间一横 */
    public static final float[][] STROKES = {
            {0.31f, 0.14f, 0.31f, 0.86f},
            {0.31f, 0.86f, 0.76f, 0.86f},
            {0.31f, 0.52f, 0.66f, 0.52f},
    };

    /** 跳过列表解析后的缓存（列表改了才重新解析；渲染线程和区块重建线程都会读） */
    private static volatile List<String> cachedList = List.of();
    private static volatile Set<Item> cachedItems = Set.of();

    /** 投影里的这个方块实体是装着东西、要提醒填充的容器 */
    public static boolean needsFilling(BlockEntity be) {
        if (!(be instanceof Container container) || container.isEmpty()) return false;
        if (!AleConfigs.Generic.FILL_MARKER_SKIP.getBooleanValue()) return true;
        Set<Item> skip = skipItems();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && !skip.contains(stack.getItem())) return true;
        }
        return false;
    }

    private static Set<Item> skipItems() {
        List<String> list = AleConfigs.Generic.FILL_MARKER_SKIP_ITEMS.getStrings();
        if (!list.equals(cachedList)) {
            Set<Item> items = new HashSet<>();
            for (String s : list) {
                Identifier id = Identifier.tryParse(s.trim());
                if (id != null) BuiltInRegistries.ITEM.getOptional(id).ifPresent(items::add);
            }
            cachedItems = items;
            cachedList = List.copyOf(list);
        }
        return cachedItems;
    }

    /** 默认跳过的物品：各色潜影盒 */
    public static final List<String> DEFAULT_SKIP = List.of(
            "minecraft:shulker_box", "minecraft:white_shulker_box", "minecraft:orange_shulker_box", "minecraft:magenta_shulker_box",
            "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box", "minecraft:lime_shulker_box", "minecraft:pink_shulker_box",
            "minecraft:gray_shulker_box", "minecraft:light_gray_shulker_box", "minecraft:cyan_shulker_box", "minecraft:purple_shulker_box",
            "minecraft:blue_shulker_box", "minecraft:brown_shulker_box", "minecraft:green_shulker_box", "minecraft:red_shulker_box",
            "minecraft:black_shulker_box");
}
