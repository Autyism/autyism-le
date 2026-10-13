package com.autyism.ale.verifier;

import com.autyism.ale.config.AleConfigs;
//? if >=1.21.11 {
import fi.dy.masa.litematica.data.EntityDataManager;
//?} else
//import fi.dy.masa.litematica.data.EntitiesDataStorage;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
 * （Servux / 原版查询）请求，拿不到数据的先挂起，之后每 tick 复查。
 * <p>
 * 不一致的容器在验证器的结果列表里是单独的一类“容器内容不同”（按容器种类分条），和 Litematica 自己的几类一样：
 * 只有在列表里选中这一类或其中一条时，世界里才框出来；“忽略”某一条后这种容器不再算进来。
 */
public final class ContainerVerifier {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Map<SchematicVerifier, Results> RESULTS = new WeakHashMap<>();
    private static final Map<BlockPos, CompletableFuture<List<ItemStack>>> FETCHES = new HashMap<>();
    private static final int RECHECK_PER_TICK = 64;
    private static int tickCounter;

    /**
     * 有结果列表里的“容器内容不同”一类（1.21.11）时，只框选中的；其他版本暂时没有这一类，照以前框出全部不一致的容器
     */
    //? if >=1.21.11 <26.1 {
    public static final boolean SELECT_IN_VERIFIER = true;
    //?} else
    //public static final boolean SELECT_IN_VERIFIER = false;

    /** 选中的容器在世界里的框的颜色（和 Litematica 已有几类的颜色都不同） */
    public static final int BOX_COLOR = 0x5A78FF;

    private ContainerVerifier() {
    }

    /** 一个容器的比较结果：投影里的方块状态、应有的物品、实际的物品 */
    public record Detail(BlockState state, List<ItemStack> wanted, List<ItemStack> actual) {
    }

    /** 一个验证器的容器检查结果和在结果列表里的选择 */
    private static final class Results {
        final Map<BlockPos, Detail> mismatches = new LinkedHashMap<>();
        final Map<BlockPos, List<ItemStack>> pending = new HashMap<>();
        final Set<Block> selectedBlocks = new HashSet<>();
        /** 忽略的容器种类；验证器重新开始时保留，“重置忽略”时清空（和 Litematica 忽略的状态一样） */
        final Set<Block> ignoredBlocks = new HashSet<>();
        boolean categorySelected;
        @Nullable List<BlockPos> renderPositions;
    }

    private static Results results(SchematicVerifier verifier) {
        return RESULTS.computeIfAbsent(verifier, v -> new Results());
    }

    /** 内容不一致、没有被忽略的容器位置 */
    public static Set<BlockPos> getMismatches(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        if (r == null) return Set.of();
        Set<BlockPos> set = new LinkedHashSet<>();
        for (var e : r.mismatches.entrySet()) {
            if (!r.ignoredBlocks.contains(e.getValue().state().getBlock())) set.add(e.getKey());
        }
        return set;
    }

    public static int countMismatches(SchematicVerifier verifier) {
        return getMismatches(verifier).size();
    }

    @Nullable
    public static Detail getDetail(SchematicVerifier verifier, BlockPos pos) {
        Results r = RESULTS.get(verifier);
        return r == null ? null : r.mismatches.get(pos);
    }

    /** 验证器清空数据（开始、重置）时：结果和选择清空，忽略的种类保留 */
    public static void reset(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        if (r == null) return;
        r.mismatches.clear();
        r.pending.clear();
        r.selectedBlocks.clear();
        r.categorySelected = false;
        r.renderPositions = null;
    }

    // ---------------- 结果列表：分类、选择、忽略 ----------------

    /** 按容器种类汇总（不含忽略的种类），每种一条：容器方块、数量 */
    public static Map<Block, Integer> overview(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        Map<Block, Integer> map = new LinkedHashMap<>();
        if (r == null) return map;
        for (Detail d : r.mismatches.values()) {
            Block block = d.state().getBlock();
            if (!r.ignoredBlocks.contains(block)) map.merge(block, 1, Integer::sum);
        }
        return map;
    }

    public static boolean isCategorySelected(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        return r != null && r.categorySelected;
    }

