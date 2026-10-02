package com.autyism.ale.schematic;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 需求 10：把告示牌换成另一种木材时，每块告示牌保持原有形态（立式/挂墙/悬挂/挂墙悬挂），
 * 所有方块状态（旋转、朝向、含水、ATTACHED…）原样复制；告示牌文字存在方块实体 NBT 里、不随方块改变，
 * 同形态的方块实体类型一致（sign / hanging_sign），所以文字正反两面都会保留。
 */
public final class SignReplaceUtils {
    private SignReplaceUtils() {
    }

    /** 告示牌形态 */
    public enum Form {
        STANDING, WALL, HANGING, WALL_HANGING;

        @Nullable
        public static Form of(Block block) {
            if (block instanceof WallHangingSignBlock) return WALL_HANGING;
            if (block instanceof CeilingHangingSignBlock) return HANGING;
            if (block instanceof WallSignBlock) return WALL;
            if (block instanceof StandingSignBlock) return STANDING;
            return null;
        }

        /** 同一个物品对应的形态组：普通告示牌（立式+挂墙），悬挂式告示牌（悬挂+挂墙悬挂） */
        public boolean sameItemFamily(Form other) {
            boolean hanging = this == HANGING || this == WALL_HANGING;
            boolean otherHanging = other == HANGING || other == WALL_HANGING;
            return hanging == otherHanging;
        }
    }

    public static boolean isSign(Block block) {
        return block instanceof SignBlock && Form.of(block) != null;
    }

    @Nullable
    public static Block find(WoodType wood, Form form) {
        for (Block b : BuiltInRegistries.BLOCK) {
            if (b instanceof SignBlock sign && sign.type() == wood && Form.of(b) == form) return b;
        }
        return null;
    }

    /**
     * 把投影中 oldBlock 所属的告示牌（与它同一物品的两种形态）换成 newBlock 的木材，形态不变。
     *
     * @return 替换的方块数
     */
    public static int replace(Block oldBlock, Block newBlock, LitematicaSchematic schematic, Collection<String> regions) {
        Form oldForm = Form.of(oldBlock);
        if (oldForm == null || !(newBlock instanceof SignBlock newSign) || !(oldBlock instanceof SignBlock oldSign)) return 0;
        WoodType fromWood = oldSign.type();
        WoodType toWood = newSign.type();

        // 来源方块 → 目标方块（只处理与被替换物品同一组的形态）
        Map<Block, Block> mapping = new HashMap<>();
        for (Form form : Form.values()) {
            if (!form.sameItemFamily(oldForm)) continue;
            Block from = find(fromWood, form);
            Block to = find(toWood, form);
            if (from != null && to != null && from != to) mapping.put(from, to);
        }
        if (mapping.isEmpty()) return 0;

        int count = 0;
        for (String region : regions) {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);
            if (container == null) continue;
            Vec3i size = container.getSize();
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        BlockState old = container.get(x, y, z);
                        Block target = mapping.get(old.getBlock());
                        if (target == null) continue;
                        container.set(x, y, z, copyProperties(old, target.defaultBlockState()));
                        count++;
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

    public static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) to = copy(from, to, property);
        }
        return to;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }
}
