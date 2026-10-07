package com.autyism.ale.preview;

import com.autyism.ale.config.AleConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 浏览器里选中的投影的大预览：模型、相机和两张离屏纹理（信息面板一张、全屏一张）。
 * 信息面板和全屏界面共用同一个会话，所以从全屏回来时角度和距离都保持不变。
 * 同一时间只有一个会话；换选中的文件时旧的会被关掉。
 */
public final class PreviewSession implements AutoCloseable {
    /** 大预览的四边形上限（约 2 百万个，显存 250 MB 左右） */
    private static final int MAX_QUADS = 2_000_000;
    /** 每帧最多花在上传网格上的时间 */
    private static final long UPLOAD_BUDGET_NANOS = 4_000_000L;
    /** 自由视角开关在不同投影之间保持（与按钮状态一致） */
    private static boolean freecam;

    @Nullable
    private static PreviewSession current;

    public final Path file;
    private final PreviewModel model;
    private final PreviewCamera camera = new PreviewCamera();
    private final PreviewTarget panelTarget = new PreviewTarget();
    private final PreviewTarget fullTarget = new PreviewTarget();
    private boolean framed;
    private int panelCamera = -1, panelContent = -1;
    private int fullCamera = -1, fullContent = -1;
    private long lastMoveNanos;
    private boolean closed;

    private PreviewSession(Path file) {
        this.file = file;
        this.model = PreviewModel.start(file, new PreviewModel.Options(-1, MAX_QUADS, AleConfigs.Preview.BLOCK_ENTITIES.getBooleanValue()));
    }

    /** 选中的文件对应的会话（没有就新建，旧的关掉） */
    public static PreviewSession forFile(Path file) {
        if (current != null && current.file.equals(file) && !current.closed) return current;
        closeCurrent();
        current = new PreviewSession(file);
        return current;
    }

    @Nullable
    public static PreviewSession current() {
        return current;
    }

    public static void closeCurrent() {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    public static boolean isFreecam() {
        return freecam;
    }

    public static void setFreecam(boolean on) {
        freecam = on;
        if (current != null) current.camera.setFree(on);
    }

    public PreviewModel model() {
        return this.model;
    }

    public PreviewCamera camera() {
        return this.camera;
    }

    /** 每帧：上传新网格；尺寸知道后把相机对准投影 */
    private void update() {
        this.model.upload(System.nanoTime() + UPLOAD_BUDGET_NANOS);
        if (!this.framed && this.model.isSized()) {
            this.camera.frame(this.model.sizeX(), this.model.sizeY(), this.model.sizeZ());
            this.camera.setFree(freecam);
            this.framed = true;
        }
    }

    /** 自由视角：按住移动键（玩家的前后左右、跳跃、潜行键）飞行 */
    public void applyMovementKeys() {
        long now = System.nanoTime();
        float dt = this.lastMoveNanos == 0 ? 0 : Math.min(0.1F, (now - this.lastMoveNanos) / 1.0E9F);
        this.lastMoveNanos = now;
        if (!this.camera.isFree() || dt <= 0) return;
        var options = Minecraft.getInstance().options;
        float forward = (PreviewInput.isDown(options.keyUp) ? 1 : 0) - (PreviewInput.isDown(options.keyDown) ? 1 : 0);
        float strafe = (PreviewInput.isDown(options.keyRight) ? 1 : 0) - (PreviewInput.isDown(options.keyLeft) ? 1 : 0);
        float up = (PreviewInput.isDown(options.keyJump) ? 1 : 0) - (PreviewInput.isDown(options.keyShift) ? 1 : 0);
        if (forward == 0 && strafe == 0 && up == 0) return;
        float speed = this.camera.flySpeed() * dt * (PreviewInput.isDown(options.keySprint) ? 3.0F : 1.0F);
        this.camera.move(forward * speed, strafe * speed, up * speed);
    }

    /** 画进信息面板（界面坐标）；内容和相机没变时只贴上次的图 */
    public void drawPanel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        draw(g, this.panelTarget, x0, y0, x1, y1, true);
    }

    /** 画满全屏 */
    public void drawFull(GuiGraphics g, int x0, int y0, int x1, int y1) {
        draw(g, this.fullTarget, x0, y0, x1, y1, false);
    }

    private void draw(GuiGraphics g, PreviewTarget target, int x0, int y0, int x1, int y1, boolean panel) {
        if (this.closed || x1 <= x0 || y1 <= y0) return;
        update();
        if (!this.model.isSized()) return;
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        boolean resized = target.ensureSize((int) Math.round((x1 - x0) * scale), (int) Math.round((y1 - y0) * scale));
        int cam = this.camera.version(), content = this.model.contentVersion();
        boolean dirty = resized || (panel ? cam != this.panelCamera || content != this.panelContent : cam != this.fullCamera || content != this.fullContent);
        if (dirty) {
            PreviewRenderer.render(this.model, this.camera, target, AleConfigs.Preview.BLOCK_ENTITIES.getBooleanValue());
            if (panel) {
                this.panelCamera = cam;
                this.panelContent = content;
            } else {
                this.fullCamera = cam;
                this.fullContent = content;
            }
        }
        target.blit(g, x0, y0, x1, y1);
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.model.close();
        this.panelTarget.close();
        this.fullTarget.close();
    }
}