    public static boolean isEntrySelected(SchematicVerifier verifier, Block block) {
        Results r = RESULTS.get(verifier);
        return r != null && r.selectedBlocks.contains(block);
    }

    /** 点类别标题：选中时取消；没选中时选中整类，并取消这一类里单独选中的条目（和 Litematica 一样） */
    public static void toggleCategory(SchematicVerifier verifier) {
        Results r = results(verifier);
        r.categorySelected = !r.categorySelected;
        if (r.categorySelected) r.selectedBlocks.clear();
        r.renderPositions = null;
    }

    /** 点一条：选中时取消；没选中时选中它，并取消整类的选择 */
    public static void toggleEntry(SchematicVerifier verifier, Block block) {
        Results r = results(verifier);
        if (!r.selectedBlocks.remove(block)) {
            r.selectedBlocks.add(block);
            r.categorySelected = false;
        }
        r.renderPositions = null;
    }

    public static void ignore(SchematicVerifier verifier, Block block) {
        Results r = results(verifier);
        r.ignoredBlocks.add(block);
        r.selectedBlocks.remove(block);
        r.renderPositions = null;
    }

    public static boolean hasIgnored(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        return r != null && !r.ignoredBlocks.isEmpty();
    }

    public static void resetIgnored(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        if (r == null) return;
        r.ignoredBlocks.clear();
        r.renderPositions = null;
    }

    /** 下次画框时按玩家现在的位置重新排（验证器整理自己的位置列表时调用） */
    public static void invalidateRender(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        if (r != null) r.renderPositions = null;
    }

    /** 所有验证器里出现过的容器种类（结果列表算列宽用） */
    public static Set<Block> knownBlocks() {
        Set<Block> set = new HashSet<>();
        for (Results r : RESULTS.values()) {
            for (Detail d : r.mismatches.values()) set.add(d.state().getBlock());
        }
        return set;
    }

    /** 选中了这一类或其中至少一条 */
    public static boolean hasSelection(SchematicVerifier verifier) {
        Results r = RESULTS.get(verifier);
        return r != null && (r.categorySelected || !r.selectedBlocks.isEmpty());
    }

    /**
     * 要在世界里框出来的位置：选中的（没有结果列表分类的版本是全部），离玩家近的在前，最多 limit 个。
     * 结果缓存到选择或比较结果改变为止（和 Litematica 一样，不随玩家走动重新排序）。
     */
    public static List<BlockPos> renderPositions(SchematicVerifier verifier, int limit) {
        Results r = RESULTS.get(verifier);
        if (r == null) return List.of();
        if (r.renderPositions != null) return r.renderPositions;
        List<BlockPos> list = new ArrayList<>();
        for (var e : r.mismatches.entrySet()) {
            Block block = e.getValue().state().getBlock();
            if (r.ignoredBlocks.contains(block)) continue;
            if (!SELECT_IN_VERIFIER || r.categorySelected || r.selectedBlocks.contains(block)) list.add(e.getKey());
        }
        if (mc.player != null) {
            BlockPos center = mc.player.blockPosition();
            list.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        }
        if (list.size() > limit) list = new ArrayList<>(list.subList(0, limit));
        r.renderPositions = list;
        return list;
    }

    // ---------------- 比较 ----------------

    /** 由 SchematicVerifier#checkBlockStates 调用（方块状态一致的位置） */
    public static void onBlockChecked(SchematicVerifier verifier, @Nullable WorldSchematic worldSchematic, @Nullable Level worldClient,
                                      BlockPos pos, BlockState stateSchematic, BlockState stateClient) {
        if (!AleConfigs.Generic.VERIFIER_CONTAINERS.getBooleanValue()) return;
        if (worldSchematic == null || worldClient == null || stateSchematic != stateClient || !stateSchematic.hasBlockEntity()) return;
        BlockEntity be = worldSchematic.getBlockEntity(pos);
        if (!(be instanceof Container schematicContainer)) return;
        List<ItemStack> wanted = nonEmpty(schematicContainer);
        evaluate(verifier, worldClient, pos.immutable(), stateSchematic, wanted);
    }

