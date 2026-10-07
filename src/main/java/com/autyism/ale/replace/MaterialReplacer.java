//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.replace;

import com.autyism.ale.AleMod;
import com.autyism.ale.mixin.litematica.MaterialListPlacementAccessor;
import com.autyism.ale.mixin.litematica.MaterialListSchematicAccessor;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 材料列表“替换”的实际修改：把投影（所有区域）里属于某个材料条目的方块全部换成选中的方块。
 * <ul>
 *     <li>同名的方块状态属性（朝向、上下半、含水、旋转……）照抄；</li>
 *     <li>同一个物品有几种形态（立式 / 挂墙的告示牌、火把、旗帜、头颅……）时，每个方块换成新方块里对应的那种形态；</li>
 *     <li>方块实体数据只在新旧方块的方块实体类型相同时保留（例如告示牌换木材，文字还在），否则去掉；</li>
 *     <li>投影标记为已修改（可以用“保存修改”存回文件），所有使用它的放置重新渲染。</li>
 * </ul>
 */
public final class MaterialReplacer {
    private MaterialReplacer() {
    }

    /** 材料列表对应的投影和区域；不是投影 / 放置的列表时返回 null */
    @Nullable
    public static Target targetOf(MaterialListBase list) {
        if (list instanceof MaterialListSchematic mls) {
            return new Target(((MaterialListSchematicAccessor) mls).ale$getSchematic(), ((MaterialListSchematicAccessor) mls).ale$getRegions(), null);
        }
        if (list instanceof MaterialListPlacement mlp) {
            SchematicPlacement placement = ((MaterialListPlacementAccessor) mlp).ale$getPlacement();
            LitematicaSchematic schematic = placement.getSchematic();
            return new Target(schematic, schematic.getAreas().keySet(), placement);
        }
        return null;
    }

    public record Target(LitematicaSchematic schematic, Collection<String> regions, @Nullable SchematicPlacement placement) {
    }

    /** 材料条目的物品对应的方块（选择界面里默认选中它）；没有对应方块时为 null */
    @Nullable
    public static Block blockOf(ItemStack stack) {
        Block block = Block.byItem(stack.getItem());
        return block == net.minecraft.world.level.block.Blocks.AIR ? null : block;
    }

    /**
     * 把 target 投影里材料物品为 item 的方块全部换成 newBlock。
     *
     * @return 换掉的方块数
     */
    public static int replace(Target target, ItemStack item, Block newBlock) {
        LitematicaSchematic schematic = target.schematic();
        MaterialCache cache = MaterialCache.getInstance();
        Map<BlockState, Optional<BlockState>> mapping = new IdentityHashMap<>();
        int count = 0;
        for (String region : target.regions()) {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);
            if (container == null) continue;
            Map<BlockPos, CompoundData> blockEntities = schematic.getBlockEntityMapForRegion(region);
            Vec3i size = container.getSize();
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        BlockState old = container.get(x, y, z);
                        if (old.isAir()) continue;
                        Optional<BlockState> replacement = mapping.computeIfAbsent(old, s -> {
                            ItemStack required = cache.getRequiredBuildItemForState(s);
                            if (required == null || required.isEmpty() || !ItemStack.isSameItem(required, item)) return Optional.empty();
                            BlockState converted = convert(s, newBlock);
                            // 换成同一个方块（同样的状态）什么都不变，不算进数量
                            return converted == s ? Optional.empty() : Optional.of(converted);
                        });
                        if (replacement.isEmpty()) continue;
                        BlockState now = replacement.get();
                        container.set(x, y, z, now);
                        count++;
                        if (blockEntities != null && !keepsBlockEntityData(old, now)) blockEntities.remove(new BlockPos(x, y, z));
                    }
                }
            }
        }
        if (count > 0) {
            SchematicMetadata metadata = schematic.getMetadata();
            metadata.setTimeModifiedToNow();
            metadata.setModifiedSinceSaved();
            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(schematic);
        }
        return count;
    }

    /** 新方块里与旧方块形态最接近的那一种，再照抄同名的属性 */
    static BlockState convert(BlockState from, Block newBlock) {
        Block form = formOf(from, newBlock);
        BlockState to = form.defaultBlockState();
        for (Property<?> p : from.getProperties()) {
            Property<?> q = form.getStateDefinition().getProperty(p.getName());
            if (q != null) to = copyValue(from, p, to, q);
        }
        return to;
    }

    private static <A extends Comparable<A>, B extends Comparable<B>> BlockState copyValue(BlockState from, Property<A> p, BlockState to, Property<B> q) {
        String value = p.getName(from.getValue(p));
        Optional<B> parsed = q.getValue(value);
        return parsed.isPresent() ? to.setValue(q, parsed.get()) : to;
    }

    /**
     * 同一个物品放出来的几种方块（例如 spruce_sign / spruce_wall_sign）里，挑属性名和旧方块重合最多的；
     * 一样多时用选中的那个方块本身。
     */
    static Block formOf(BlockState from, Block chosen) {
        Item item = chosen.asItem();
        if (item == Items.AIR) return chosen;
        List<Block> forms = FORMS.computeIfAbsent(item, MaterialReplacer::blocksOf);
        if (forms.size() <= 1) return chosen;
        Block best = chosen;
        int bestScore = sharedNames(from, chosen);
        for (Block b : forms) {
            int score = sharedNames(from, b);
            if (score > bestScore) {
                best = b;
                bestScore = score;
            }
        }
        return best;
    }

    private static final Map<Item, List<Block>> FORMS = new HashMap<>();

    private static List<Block> blocksOf(Item item) {
        List<Block> list = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) if (b.asItem() == item) list.add(b);
        return list;
    }

    private static int sharedNames(BlockState from, Block to) {
        int n = 0;
        for (Property<?> p : from.getProperties()) if (to.getStateDefinition().getProperty(p.getName()) != null) n++;
        // 属性名都对上、而且属性个数也一样的最像
        if (n == from.getProperties().size() && n == to.getStateDefinition().getProperties().size()) n++;
        return n;
    }

    private static final Map<BlockState, Map<BlockState, Boolean>> KEEP_DATA = new IdentityHashMap<>();

    /** 旧方块的方块实体数据能不能留给新方块（同一种方块实体才留） */
    static boolean keepsBlockEntityData(BlockState old, BlockState now) {
        return KEEP_DATA.computeIfAbsent(old, k -> new IdentityHashMap<>()).computeIfAbsent(now, n -> {
            BlockEntityType<?> a = typeOf(old), b = typeOf(n);
            return a != null && a == b;
        });
    }

    @Nullable
    private static BlockEntityType<?> typeOf(BlockState state) {
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock eb)) return null;
        try {
            BlockEntity be = eb.newBlockEntity(BlockPos.ZERO, state);
            return be != null ? be.getType() : null;
        } catch (Throwable t) {
            AleMod.LOGGER.debug("Could not check the block entity type of {}", state, t);
            return null;
        }
    }
}
//?}
