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
    /** 单人游戏：向服务端线程要的容器内容（和要的时间）。太久以前要的结果不用（之后可能已经变了） */
    private record Fetch(CompletableFuture<List<ItemStack>> future, long tick) {
    }
    private static final Map<BlockPos, Fetch> FETCHES = new HashMap<>();
    private static final int FETCH_MAX_AGE = 10;
    /**
     * 确认过“世界里的内容和投影一样”的容器（BlockPos.asLong → 确认时客户端世界里的方块和方块实体）。“需要填充”标记不画在这些容器上。
     * 只在还能确定的时候有效：
     * <ul>
     * <li>方块被敲掉或换掉（客户端的方块实体换了一个）、区块卸载、换世界：作废（每 tick 在主线程检查）；</li>
     * <li>打开容器看到内容不一样：作废（OpenedContainers）；</li>
     * <li>能读到世界里的内容时（单人游戏；多人游戏里 Litematica 能从服务端拿到容器数据时，限玩家附近）定期重新比较，变了就作废。</li>
     * </ul>
     * 区块重建线程只读这个表（用并发表），写入都在客户端主线程。验证器重置时也全部清空
     */
    private static final Map<Long, Known> KNOWN = new java.util.concurrent.ConcurrentHashMap<>();

    /** 确认时客户端世界里的方块和方块实体（敲掉重放会换成新的方块实体），以及上次比较内容的时间（tick） */
    private record Known(Block block, java.lang.ref.WeakReference<BlockEntity> blockEntity, long checkedAt) {
    }

    /** 每 tick 最多检查这么多条“方块还是那个吗”（一般远少于这个数） */
    private static final int VALIDATE_PER_TICK = 512;
    /** 每条已确认的容器多久重新比较一次内容（tick） */
    private static final int RECHECK_INTERVAL = 40;
    private static final int CONTENT_RECHECKS_PER_TICK = 16;
    /** 多人游戏里只重新比较玩家附近的（每次都要向服务端要数据） */
    private static final double REMOTE_RECHECK_RADIUS = 64.0;
    @Nullable
    private static Iterator<Map.Entry<Long, Known>> validateCursor;
    /** 已知结果变了、要重建渲染数据的区块（每 tick 处理几个，不一次全部重建） */
    private static final Set<Long> REBUILD_CHUNKS = new LinkedHashSet<>();
    private static final int REBUILDS_PER_TICK = 8;
    @Nullable
    private static Level knownLevel;
    private static final int RECHECK_PER_TICK = 64;
    private static int tickCounter;

    /**
     * 结果列表里有“容器内容不同”一类：只框选中的（不再一下框出全部不一致的容器）
     */
    public static final boolean SELECT_IN_VERIFIER = true;

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
        clearKnown();
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

    private static void clearKnown() {
        for (long p : KNOWN.keySet()) forget(p);
    }

    /**
     * 这一格的“需要填充”可能变了：重画它所在的区块。大箱子的 F 画在两半中间、由其中一半来画，
     * 另一半在别的区块时那个区块也要重画
     */
    private static void markRebuild(long pos) {
        REBUILD_CHUNKS.add(net.minecraft.world.level.ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4));
        // Litefastica 画的投影：它的网格不跟着 Litematica 的区块重画，F 在这里也要重建
        com.autyism.ale.render.FastMoveFill.markChunkDirty(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
        WorldSchematic ws = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
        if (ws == null) return;
        net.minecraft.world.level.block.state.BlockState state = ws.getBlockState(BlockPos.of(pos));
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                && state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            BlockPos other = BlockPos.of(pos).relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
            REBUILD_CHUNKS.add(net.minecraft.world.level.ChunkPos.asLong(other.getX() >> 4, other.getZ() >> 4));
        }
    }

    private static void forget(long pos) {
        if (KNOWN.remove(pos) != null) {
            markRebuild(pos);
        }
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
        boolean same = sameContents(wanted, actual);
        if (same) {
            if (old != null) {
                r.mismatches.remove(pos);
                r.renderPositions = null;
            }
        } else {
            r.mismatches.put(pos, new Detail(state, wanted, actual));
            if (old == null) r.renderPositions = null;
        }
        setKnownMatch(pos, same);
    }

    /** 世界里这个容器的内容已经确认和投影一样（验证器查过，或者打开看过），而且之后没有作废 */
    public static boolean isKnownToMatch(BlockPos pos) {
        return !KNOWN.isEmpty() && KNOWN.containsKey(pos.asLong());
    }

    /** 别的途径（例如玩家打开了这个容器）知道了世界里这个容器的内容和投影是否一样 */
    public static void noteWorldContents(BlockPos pos, boolean same) {
        setKnownMatch(pos.immutable(), same);
    }

    /** 主线程调用。确认一样时记下客户端世界里这一格现在的方块和方块实体（之后换掉了就作废） */
    private static void setKnownMatch(BlockPos pos, boolean same) {
        long key = pos.asLong();
        BlockEntity be = same && mc.level != null ? mc.level.getBlockEntity(pos) : null;
        if (be == null) {
            forget(key);
            return;
        }
        Known old = KNOWN.put(key, new Known(mc.level.getBlockState(pos).getBlock(), new java.lang.ref.WeakReference<>(be), tickCounter));
        if (old == null) markRebuild(key);
    }

    /**
     * 每 tick：已确认的容器里，方块被敲掉 / 换掉（方块实体不是原来那个）、区块卸载的作废；
     * 读得到世界里的内容时，到时间的重新比较一次，变了的作废
     */
    private static void maintainKnown(@Nullable WorldSchematic ws) {
        if (KNOWN.isEmpty() || mc.level == null) {
            validateCursor = null;
            return;
        }
        if (validateCursor == null || !validateCursor.hasNext()) validateCursor = KNOWN.entrySet().iterator();
        for (int i = 0; i < VALIDATE_PER_TICK && validateCursor.hasNext(); i++) {
            var e = validateCursor.next();
            BlockPos pos = BlockPos.of(e.getKey());
            Known k = e.getValue();
            BlockEntity be = mc.level.getBlockEntity(pos);
            if (be == null || be != k.blockEntity().get() || mc.level.getBlockState(pos).getBlock() != k.block()) forget(e.getKey());
        }
        boolean local = mc.getSingleplayerServer() != null;
        boolean remote = !local && ServerContainerData.available();
        if ((!local && !remote) || ws == null || mc.player == null) return;
        int budget = CONTENT_RECHECKS_PER_TICK;
        for (var e : KNOWN.entrySet()) {
            if (budget <= 0) break;
            Known k = e.getValue();
            if (tickCounter - k.checkedAt() < RECHECK_INTERVAL) continue;
            BlockPos pos = BlockPos.of(e.getKey());
            if (remote && mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REMOTE_RECHECK_RADIUS * REMOTE_RECHECK_RADIUS) continue;
            budget--;
            if (!(ws.getBlockEntity(pos) instanceof Container wanted)) {
                forget(e.getKey());
                continue;
            }
            List<ItemStack> actual = actualContents(mc.level, pos);
            if (actual == null) continue;
            if (sameContents(nonEmpty(wanted), actual)) KNOWN.replace(e.getKey(), k, new Known(k.block(), k.blockEntity(), tickCounter));
            else forget(e.getKey());
        }
    }

    /** 每 tick：处理挂起的位置、复查已标记的位置（玩家补好内容物后标记自动消失） */
    // ---------------- 不用打开也能知道填好了没有 ----------------
    /**
     * 画着“需要填充”F 的容器（区块重建线程记下，BlockPos.asLong → 下次检查的 tick）。能读到世界里的内容时
     * （单人游戏；多人游戏里 Litematica 的实体数据同步开着、服务端有 Servux），主线程每 tick 查几个：
     * 和投影一样就记为已确认（F 随之消失），不一样就过一会儿再查。多人游戏只查玩家附近的（每次都要向服务端要数据）。
     */
    private static final Map<Long, Long> FILL_CANDIDATES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int CANDIDATE_CHECKS_PER_TICK = 8;
    private static final int CANDIDATE_RECHECK_INTERVAL = 20;
    private static final int MAX_CANDIDATES = 8192;

    /** 这一格画着 F、等着主线程去查世界里填好了没有（测试用） */
    public static boolean isFillCandidate(BlockPos pos) {
        return FILL_CANDIDATES.containsKey(pos.asLong());
    }

    /** 区块重建线程：这一格画了 F */
    public static void noteFillCandidate(BlockPos pos) {
        if (FILL_CANDIDATES.size() >= MAX_CANDIDATES) return;
        FILL_CANDIDATES.putIfAbsent(pos.asLong(), 0L);
    }

    /**
     * 投影文件里这一格该放的东西（Litefastica 画的投影：F 按投影文件来画，投影世界可能还没写好，主线程查的时候拿这个比）。
     * 投影世界里有这个容器时还是以投影世界为准
     */
    private record Wanted(Block block, List<ItemStack> items) {
    }

    private static final Map<Long, Wanted> FILL_WANTED = new java.util.concurrent.ConcurrentHashMap<>();

    /** 区段建网格的线程：这一格画了 F，投影文件里该放的是 wanted */
    public static void noteFillCandidate(BlockPos pos, Block block, Container wanted) {
        if (FILL_CANDIDATES.size() >= MAX_CANDIDATES) return;
        FILL_WANTED.put(pos.asLong(), new Wanted(block, nonEmpty(wanted)));
        FILL_CANDIDATES.putIfAbsent(pos.asLong(), 0L);
    }

    private static void checkFillCandidates(@Nullable WorldSchematic ws) {
        if (FILL_CANDIDATES.isEmpty() || ws == null || mc.level == null || mc.player == null) return;
        boolean local = mc.getSingleplayerServer() != null;
        boolean remote = !local && ServerContainerData.available();
        if (!local && !remote) return;
        int budget = CANDIDATE_CHECKS_PER_TICK;
        var it = FILL_CANDIDATES.entrySet().iterator();
        while (it.hasNext() && budget > 0) {
            var e = it.next();
            if (e.getValue() > tickCounter) continue;
            BlockPos pos = BlockPos.of(e.getKey());
            List<ItemStack> wantedItems;
            Block wantedBlock;
            if (ws.getBlockEntity(pos) instanceof Container wanted) {
                wantedItems = nonEmpty(wanted);
                wantedBlock = ws.getBlockState(pos).getBlock();
            } else if (FILL_WANTED.get(e.getKey()) instanceof Wanted w) {
                wantedItems = w.items();
                wantedBlock = w.block();
            } else {
                wantedItems = null;
                wantedBlock = null;
            }
            if (KNOWN.containsKey(e.getKey()) || wantedItems == null) {
                // 已经确认过、或者投影里不再是容器
                it.remove();
                FILL_WANTED.remove(e.getKey());
                continue;
            }
            double far = (mc.options.getEffectiveRenderDistance() + 2) * 16.0;
            if (Math.abs(pos.getX() - mc.player.getX()) > far || Math.abs(pos.getZ() - mc.player.getZ()) > far) {
                // 远到投影的区块都不画了：回来时区块重画，会再记一次
                it.remove();
                FILL_WANTED.remove(e.getKey());
                continue;
            }
            if (wantedBlock != mc.level.getBlockState(pos).getBlock()) {
                // 世界里这一格还不是这个方块：还没放，或者世界的区块还没载入（进服务器、走过去时投影常常先画出来）。
                // 以前这里直接不管了，之后区块载入、F 还画着却再也没人去查，要打开一次才消失。过一会儿再看
                e.setValue((long) tickCounter + CANDIDATE_RECHECK_INTERVAL);
                continue;
            }
            if (remote && mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > REMOTE_RECHECK_RADIUS * REMOTE_RECHECK_RADIUS) continue;
            budget--;
            List<ItemStack> actual = actualContents(mc.level, pos);
            if (actual == null) {
                e.setValue(tickCounter + 5L); // 在等服务端发数据
                continue;
            }
            if (sameContents(wantedItems, actual)) {
                noteWorldContents(pos, true);
                it.remove();
                FILL_WANTED.remove(e.getKey());
            } else {
                e.setValue((long) tickCounter + CANDIDATE_RECHECK_INTERVAL);
            }
        }
    }

    public static void tick() {
        if (mc.level != knownLevel) {
            // 换世界 / 离开世界：之前知道的都不算了
            knownLevel = mc.level;
            KNOWN.clear();
            // 画着 F 的格子不清：进世界时投影的区块常常在这之前就画好、记下了（之后不会再画一遍），清掉就再也没人去查，
            // 退出重进后 F 一直不消失。留着的每一格查之前都会再核对投影和世界，换了世界的不会查错
            FETCHES.clear(); // 上一个世界里要的内容不能拿来比
            SignHighlight.onWorldChange(mc.level);
            validateCursor = null;
            REBUILD_CHUNKS.clear();
            ServerContainerData.clear();
        }
        if (mc.level == null) {
            FETCHES.clear();
            return;
        }
        if (!REBUILD_CHUNKS.isEmpty()) {
            var it = REBUILD_CHUNKS.iterator();
            for (int i = 0; i < REBUILDS_PER_TICK && it.hasNext(); i++) {
                long chunk = it.next();
                it.remove();
                fi.dy.masa.litematica.util.SchematicWorldRefresher.INSTANCE.markSchematicChunksForRenderUpdate(
                        net.minecraft.world.level.ChunkPos.getX(chunk), net.minecraft.world.level.ChunkPos.getZ(chunk));
            }
        }
        tickCounter++;
        int budget = RECHECK_PER_TICK;
        WorldSchematic ws = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();
        maintainKnown(ws);
        checkFillCandidates(ws);
        SignHighlight.tick();
        for (var entry : new ArrayList<>(RESULTS.entrySet())) {
            SchematicVerifier verifier = entry.getKey();
            for (var p : new ArrayList<>(entry.getValue().pending.entrySet())) {
                if (budget-- <= 0) return;
                BlockState state = ws != null ? ws.getBlockState(p.getKey()) : mc.level.getBlockState(p.getKey());
                evaluate(verifier, mc.level, p.getKey(), state, p.getValue());
            }
        }
        if (tickCounter % 20 != 0 || ws == null) return;
        // 多人游戏里每次复查都要向服务端要数据：只复查玩家附近的
        boolean remote = mc.getSingleplayerServer() == null;
        for (var entry : new ArrayList<>(RESULTS.entrySet())) {
            Results r = entry.getValue();
            for (BlockPos pos : new ArrayList<>(r.mismatches.keySet())) {
                if (remote && mc.player != null && mc.player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))
                        > REMOTE_RECHECK_RADIUS * REMOTE_RECHECK_RADIUS) continue;
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
            Fetch fetch = FETCHES.get(pos);
            if (fetch != null && fetch.future().isDone() && tickCounter - fetch.tick() > FETCH_MAX_AGE) {
                FETCHES.remove(pos); // 很早以前要的，内容可能已经变了：重新要
                fetch = null;
            }
            CompletableFuture<List<ItemStack>> f = fetch == null ? null : fetch.future();
            if (f == null) {
                var dim = worldClient.dimension();
                f = server.submit(() -> {
                    var level = server.getLevel(dim);
                    if (level != null && level.getBlockEntity(pos) instanceof Container c) return nonEmpty(c);
                    return List.<ItemStack>of();
                });
                FETCHES.put(pos, new Fetch(f, tickCounter));
            }
            if (!f.isDone()) return null;
            FETCHES.remove(pos);
            return f.getNow(List.of());
        }
        // 多人游戏：只用服务端真正发来的数据（Litematica 的实体数据同步：服务端装了 Servux，或者 OP 的备用查询）。
        // Litematica 自己的缓存在等回复时放的是客户端世界里的方块实体（里面没有物品），大箱子还会两半合起来，都不能拿来比
        return ServerContainerData.get(worldClient, pos);
    }

    /** 主线程的 tick 计数（ServerContainerData 判断数据新旧用） */
    static long now() {
        return tickCounter;
    }

    /** 容器里不空的格子（复制） */
    static List<ItemStack> nonEmpty(Container c) {
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
