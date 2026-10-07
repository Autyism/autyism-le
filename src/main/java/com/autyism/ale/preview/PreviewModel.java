package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import net.minecraft.core.BlockPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一个投影文件的 3D 预览数据。后台线程读文件、分批生成网格；渲染线程每帧在时间预算内把生成好的批次上传到显卡，
 * 所以大投影会一边生成一边逐步显示，界面不会卡住。关闭后取消后台任务并释放所有内存和显卡缓冲。
 */
public final class PreviewModel implements AutoCloseable {
    public enum Status {
        LOADING,
        READY,
        TOO_BIG,
        FAILED
    }

    /** 生成选项：体积上限（超过就不做，-1 不限）、四边形上限、是否画方块实体 */
    public record Options(long maxVolume, int maxQuads, boolean blockEntities) {
    }

    private static final int SECTIONS_PER_BATCH = 48;
    private static final int QUADS_PER_BATCH = 40_000;

    public final Path file;
    private final Options options;
    private volatile Status status = Status.LOADING;
    private volatile int sizeX = 1, sizeY = 1, sizeZ = 1;
    private volatile boolean sized;
    private volatile boolean meshDone;
    private volatile boolean truncated;
    private volatile int sectionsTotal = 1;
    private volatile int sectionsDone;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ConcurrentLinkedQueue<SceneMesher.MeshPart> ready = new ConcurrentLinkedQueue<>();

    // 以下只在渲染线程访问
    private final List<PreviewRenderer.Page> pages = new ArrayList<>();
    private final List<PreviewRenderer.BlockEntityDraw> blockEntities = new ArrayList<>();
    private boolean blockEntitiesDisabled;
    private int contentVersion;
    private boolean closed;

    private PreviewModel(Path file, Options options) {
        this.file = file;
        this.options = options;
    }

    /** 开始在后台生成 */
    public static PreviewModel start(Path file, Options options) {
        PreviewModel model = new PreviewModel(file, options);
        PreviewWorkers.submit(model::build);
        return model;
    }

    public Status status() {
        return this.status;
    }

