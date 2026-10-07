//? if <1.21.9 {
/*package com.autyism.ale.materials;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.util.ItemType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 1.21.8 及更早的 Litematica 还没有自定义材料列表：“容器内容物”用这个列表显示。
// 每种物品：总数 = 缺少数 = 容器里的数量，“可用”是背包里已有的数量（与新版本 Litematica 的自定义列表一样）
public final class ContainerContentsList extends MaterialListBase {
    private final String name;
    private final Map<ItemType, Integer> items;

    public ContainerContentsList(String name, Map<ItemType, Integer> items) {
        this.name = name;
        this.items = items;
        this.reCreateMaterialList();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getTitle() {
        return this.name;
    }

    @Override
    public void reCreateMaterialList() {
        Minecraft mc = Minecraft.getInstance();
        Object2IntOpenHashMap<ItemType> inventory = mc.player != null
                ? MaterialListUtils.getInventoryItemCounts(mc.player.getInventory()) : new Object2IntOpenHashMap<>();
        List<MaterialListEntry> list = new ArrayList<>();
        for (Map.Entry<ItemType, Integer> e : this.items.entrySet()) {
            int count = e.getValue();
            list.add(new MaterialListEntry(e.getKey().getStack().copy(), count, count, 0, inventory.getInt(e.getKey())));
        }
        this.materialListAll = ImmutableList.copyOf(list);
        this.refreshPreFilteredList();
        this.updateCounts();
    }
}
*///?}
