package com.autyism.ale.verifier;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 不开验证器也一直检查附近投影里的容器：世界里这一格是同一种容器时，比较每一格的内容（ContainerVerifier.sameContents）。
 * <ul>
 * <li>一样：记下“确认一样”（F 不再画）；</li>
 * <li>世界里还是空的、投影里有东西：交给 F（不另外框）；</li>
 * <li>别的（放错、多了、少了、放错格子、合成器禁用的格子不同）：内容不对，隔着墙框出来。</li>
 * </ul>
 * 多人游戏里内容从服务端要（Servux），所以只查已经载入、世界里方块对上的容器，每 tick 只查几个，查过的过一会儿再查。
 * 范围是客户端载入的区块（以前固定 64 格，远一点的 F 不会自己消失）。
 */
public final class ContainerCheck {
    private static final Minecraft mc = Minecraft.getInstance();
    /** 每 20 tick 重新收集一次附近投影里的容器 */
    private static final int COLLECT_INTERVAL = 20;
    /** 同一个容器多久重新查一次（tick） */
    private static final int RECHECK_TICKS = 60;
    private static final int CHECKS_PER_TICK_REMOTE = 8;
    private static final int CHECKS_PER_TICK_LOCAL = 32;
    private static final int MAX_CONTAINERS = 20000;

    /** 内容不对的容器（主线程写，渲染线程读） */
    private static final Set<Long> WRONG = ConcurrentHashMap.newKeySet();
    private static volatile List<BlockPos> wrongList = List.of();
    /** 每个附近的容器下次可以查的时间 */
    private static final Map<Long, Long> NEXT = new HashMap<>();
    private static List<BlockPos> queue = List.of();
    private static int cursor;
    private static long ticks;

    private ContainerCheck() {
    }

    public static List<BlockPos> wrong() {
        return wrongList;
    }

    public static boolean isWrong(BlockPos pos) {
        return WRONG.contains(pos.asLong());
    }

    public static void clear() {
        WRONG.clear();
        wrongList = List.of();
        NEXT.clear();
        queue = List.of();
        cursor = 0;
    }

    /** 客户端每 tick（ContainerVerifier.tick 里调用） */
    public static void tick(WorldSchematic ws) {
        ticks++;
        if (!AleConfigs.Generic.CONTAINER_CHECK.getBooleanValue() || ws == null || mc.level == null || mc.player == null) {
            if (!WRONG.isEmpty()) clear();
            return;
        }
        boolean local = mc.getSingleplayerServer() != null;
        if (!local && !ServerContainerData.available()) return;
        if (ticks % COLLECT_INTERVAL == 0) collect(ws);
        int budget = local ? CHECKS_PER_TICK_LOCAL : CHECKS_PER_TICK_REMOTE;
        int tries = queue.size();
        while (budget > 0 && tries-- > 0 && !queue.isEmpty()) {
            if (cursor >= queue.size()) cursor = 0;
            BlockPos pos = queue.get(cursor++);
            long key = pos.asLong();
            Long next = NEXT.get(key);
            if (next != null && next > ticks) continue;
            if (!(ws.getBlockEntity(pos) instanceof Container wanted) || ws.getBlockState(pos).getBlock() != mc.level.getBlockState(pos).getBlock()) {
                setWrong(key, false);
                NEXT.put(key, ticks + RECHECK_TICKS);
                continue;
            }
            budget--;
            List<ItemStack> actual = ContainerVerifier.actualContents(mc.level, pos, false);
            if (actual == null) {
                NEXT.put(key, ticks + 5); // 在等服务端发数据
                continue;
            }
            NEXT.put(key, ticks + RECHECK_TICKS);
            List<ItemStack> want = ContainerVerifier.nonEmpty(wanted);
            if (ContainerVerifier.sameContents(want, actual)) {
                ContainerVerifier.noteWorldContents(pos, true);
                setWrong(key, false);
            } else {
                ContainerVerifier.noteWorldContents(pos, false);
                // 世界里还是空的：要填，F 已经说了，不另外框
                setWrong(key, ContainerVerifier.hasItems(actual) || !ContainerVerifier.hasItems(want));
            }
        }
    }

    private static void setWrong(long key, boolean wrong) {
        boolean changed = wrong ? WRONG.add(key) : WRONG.remove(key);
        if (changed) rebuildList();
    }

    private static void rebuildList() {
        List<BlockPos> list = new ArrayList<>(WRONG.size());
        for (long k : WRONG) list.add(BlockPos.of(k));
        wrongList = List.copyOf(list);
    }

    /** 附近（客户端载入的区块里）投影中的容器，近的先查 */
    private static void collect(WorldSchematic ws) {
        int radius = Math.max(2, mc.options.getEffectiveRenderDistance());
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        List<BlockPos> found = new ArrayList<>();
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!ws.hasChunk(cx, cz) || !mc.level.hasChunk(cx, cz)) continue;
                List<BlockEntity> list;
                try {
                    list = new ArrayList<>(ws.getChunk(cx, cz).getBlockEntities().values());
                } catch (java.util.ConcurrentModificationException e) {
                    continue; // 投影世界的区块正被后台线程写：下次再收集
                }
                for (BlockEntity be : list) {
                    if (be instanceof Container) found.add(be.getBlockPos().immutable());
                    if (found.size() >= MAX_CONTAINERS) break;
                }
            }
        }
        BlockPos center = mc.player.blockPosition();
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        // 不在附近了的不再框（回来时重新查）
        Set<Long> near = new java.util.HashSet<>();
        for (BlockPos p : found) near.add(p.asLong());
        boolean removed = WRONG.removeIf(k -> !near.contains(k));
        NEXT.keySet().removeIf(k -> !near.contains(k));
        if (removed) rebuildList();
        queue = found;
        if (cursor >= queue.size()) cursor = 0;
    }
}
