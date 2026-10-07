package com.autyism.ale.preview;

import com.autyism.ale.config.AleConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 浏览器列表 / 网格里的缩略图。只为屏幕上真正画到的条目生成（后台读文件、生成网格），
 * 画好一次就只保留那张小图，网格随即释放；每帧限制花在上传和绘制上的时间，几百个文件也不会卡。
 * 离开浏览器时整个缓存清空。
 */
public final class ThumbnailCache {
    public enum State {
        LOADING,
        READY,
        TOO_BIG,
        FAILED
    }

    private static final int MAX_QUADS = 1_000_000;
    private static final int MAX_TEXTURE = 512;
    private static final int MAX_ENTRIES = 600;
    private static final long FRAME_BUDGET_NANOS = 6_000_000L;

    private static final class Entry {
        final Path file;
        final long modified;
        long checkedAt;
        @Nullable
        PreviewModel model;
        final PreviewTarget target = new PreviewTarget();
        State state = State.LOADING;
        int renderedW, renderedH;
        int wantW, wantH;
        long lastUsed;

        Entry(Path file, long modified) {
            this.file = file;
            this.modified = modified;
        }

        void close() {
            if (this.model != null) this.model.close();
            this.model = null;
            this.target.close();
        }
    }

    private final Map<Path, Entry> entries = new HashMap<>();
    /** 已经读好、等着上传和绘制的缩略图（按请求先后，先来的先做） */
    private final java.util.LinkedHashSet<Entry> work = new java.util.LinkedHashSet<>();
    private long frame;
    private long frameStart;

    /**
     * 在 (x, y, w, h)（界面坐标）画 file 的缩略图；还没好时什么也不画。
     * 返回当前状态（调用者据此画“太大”“无法预览”之类的提示）。
     */
    public State draw(GuiGraphics g, Path file, int x, int y, int w, int h) {
        if (w <= 2 || h <= 2) return State.LOADING;
        Entry e = entry(file);
        e.lastUsed = this.frame;
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        e.wantW = Math.min(MAX_TEXTURE, (int) Math.round(w * scale));
        e.wantH = Math.min(MAX_TEXTURE, (int) Math.round(h * scale));
        if (e.state == State.READY && (e.renderedW != e.wantW || e.renderedH != e.wantH) && e.model == null) {
            // 尺寸变了（换了显示方式）：重新生成
            e.state = State.LOADING;
        }
        if (e.state == State.LOADING) request(e);
        if (e.state == State.READY && e.target.isReady()) e.target.blit(g, x, y, x + w, y + h);
        return e.state;
    }

    private Entry entry(Path file) {
        Entry e = this.entries.get(file);
        long now = System.currentTimeMillis();
        // 文件被改写（例如另存覆盖）时重新生成；最多每 2 秒查一次修改时间
        if (e != null && now - e.checkedAt > 2000L) {
            e.checkedAt = now;
            if (modified(file) != e.modified) {
                e.close();
                this.entries.remove(file);
                e = null;
            }
        }
        if (e == null) {
            if (this.entries.size() >= MAX_ENTRIES) evictOldest();
            e = new Entry(file, modified(file));
            e.checkedAt = now;
            this.entries.put(file, e);
        }
        return e;
    }

    /** 画的时候只做便宜的事：开始后台读取、处理“太大 / 读不出”；上传和绘制留到这一帧最后（endFrame） */
    private void request(Entry e) {
        if (e.model == null) {
            long maxVolume = AleConfigs.Browser.THUMBNAIL_MAX_VOLUME.getIntegerValue();
            e.model = PreviewModel.start(e.file, new PreviewModel.Options(maxVolume, MAX_QUADS, AleConfigs.Preview.BLOCK_ENTITIES.getBooleanValue()));
        }
        switch (e.model.status()) {
            case TOO_BIG -> finish(e, State.TOO_BIG);
            case FAILED -> finish(e, State.FAILED);
            case READY -> this.work.add(e);
            case LOADING -> {
            }
        }
    }

    /**
     * 每帧画完条目后调用：按请求先后上传网格、画缩略图，直到用完这一帧的时间预算。
     * 至少完成一个，保证不管这一帧别的东西花了多少时间，缩略图都会一个个出来。
     */
    public void endFrame() {
        long start = System.nanoTime();
        boolean progressed = false;
        for (java.util.Iterator<Entry> it = this.work.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            PreviewModel model = e.model;
            if (model == null || e.state != State.LOADING || model.status() != PreviewModel.Status.READY) {
                it.remove();
                continue;
            }
            if (progressed && System.nanoTime() - start > FRAME_BUDGET_NANOS) break;
            model.upload(progressed ? start + FRAME_BUDGET_NANOS : Long.MAX_VALUE);
            if (!model.isComplete()) {
                progressed = true;
                continue;
            }
            PreviewCamera camera = new PreviewCamera();
            camera.frame(model.sizeX(), model.sizeY(), model.sizeZ());
            e.target.ensureSize(e.wantW, e.wantH);
            PreviewRenderer.render(model, camera, e.target, AleConfigs.Preview.BLOCK_ENTITIES.getBooleanValue());
            e.renderedW = e.wantW;
            e.renderedH = e.wantH;
            finish(e, State.READY);
            it.remove();
            progressed = true;
        }
    }

    private void finish(Entry e, State state) {
        e.state = state;
        if (e.model != null) {
            e.model.close();
            e.model = null;
        }
    }

    /** 每帧画缩略图之前调用一次：开始这一帧的时间预算 */
    public void beginFrame() {
        this.frame++;
        this.frameStart = System.nanoTime();
    }

    private void evictOldest() {
        Entry oldest = null;
        for (Entry e : this.entries.values()) {
            if (oldest == null || e.lastUsed < oldest.lastUsed) oldest = e;
        }
        if (oldest != null) {
            oldest.close();
            this.entries.remove(oldest.file);
            this.work.remove(oldest);
        }
    }

    /** 不在屏幕上的条目的后台任务先停掉（滚动很快时不浪费时间） */
    public void dropUnused() {
        List<Path> stale = new ArrayList<>();
        for (Entry e : this.entries.values()) {
            if (e.model != null && this.frame - e.lastUsed > 40) {
                e.model.close();
                e.model = null;
                if (e.state == State.LOADING) stale.add(e.file);
            }
        }
        for (Path p : stale) this.entries.remove(p);
    }

    public void clear() {
        this.work.clear();
        for (Iterator<Entry> it = this.entries.values().iterator(); it.hasNext(); ) {
            it.next().close();
            it.remove();
        }
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    /** 测试用：某个缩略图的详细状态 */
    public String describe(Path file) {
        Entry e = this.entries.get(file);
        if (e == null) return "no entry (frame " + this.frame + ")";
        return e.state + " model=" + (e.model == null ? "none" : e.model.debugState())
                + " want=" + e.wantW + "x" + e.wantH + " rendered=" + e.renderedW + "x" + e.renderedH + " lastUsed=" + e.lastUsed + " frame=" + this.frame;
    }

    /** 测试用：某个文件缩略图的状态（还没请求过为 null） */
    @Nullable
    public State stateOf(Path file) {
        Entry e = this.entries.get(file);
        return e == null ? null : e.state;
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }
}
