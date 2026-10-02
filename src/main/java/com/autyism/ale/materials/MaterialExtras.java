package com.autyism.ale.materials;

import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.ItemType;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 需求 9：材料列表的额外统计。
 * <ul>
 *     <li>投影实体（物品展示框、矿车、盔甲架、船、画、末影水晶……）折算成物品，计入主材料列表；</li>
 *     <li>容器内容物：投影里所有容器（箱子、潜影盒、木桶、漏斗、发射器……）以及物品展示框里的物品、盔甲架装备、
 *     运输矿车里的东西，汇总成一个单独的列表。容器里的潜影盒物品会额外展开计入它装的东西。</li>
 * </ul>
 */
public final class MaterialExtras {
    private MaterialExtras() {
    }

    // ------------------------------------------------------------------ 实体 → 物品

    /** 实体对应的放置物品（与实体类型 id 同名的物品，例如 item_frame、minecart、oak_boat）；生物等返回 null */
    @Nullable
    public static Item itemForEntity(Entity entity) {
        if (entity instanceof Mob) return null;
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isPresent() && item.get() != Items.AIR) return item.get();
        if (entity.getType() == net.minecraft.world.entity.EntityType.LEASH_KNOT) return Items.LEAD;
        return null;
    }

    @Nullable
    private static Entity createEntity(CompoundData nbt) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;
        try {
            return fi.dy.masa.litematica.util.EntityUtils.createEntityAndPassengersFromData(nbt, level);
        } catch (Exception e) {
            return null;
        }
    }

    /** 投影中所有实体折算的物品数量 */
    public static Object2IntOpenHashMap<Item> entityItems(LitematicaSchematic schematic, Collection<String> regions) {
        Object2IntOpenHashMap<Item> counts = new Object2IntOpenHashMap<>();
        for (String region : regions) {
            List<LitematicaSchematic.EntityInfo> list = schematic.getEntityListForRegion(region);
            if (list == null) continue;
            for (LitematicaSchematic.EntityInfo info : list) {
                Entity e = createEntity(info.nbt());
                if (e == null) continue;
                Item item = itemForEntity(e);
                if (item != null) counts.addTo(item, 1);
                for (Entity passenger : e.getPassengers()) {
                    Item p = itemForEntity(passenger);
                    if (p != null) counts.addTo(p, 1);
                }
            }
        }
        return counts;
    }

    /**
     * 投影放置中“已存在于世界”的实体数量（按物品统计）：用投影世界里已载入的实体，
     * 找世界中 1 格内同类型的实体。
     */
    public static Object2IntOpenHashMap<Item> presentEntityItems(SchematicPlacement placement) {
        Object2IntOpenHashMap<Item> present = new Object2IntOpenHashMap<>();
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        Level client = Minecraft.getInstance().level;
        if (ws == null || client == null) return present;
        Set<Entity> used = new HashSet<>();
        for (Box box : placement.getSubRegionBoxes(SubRegionPlacement.RequiredEnabled.PLACEMENT_ENABLED).values()) {
            if (box.getPos1() == null || box.getPos2() == null) continue;
            AABB aabb = new AABB(Vec(box.getPos1()), Vec(box.getPos2())).inflate(1);
            for (Entity se : ws.getEntities((Entity) null, aabb, e -> true)) {
                Item item = itemForEntity(se);
                if (item == null) continue;
                for (Entity ce : client.getEntities(se, se.getBoundingBox().inflate(1.0), c -> c.getType() == se.getType() && !used.contains(c))) {
                    used.add(ce);
                    present.addTo(item, 1);
                    break;
                }
            }
        }
        return present;
    }

    private static net.minecraft.world.phys.Vec3 Vec(net.minecraft.core.BlockPos p) {
        return new net.minecraft.world.phys.Vec3(p.getX(), p.getY(), p.getZ());
    }

    /** 把实体物品追加到材料列表条目里（同种物品合并） */
    public static List<MaterialListEntry> withEntities(List<MaterialListEntry> list, Object2IntOpenHashMap<Item> total,
                                                       @Nullable Object2IntOpenHashMap<Item> present) {
        if (total.isEmpty()) return list;
        Object2IntOpenHashMap<ItemType> inventory = Minecraft.getInstance().player != null
                ? MaterialListUtils.getInventoryItemCounts(Minecraft.getInstance().player.getInventory()) : new Object2IntOpenHashMap<>();
        List<MaterialListEntry> out = new ArrayList<>(list);
        for (var e : total.object2IntEntrySet()) {
            Item item = e.getKey();
            int count = e.getIntValue();
            int missing = Math.max(0, count - (present != null ? present.getInt(item) : 0));
            ItemStack stack = new ItemStack(item);
            int index = -1;
            for (int i = 0; i < out.size(); i++) {
                if (ItemStack.isSameItemSameComponents(out.get(i).getStack(), stack)) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                MaterialListEntry old = out.get(index);
                out.set(index, new MaterialListEntry(old.getStack(), old.getCountTotal() + count, old.getCountMissing() + missing,
                        old.getCountMismatched(), old.getCountAvailable()));
            } else {
                out.add(new MaterialListEntry(stack, count, missing, 0, inventory.getInt(new ItemType(stack, true, false))));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 容器内容物

    /** 投影中所有容器（含实体容器、物品展示框、盔甲架装备）里的物品汇总 */
    public static Map<ItemType, Integer> containerContents(LitematicaSchematic schematic, Collection<String> regions) {
        Object2IntOpenHashMap<ItemType> counts = new Object2IntOpenHashMap<>();
        var registry = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : null;
        if (registry == null) return new HashMap<>();
        for (String region : regions) {
            Map<net.minecraft.core.BlockPos, CompoundData> bes = schematic.getBlockEntityMapForRegion(region);
            if (bes != null) {
                for (CompoundData data : bes.values()) {
                    Container inv = fi.dy.masa.malilib.util.InventoryUtils.getDataInventory(data, -1, registry);
                    if (inv != null) addContainer(inv, counts);
                }
            }
            List<LitematicaSchematic.EntityInfo> entities = schematic.getEntityListForRegion(region);
            if (entities != null) {
                for (LitematicaSchematic.EntityInfo info : entities) {
                    Entity e = createEntity(info.nbt());
                    if (e == null) continue;
                    if (e instanceof ItemFrame frame) {
                        add(frame.getItem(), counts);
                    } else if (e instanceof ArmorStand stand) {
                        for (EquipmentSlot slot : EquipmentSlot.values()) add(stand.getItemBySlot(slot), counts);
                    } else if (e instanceof Container c) {
                        addContainer(c, counts);
                    }
                }
            }
        }
        return new HashMap<>(counts);
    }

    private static void addContainer(Container inv, Object2IntOpenHashMap<ItemType> counts) {
        for (int i = 0; i < inv.getContainerSize(); i++) add(inv.getItem(i), counts);
    }

    private static void add(ItemStack stack, Object2IntOpenHashMap<ItemType> counts) {
        if (stack == null || stack.isEmpty()) return;
        counts.addTo(new ItemType(stack.copyWithCount(1), true, true), stack.getCount());
        // 容器里的潜影盒等：额外展开它装着的东西
        ItemContainerContents nested = stack.get(DataComponents.CONTAINER);
        if (nested != null) {
            for (ItemStack inner : nested.nonEmptyItemsCopy()) {
                counts.addTo(new ItemType(inner.copyWithCount(1), true, true), inner.getCount() * stack.getCount());
            }
        }
    }
}
