package com.autyism.ale.compat;

import com.autyism.ale.AleMod;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.verifier.ContainerVerifier;
import com.autyism.ale.verifier.ServerContainerData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Container Searcher（Loxoz 的模组）只记得打开过的容器。装了它时，附近容器的内容不用打开就记进它的缓存：
 * 单人直接从世界里拿，多人经 Servux 从服务端拿（和容器检查同一条路，ContainerVerifier.actualContents）。
 * 记的方式和打开容器一样，调它自己的 updateCachedContainer（按名字反射调用，不复制也不改它的代码）。
 * <p>
 * 只查客户端已加载的区块，近的先查；每 tick 几个，每个查完隔一段时间再查。
 */
public final class ContainerSearcherServux {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("csearcher");
    private static final int COLLECT_INTERVAL = 40;
    private static final int RECHECK_TICKS = 20 * 60;
    private static final int WAIT_TICKS = 5;
    private static final int PER_TICK = 4;
    private static final int MAX_CONTAINERS = 4096;
    /** Container Searcher 的“忽略小容器”：少于这么多格的不记（它自己的 NON_SMALL_CONTAINER） */
    private static final int SMALL_CONTAINER = 9;

    private static Method inst, getConfig, getCache, current, update, isValid;
    private static Field ignoreSmall;
    private static boolean broken;

    /** 每个位置下次可以查的 tick */
    private static final Map<Long, Long> NEXT = new HashMap<>();
    private static List<BlockPos> queue = List.of();
    private static int cursor;
    private static long ticks;
    @Nullable
    private static Level level;

    private ContainerSearcherServux() {
    }

    public static void tick() {
        if (!LOADED || broken || !AleConfigs.Generic.CONTAINER_SEARCHER_SERVUX.getBooleanValue()) return;
        if (mc.level != level) {
            level = mc.level;
            NEXT.clear();
            queue = List.of();
            cursor = 0;
        }
        if (mc.level == null || mc.player == null) return;
        if (mc.getSingleplayerServer() == null && !ServerContainerData.available()) return;
        if (!lookup()) return;
        if (ticks++ % COLLECT_INTERVAL == 0) collect();
        int budget = PER_TICK, tries = queue.size();
        while (budget > 0 && tries-- > 0) {
            if (cursor >= queue.size()) cursor = 0;
            BlockPos pos = queue.get(cursor++);
            long key = pos.asLong();
            Long next = NEXT.get(key);
            if (next != null && next > ticks) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (!(mc.level.getBlockEntity(pos) instanceof Container) || !valid(state.getBlock())) {
                NEXT.put(key, ticks + RECHECK_TICKS);
                continue;
            }
            budget--;
            // 大箱子在 Container Searcher 里是一条：两半合起来交给它，右半在前（和游戏里大箱子界面的格子顺序一样）。
            // 两半同时要，免得一半到了另一半还没要
            BlockPos other = otherHalf(pos, state);
            List<ItemStack> items = contents(pos);
            List<ItemStack> otherItems = other == null ? List.of() : contents(other);
            if (items == null || otherItems == null) {
                NEXT.put(key, ticks + WAIT_TICKS); // 还在等服务端
                continue;
            }
            if (other != null) {
                boolean thisFirst = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
                List<ItemStack> all = new ArrayList<>(thisFirst ? items : otherItems);
                all.addAll(thisFirst ? otherItems : items);
                items = all;
                NEXT.put(other.asLong(), ticks + RECHECK_TICKS);
            }
            NEXT.put(key, ticks + RECHECK_TICKS);
            if (items.size() < SMALL_CONTAINER && ignoreSmall()) continue;
            boolean full = !items.isEmpty() && items.stream().allMatch(s -> !s.isEmpty() && s.getCount() >= s.getMaxStackSize());
            store(pos, items, full);
        }
    }

    /** 容器每一格的内容（合成器禁用的格子当空格）；还拿不到返回 null */
    @Nullable
    private static List<ItemStack> contents(BlockPos pos) {
        List<ItemStack> slots = ContainerVerifier.actualContents(mc.level, pos);
        if (slots == null) return null;
        List<ItemStack> out = new ArrayList<>(slots.size());
        for (ItemStack s : slots) out.add(s == ContainerVerifier.disabledSlot() ? ItemStack.EMPTY : s);
        return out;
    }

    private static void collect() {
        int radius = Math.max(2, mc.options.getEffectiveRenderDistance());
        BlockPos center = mc.player.blockPosition();
        int pcx = center.getX() >> 4, pcz = center.getZ() >> 4;
        List<BlockPos> found = new ArrayList<>();
        for (int cx = pcx - radius; cx <= pcx + radius; cx++) {
            for (int cz = pcz - radius; cz <= pcz + radius; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : mc.level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (be instanceof Container && valid(be.getBlockState().getBlock())) found.add(be.getBlockPos().immutable());
                }
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        queue = found.size() > MAX_CONTAINERS ? new ArrayList<>(found.subList(0, MAX_CONTAINERS)) : found;
        cursor = 0;
        NEXT.keySet().removeIf(k -> BlockPos.of(k).distSqr(center) > (double) (radius + 2) * (radius + 2) * 256);
    }

    @Nullable
    private static BlockPos otherHalf(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState o = mc.level.getBlockState(other);
        if (!o.is(state.getBlock())) return null;
        ChestType t = o.getValue(ChestBlock.TYPE);
        return t != ChestType.SINGLE && t != state.getValue(ChestBlock.TYPE) ? other : null;
    }

    // ---- 按名字调用 Container Searcher

    private static boolean lookup() {
        if (update != null) return true;
        try {
            Class<?> cs = Class.forName("fr.loxoz.csearcher.CSearcher");
            Class<?> entry = Class.forName("fr.loxoz.csearcher.core.CacheEntry");
            inst = cs.getMethod("inst");
            getConfig = cs.getMethod("getConfig");
            getCache = cs.getMethod("getCache");
            current = getCache.getReturnType().getMethod("current");
            isValid = cs.getMethod("isValidContainer", Block.class);
            ignoreSmall = getConfig.getReturnType().getField("ignoreSmallContainers");
            update = cs.getMethod("updateCachedContainer", Level.class, BlockPos.class, entry, List.class, boolean.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            broken = true;
            AleMod.LOGGER.warn("Container Searcher 的接口对不上（版本变了？），不往它里面记了: {}", e.toString());
            return false;
        }
    }

    private static boolean valid(Block block) {
        try {
            return (boolean) isValid.invoke(null, block);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean ignoreSmall() {
        try {
            Object cfg = getConfig.invoke(null);
            return cfg == null || ignoreSmall.getBoolean(cfg);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    private static void store(BlockPos pos, List<ItemStack> items, boolean full) {
        try {
            Object cs = inst.invoke(null);
            if (cs == null) return;
            Object entry = current.invoke(getCache.invoke(cs));
            if (entry == null) return; // 还没进世界 / 这个世界关掉了记录
            update.invoke(cs, mc.level, pos, entry, items, full);
        } catch (ReflectiveOperationException e) {
            broken = true;
            AleMod.LOGGER.warn("往 Container Searcher 里记容器失败，不再记了: {}", e.toString());
        }
    }
}
