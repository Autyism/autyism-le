package com.autyism.ale.verifier;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.data.EntityDataManager;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 需求 9b：投影验证器同时检查容器内容物。
 * <p>
 * 方块状态一致、且投影里是容器的位置，比较投影容器与世界容器的物品（不计槽位顺序，比较物品、组件、数量）。
 * 世界里容器的内容：单人世界直接在内置服务端线程读取；多人服务器通过 Litematica 的实体数据管理器
 * （Servux / 原版查询）请求，拿不到数据的先挂起，之后每 tick 复查。不一致的位置用洋红色框标出。
 */
public final class ContainerVerifier {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<SchematicVerifier, Set<BlockPos>> MISMATCHES = new WeakHashMap<>();
    private static final Map<SchematicVerifier, Map<BlockPos, List<ItemStack>>> PENDING = new WeakHashMap<>();
    private static final Map<BlockPos, CompletableFuture<List<ItemStack>>> FETCHES = new HashMap<>();
    private static final int RECHECK_PER_TICK = 64;
    private static int tickCounter;

    private ContainerVerifier() {
    }

    public static Set<BlockPos> getMismatches(SchematicVerifier verifier) {
        return MISMATCHES.getOrDefault(verifier, Set.of());
    }

    public static int countMismatches(SchematicVerifier verifier) {
        return getMismatches(verifier).size();
    }

    public static void reset(SchematicVerifier verifier) {
        MISMATCHES.remove(verifier);
        PENDING.remove(verifier);
    }

    /** 由 SchematicVerifier#checkBlockStates 调用（方块状态一致的位置） */
    public static void onBlockChecked(SchematicVerifier verifier, @Nullable WorldSchematic worldSchematic, @Nullable Level worldClient,
                                      BlockPos pos, BlockState stateSchematic, BlockState stateClient) {
        if (!AleConfigs.Generic.VERIFIER_CONTAINERS.getBooleanValue()) return;
        if (worldSchematic == null || worldClient == null || stateSchematic != stateClient || !stateSchematic.hasBlockEntity()) return;
        BlockEntity be = worldSchematic.getBlockEntity(pos);
        if (!(be instanceof Container schematicContainer)) return;
        List<ItemStack> wanted = nonEmpty(schematicContainer);
        evaluate(verifier, worldClient, pos.immutable(), wanted);
    }

    private static void evaluate(SchematicVerifier verifier, Level worldClient, BlockPos pos, List<ItemStack> wanted) {
        List<ItemStack> actual = actualContents(worldClient, pos);
        if (actual == null) {
            PENDING.computeIfAbsent(verifier, v -> new HashMap<>()).put(pos, wanted);
            return;
        }
        Map<BlockPos, List<ItemStack>> pending = PENDING.get(verifier);
        if (pending != null) pending.remove(pos);
        Set<BlockPos> set = MISMATCHES.computeIfAbsent(verifier, v -> new LinkedHashSet<>());
        if (sameContents(wanted, actual)) set.remove(pos);
        else set.add(pos);
    }

    /** 每 tick：处理挂起的位置、复查已标记的位置（玩家补好内容物后标记自动消失） */
    public static void tick() {
        if (mc.level == null) {
            FETCHES.clear();
            return;
        }
        tickCounter++;
        int budget = RECHECK_PER_TICK;
        for (var entry : new ArrayList<>(PENDING.entrySet())) {
            SchematicVerifier verifier = entry.getKey();
            for (var p : new ArrayList<>(entry.getValue().entrySet())) {
                if (budget-- <= 0) return;
                evaluate(verifier, mc.level, p.getKey(), p.getValue());
            }
        }
        if (tickCounter % 20 != 0) return;
        WorldSchematic ws = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
        if (ws == null) return;
        for (var entry : new ArrayList<>(MISMATCHES.entrySet())) {
            for (BlockPos pos : new ArrayList<>(entry.getValue())) {
                if (budget-- <= 0) return;
                if (ws.getBlockEntity(pos) instanceof Container c) {
                    evaluate(entry.getKey(), mc.level, pos, nonEmpty(c));
                } else {
                    entry.getValue().remove(pos);
                }
            }
        }
    }

    /** 世界中容器的实际内容；暂时拿不到返回 null */
    @Nullable
    private static List<ItemStack> actualContents(Level worldClient, BlockPos pos) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            CompletableFuture<List<ItemStack>> f = FETCHES.get(pos);
            if (f == null) {
                var dim = worldClient.dimension();
                f = server.submit(() -> {
                    var level = server.getLevel(dim);
                    if (level != null && level.getBlockEntity(pos) instanceof Container c) return nonEmpty(c);
                    return List.<ItemStack>of();
                });
                FETCHES.put(pos, f);
            }
            if (!f.isDone()) return null;
            FETCHES.remove(pos);
            return f.getNow(List.of());
        }
        Container c = EntityDataManager.getInstance().getBlockInventoryWrapped(worldClient, pos, false);
        if (c == null) {
            EntityDataManager.getInstance().requestBlockEntityWrapped(worldClient, pos);
            return null;
        }
        return nonEmpty(c);
    }

    private static List<ItemStack> nonEmpty(Container c) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) list.add(s.copy());
        }
        return list;
    }

    /** 不计顺序比较（同种物品的数量合并后比较） */
    public static boolean sameContents(List<ItemStack> a, List<ItemStack> b) {
        return totals(a).equals(totals(b));
    }

    private static Map<String, Integer> totals(List<ItemStack> list) {
        Map<String, Integer> m = new HashMap<>();
        for (ItemStack s : list) {
            String key = s.getItem() + "|" + s.getComponentsPatch();
            m.merge(key, s.getCount(), Integer::sum);
        }
        return m;
    }
}