    public boolean isSized() {
        return this.sized;
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    /** 网格已全部生成（并且已经全部上传） */
    public boolean isComplete() {
        return this.meshDone && this.ready.isEmpty();
    }

    /** 超过四边形上限，只显示了一部分 */
    public boolean isTruncated() {
        return this.truncated;
    }

    /** 0..1 的生成进度 */
    public float progress() {
        return Math.min(1.0F, (float) this.sectionsDone / Math.max(1, this.sectionsTotal));
    }

    /** 内容（上传的网格、方块实体）每变一次加一 */
    public int contentVersion() {
        return this.contentVersion;
    }

    List<PreviewRenderer.Page> pages() {
        return this.pages;
    }

    List<PreviewRenderer.BlockEntityDraw> blockEntityDraws() {
        return this.blockEntitiesDisabled ? Collections.emptyList() : this.blockEntities;
    }

    void disableBlockEntities() {
        this.blockEntitiesDisabled = true;
    }

    /** 测试用：生成进度的详细情况 */
    public String debugState() {
        return "status=" + this.status + " meshDone=" + this.meshDone + " pending=" + this.ready.size() + " sections=" + this.sectionsDone + "/"
                + this.sectionsTotal + " cancelled=" + this.cancelled.get() + " pages=" + this.pages.size();
    }

    /** 测试用：会画出来的方块实体个数 */
    public int blockEntityCount() {
        int n = 0;
        for (PreviewRenderer.BlockEntityDraw d : blockEntityDraws()) if (!d.failed) n++;
        return n;
    }

    // ------------------------------------------------------------------ 后台

    private void build() {
        try {
            if (this.cancelled.get()) return;
            if (this.options.maxVolume() >= 0) {
                long volume = SchematicFiles.readVolume(this.file);
                if (volume > this.options.maxVolume()) {
                    this.status = Status.TOO_BIG;
                    return;
                }
            }
            LitematicaSchematic schematic = SchematicFiles.load(this.file);
            if (schematic == null) {
                this.status = Status.FAILED;
                return;
            }
            if (this.cancelled.get()) return;
            SchematicView view = SchematicView.of(schematic);
            if (this.options.maxVolume() >= 0 && (long) view.sizeX() * view.sizeY() * view.sizeZ() > this.options.maxVolume()) {
                this.status = Status.TOO_BIG;
                return;
            }
            this.sizeX = view.sizeX();
            this.sizeY = view.sizeY();
            this.sizeZ = view.sizeZ();
            this.sized = true;
            this.status = Status.READY;
            mesh(schematic, view);
        } catch (Throwable t) {
            AleMod.LOGGER.warn("Could not build the schematic preview of {}", this.file, t);
            if (!this.sized) this.status = Status.FAILED;
        } finally {
            this.meshDone = true;
        }
    }

    private void mesh(LitematicaSchematic schematic, SchematicView view) {
        Map<BlockPos, CompoundData> blockEntityData = null;
        if (this.options.blockEntities()) {
            blockEntityData = new HashMap<>();
            for (String region : schematic.getAreas().keySet()) {
                BlockPos origin = view.regionOrigin(region);
                Map<BlockPos, CompoundData> map = schematic.getBlockEntityMapForRegion(region);
                if (origin == null || map == null) continue;
                for (Map.Entry<BlockPos, CompoundData> e : map.entrySet()) blockEntityData.put(origin.offset(e.getKey()), e.getValue());
            }
        }
        // 区段从中心向外排：大投影先显示中间
        int nx = (view.sizeX() + 15) >> 4, ny = (view.sizeY() + 15) >> 4, nz = (view.sizeZ() + 15) >> 4;
        List<int[]> sections = new ArrayList<>(nx * ny * nz);
        for (int x = 0; x < nx; x++) for (int y = 0; y < ny; y++) for (int z = 0; z < nz; z++) sections.add(new int[]{x, y, z});
        float cx = nx * 0.5F - 0.5F, cy = ny * 0.5F - 0.5F, cz = nz * 0.5F - 0.5F;
        sections.sort((a, b) -> Float.compare(d2(a, cx, cy, cz), d2(b, cx, cy, cz)));
        this.sectionsTotal = sections.size();

        SceneMesher mesher = new SceneMesher(view, blockEntityData);
        int quads = 0;
        int i = 0;
        while (i < sections.size() && !this.cancelled.get()) {
            List<int[]> batch = new ArrayList<>();
            int batchQuadsEstimate = 0;
            while (i < sections.size() && batch.size() < SECTIONS_PER_BATCH && batchQuadsEstimate < QUADS_PER_BATCH) {
                batch.add(sections.get(i++));
                batchQuadsEstimate += 600;
            }
            SceneMesher.MeshPart part = mesher.mesh(batch, this.cancelled::get);
            if (part == null) break;
            this.sectionsDone = i;
            quads += part.quads();
            this.ready.add(part);
            if (this.cancelled.get()) break;
            if (quads > this.options.maxQuads()) {
                this.truncated = i < sections.size();
                break;
            }
        }
        if (this.cancelled.get()) freePending();
    }

    private static float d2(int[] s, float cx, float cy, float cz) {
        float dx = s[0] - cx, dy = s[1] - cy, dz = s[2] - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------------ 渲染线程

    /** 把后台生成好的批次上传到显卡，直到超过截止时间；有新内容时返回 true */
    public boolean upload(long deadlineNanos) {
        if (this.closed) return false;
        boolean changed = false;
        SceneMesher.MeshPart part;
        while ((part = this.ready.poll()) != null) {
            try {
                for (var e : part.layers.entrySet()) {
                    if (e.getValue().quadCount() == 0) continue;
                    float[] centroids = e.getKey() == MeshLayer.TRANSLUCENT ? part.translucentCentroids : new float[0];
                    this.pages.add(PreviewRenderer.upload(e.getKey(), e.getValue(), centroids, part.centerX, part.centerY, part.centerZ));
                }
                for (SceneMesher.PlacedBlockEntity be : part.blockEntities) {
                    this.blockEntities.add(new PreviewRenderer.BlockEntityDraw(be.pos().getX(), be.pos().getY(), be.pos().getZ(), be.blockEntity()));
                }
            } catch (Throwable t) {
                AleMod.LOGGER.warn("Could not upload a schematic preview mesh", t);
            } finally {
                part.free();
            }
            changed = true;
            if (System.nanoTime() > deadlineNanos) break;
        }
        if (changed) this.contentVersion++;
        return changed;
    }

    private void freePending() {
        SceneMesher.MeshPart part;
        while ((part = this.ready.poll()) != null) part.free();
    }

    /** 取消后台任务，释放网格内存和显卡缓冲（渲染线程调用） */
    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.cancelled.set(true);
        freePending();
        for (PreviewRenderer.Page p : this.pages) p.close();
        this.pages.clear();
        this.blockEntities.clear();
        // 后台线程可能还在生成最后一批：它结束时看到 cancelled 会自己释放
        PreviewWorkers.submit(this::freePending);
    }
}
