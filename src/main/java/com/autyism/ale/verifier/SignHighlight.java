package com.autyism.ale.verifier;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 验证器界面的“高亮告示牌”：选中的投影范围里有字的告示牌透墙标出来。很多投影在要填充的地方放了写着提示的告示牌，不用到处找。
 * <p>
 * 世界里已经放了的（世界里的告示牌有字，或者投影里这里是有字的告示牌、世界里已经放了告示牌）用亮绿色；
 * 投影里有、世界里还没放的用深绿色。颜色在设置里能改。
 * <p>
 * 只看方块实体（每个区块只有几个），开着时每秒重新收集一次，所以放下、敲掉告示牌一秒内就跟上。
 */
public final class SignHighlight {
    /** 验证器界面上的开关（这次进游戏有效） */
    public static boolean enabled;

    private static final int REFRESH_TICKS = 20;
    private static final int MAX_CHUNKS = 4096;

    private static volatile List<BlockPos> placed = List.of();
    private static volatile List<BlockPos> missing = List.of();
    private static int sinceRefresh = REFRESH_TICKS;

    private SignHighlight() {
    }

    public static List<BlockPos> placed() {
        return placed;
    }

    public static List<BlockPos> missing() {
        return missing;
    }

    public static void toggle() {
        enabled = !enabled;
        sinceRefresh = REFRESH_TICKS;
        if (!enabled) clear();
    }

    private static void clear() {
        placed = List.of();
        missing = List.of();
    }

    /** 客户端每 tick（ContainerVerifier.tick 里调用） */
    public static void tick() {
        if (!enabled) return;
        if (++sinceRefresh < REFRESH_TICKS) return;
        sinceRefresh = 0;
        Minecraft mc = Minecraft.getInstance();
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (mc.level == null || schematic == null || placement == null) {
            clear();
            return;
        }
        // 投影所有子区域合起来的范围（放置刚建好时 Litematica 的“整体范围”还没算出来，不能用）
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Box box : placement.getSubRegionBoxes(fi.dy.masa.litematica.schematic.placement.SubRegionPlacement.RequiredEnabled.ANY).values()) {
            BlockPos p1 = box.getPos1(), p2 = box.getPos2();
            if (p1 == null || p2 == null) continue;
            minX = Math.min(minX, Math.min(p1.getX(), p2.getX()));
            minY = Math.min(minY, Math.min(p1.getY(), p2.getY()));
            minZ = Math.min(minZ, Math.min(p1.getZ(), p2.getZ()));
            maxX = Math.max(maxX, Math.max(p1.getX(), p2.getX()));
            maxY = Math.max(maxY, Math.max(p1.getY(), p2.getY()));
            maxZ = Math.max(maxZ, Math.max(p1.getZ(), p2.getZ()));
        }
        if (minX > maxX) {
            clear();
            return;
        }
        List<BlockPos> nowPlaced = new ArrayList<>();
        List<BlockPos> nowMissing = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        int chunks = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (++chunks > MAX_CHUNKS) break;
                // 投影里有字的告示牌
                if (schematic.hasChunk(cx, cz)) {
                    // 投影世界的区块可能正被 Litematica 的后台线程载入：复制一份再看
                    List<BlockEntity> list;
                    try {
                        list = new ArrayList<>(schematic.getChunk(cx, cz).getBlockEntities().values());
                    } catch (java.util.ConcurrentModificationException e) {
                        sinceRefresh = REFRESH_TICKS - 2; // 过两 tick 再收集
                        return;
                    }
                    for (BlockEntity be : list) {
                        BlockPos pos = be.getBlockPos();
                        if (!inside(pos, minX, minY, minZ, maxX, maxY, maxZ) || !(be instanceof SignBlockEntity sign) || !hasText(sign)) continue;
                        seen.add(pos);
                        if (mc.level.hasChunk(cx, cz) && mc.level.getBlockState(pos).getBlock() instanceof SignBlock) nowPlaced.add(pos);
                        else nowMissing.add(pos);
                    }
                }
                // 世界里有字的告示牌（投影里没有、或者上面已经算过的不重复）
                if (mc.level.hasChunk(cx, cz)) {
                    for (BlockEntity be : mc.level.getChunk(cx, cz).getBlockEntities().values()) {
                        BlockPos pos = be.getBlockPos();
                        if (seen.contains(pos) || !inside(pos, minX, minY, minZ, maxX, maxY, maxZ)) continue;
                        if (be instanceof SignBlockEntity sign && hasText(sign)) nowPlaced.add(pos);
                    }
                }
            }
        }
        placed = List.copyOf(nowPlaced);
        missing = List.copyOf(nowMissing);
    }

    private static boolean inside(BlockPos p, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    private static boolean hasText(SignBlockEntity sign) {
        return hasText(sign.getFrontText()) || hasText(sign.getBackText());
    }

    private static boolean hasText(SignText text) {
        for (Component line : text.getMessages(false)) {
            if (!line.getString().isBlank()) return true;
        }
        return false;
    }

    public static int placedColor() {
        return AleConfigs.Generic.SIGN_HIGHLIGHT_PLACED_COLOR.getIntegerValue();
    }

    public static int missingColor() {
        return AleConfigs.Generic.SIGN_HIGHLIGHT_MISSING_COLOR.getIntegerValue();
    }

    /** 世界换了：重新收集 */
    public static void onWorldChange(Level level) {
        clear();
        sinceRefresh = REFRESH_TICKS;
    }
}
