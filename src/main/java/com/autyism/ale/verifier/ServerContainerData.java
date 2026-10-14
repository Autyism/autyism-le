package com.autyism.ale.verifier;

import fi.dy.masa.litematica.data.EntityDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 多人游戏里世界中容器的内容：只认服务端真正发来的数据。
 * <p>
 * Litematica 的实体数据同步（服务端装了 Servux 并打开同步，或者 OP 的备用查询）收到一个方块实体的数据时，
 * 把它记下来（EntityDataReceivedMixin）。Litematica 自己的缓存不能直接用：等回复期间里面放的是客户端世界里的方块实体（没有物品），
 * 大箱子还会两半合在一起。没有同步时一律返回 null（不检查，也不当作“空的”）。
 * <p>
 * 都在客户端主线程上用。
 */
public final class ServerContainerData {
    /** 收到的数据多久以内算“现在的”（tick） */
    private static final int FRESH_TICKS = 40;
    /** 发出请求后这么久还没收到，可以再请求一次 */
    private static final int REQUEST_RETRY_TICKS = 100;
    /** 太久没用的记录清掉 */
    private static final int FORGET_AFTER_TICKS = 1200;

    private record Snapshot(List<ItemStack> items, long tick) {
    }

    private static final Map<Long, Snapshot> RECEIVED = new HashMap<>();
    private static final Map<Long, Long> REQUESTED = new HashMap<>();
    private static long lastPrune;

    private ServerContainerData() {
    }

    /**
     * 能向服务端要容器数据：Litematica 的实体数据同步开着而且服务端有 Servux，或者打开了备用查询（只有 OP 能查到；
     * 查不到时没有回复，这些容器就不检查）。Litematica 自己限制每 tick 发出的请求数
     */
    public static boolean available() {
        EntityDataManager manager = EntityDataManager.getInstance();
        return (manager.isEnabled() && manager.hasServuxServer()) || manager.isBackupEnabled();
    }

    /** Litematica 收到了服务端发来的这个方块实体的数据（并已经读进了客户端的方块实体） */
    public static void received(BlockPos pos, @Nullable BlockEntity be) {
        if (!(be instanceof Container container)) return;
        // 收到时就复制一份内容；记录在主线程上做
        List<ItemStack> items = ContainerVerifier.nonEmpty(container);
        long key = pos.asLong();
        Runnable store = () -> {
            // 只记 ALE 要过的位置（Litematica 为别的功能要的数据不用存）
            if (REQUESTED.containsKey(key)) RECEIVED.put(key, new Snapshot(items, ContainerVerifier.now()));
        };
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.isSameThread()) store.run();
        else mc.execute(store);
    }

    /**
     * 服务端最近发来的这个容器的内容（物品不分格子，复制过的）。没有足够新的数据时向服务端要（已经在等就不重复要），返回 null
     */
    @Nullable
    public static List<ItemStack> get(Level world, BlockPos pos) {
        if (!available()) return null;
        long now = ContainerVerifier.now();
        prune(now);
        long key = pos.asLong();
        Snapshot snapshot = RECEIVED.get(key);
        if (snapshot != null && now - snapshot.tick() <= FRESH_TICKS) return snapshot.items();
        Long asked = REQUESTED.get(key);
        boolean waiting = asked != null && now - asked <= REQUEST_RETRY_TICKS && (snapshot == null || snapshot.tick() < asked);
        if (!waiting) {
            REQUESTED.put(key, now);
            EntityDataManager.getInstance().requestBlockEntityWrapped(world, pos);
        }
        return null;
    }

    public static void clear() {
        RECEIVED.clear();
        REQUESTED.clear();
    }

    private static void prune(long now) {
        if (now - lastPrune < 200) return;
        lastPrune = now;
        RECEIVED.values().removeIf(s -> now - s.tick() > FORGET_AFTER_TICKS);
        REQUESTED.values().removeIf(t -> now - t > FORGET_AFTER_TICKS);
    }
}