    private static void evaluate(SchematicVerifier verifier, Level worldClient, BlockPos pos, BlockState state, List<ItemStack> wanted) {
        List<ItemStack> actual = actualContents(worldClient, pos);
        Results r = results(verifier);
        if (actual == null) {
            r.pending.put(pos, wanted);
            return;
        }
        r.pending.remove(pos);
        Detail old = r.mismatches.get(pos);
        if (sameContents(wanted, actual)) {
            if (old != null) {
                r.mismatches.remove(pos);
                r.renderPositions = null;
            }
        } else {
            r.mismatches.put(pos, new Detail(state, wanted, actual));
            if (old == null) r.renderPositions = null;
        }
    }

    /** 每 tick：处理挂起的位置、复查已标记的位置（玩家补好内容物后标记自动消失） */
    public static void tick() {
        if (mc.level == null) {
            FETCHES.clear();
            return;
        }
        tickCounter++;
        int budget = RECHECK_PER_TICK;
        WorldSchematic ws = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
        for (var entry : new ArrayList<>(RESULTS.entrySet())) {
            SchematicVerifier verifier = entry.getKey();
            for (var p : new ArrayList<>(entry.getValue().pending.entrySet())) {
                if (budget-- <= 0) return;
                BlockState state = ws != null ? ws.getBlockState(p.getKey()) : mc.level.getBlockState(p.getKey());
                evaluate(verifier, mc.level, p.getKey(), state, p.getValue());
            }
        }
        if (tickCounter % 20 != 0 || ws == null) return;
        for (var entry : new ArrayList<>(RESULTS.entrySet())) {
            Results r = entry.getValue();
            for (BlockPos pos : new ArrayList<>(r.mismatches.keySet())) {
                if (budget-- <= 0) return;
                if (ws.getBlockEntity(pos) instanceof Container c && ws.getBlockState(pos) == mc.level.getBlockState(pos)) {
                    evaluate(entry.getKey(), mc.level, pos, ws.getBlockState(pos), nonEmpty(c));
                } else {
                    r.mismatches.remove(pos);
                    r.renderPositions = null;
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
        // 1.21.10 及更早 Litematica 的实体数据管理器叫 EntitiesDataStorage
        //? if >=1.21.11 {
        Container c = EntityDataManager.getInstance().getBlockInventoryWrapped(worldClient, pos, false);
        if (c == null) {
            EntityDataManager.getInstance().requestBlockEntityWrapped(worldClient, pos);
            return null;
        }
        //?} else {
        /*Container c = EntitiesDataStorage.getInstance().getBlockInventory(worldClient, pos, false);
        if (c == null) {
            EntitiesDataStorage.getInstance().requestBlockEntity(worldClient, pos);
            return null;
        }
        *///?}
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

    /**
     * 两份物品的差别：正数 = 世界里少了（应放入），负数 = 世界里多了（应取出）。键是一个代表物品（数量 1）
     */
    public static List<Map.Entry<ItemStack, Integer>> difference(Detail detail) {
        Map<String, ItemStack> samples = new LinkedHashMap<>();
        Map<String, Integer> diff = new LinkedHashMap<>();
        for (ItemStack s : detail.wanted()) {
            String key = key(s);
            samples.putIfAbsent(key, s.copyWithCount(1));
            diff.merge(key, s.getCount(), Integer::sum);
        }
        for (ItemStack s : detail.actual()) {
            String key = key(s);
            samples.putIfAbsent(key, s.copyWithCount(1));
            diff.merge(key, -s.getCount(), Integer::sum);
        }
        List<Map.Entry<ItemStack, Integer>> list = new ArrayList<>();
        for (var e : diff.entrySet()) {
            if (e.getValue() != 0) list.add(Map.entry(samples.get(e.getKey()), e.getValue()));
        }
        return list;
    }

    private static Map<String, Integer> totals(List<ItemStack> list) {
        Map<String, Integer> m = new HashMap<>();
        for (ItemStack s : list) m.merge(key(s), s.getCount(), Integer::sum);
        return m;
    }

    private static String key(ItemStack s) {
        return s.getItem() + "|" + s.getComponentsPatch();
    }
}
