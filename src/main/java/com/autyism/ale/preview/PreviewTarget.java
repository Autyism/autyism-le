package com.autyism.ale.preview;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * 离屏纹理（颜色 + 深度），预览先画进这里，再作为一张图贴到界面上。
 * 只在渲染线程使用；用完要 close。
 */
public final class PreviewTarget implements AutoCloseable {
    @Nullable
    private GpuTexture color;
    @Nullable
    private GpuTextureView colorView;
    @Nullable
    private GpuTexture depth;
    @Nullable
    private GpuTextureView depthView;
    private int width;
    private int height;

    /** 确保纹理是这个像素尺寸；尺寸变了（重新创建）时返回 true */
    public boolean ensureSize(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (this.color != null && this.width == width && this.height == height) return false;
        close();
        GpuDevice device = RenderSystem.getDevice();
        this.color = device.createTexture(() -> "ALE schematic preview", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST,
                TextureFormat.RGBA8, width, height, 1, 1);
        this.colorView = device.createTextureView(this.color);
        this.depth = device.createTexture(() -> "ALE schematic preview depth", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST, TextureFormat.DEPTH32, width, height, 1, 1);
        this.depthView = device.createTextureView(this.depth);
        this.width = width;
        this.height = height;
        return true;
    }

    public boolean isReady() {
        return this.color != null;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    float aspect() {
        return this.height == 0 ? 1.0F : (float) this.width / this.height;
    }

    GpuTexture colorTexture() {
        return this.color;
    }

    GpuTextureView colorView() {
        return this.colorView;
    }

    GpuTexture depthTexture() {
        return this.depth;
    }

    GpuTextureView depthView() {
        return this.depthView;
    }

    /** 把纹理贴到界面的 (x0, y0)-(x1, y1)（界面坐标）；纹理里的颜色是预乘透明度的 */
    public void blit(GuiGraphics g, int x0, int y0, int x1, int y1) {
        if (this.colorView == null) return;
        BlitRenderState blit = new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(this.colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                new Matrix3x2f(g.pose()), x0, y0, x1, y1, 0.0F, 1.0F, 1.0F, 0.0F, -1, g.scissorStack.peek());
        //? if >=26.1 {
        /*g.guiRenderState.addGuiElement(blit);
        *///?} else {
        g.guiRenderState.submitGuiElement(blit);
        //?}
    }

    /**
     * 释放纹理。界面上这一帧可能已经登记了要贴这张图（界面在这一帧最后才真正绘制），
     * 所以显卡对象等到这一帧的命令都执行完后再关。
     */
    @Override
    public void close() {
        GpuTextureView cv = this.colorView, dv = this.depthView;
        GpuTexture c = this.color, d = this.depth;
        this.colorView = null;
        this.color = null;
        this.depthView = null;
        this.depth = null;
        this.width = 0;
        this.height = 0;
        if (c == null) return;
        Runnable release = () -> {
            if (cv != null) cv.close();
            c.close();
            if (dv != null) dv.close();
            if (d != null) d.close();
        };
        if (RenderSystem.tryGetDevice() != null) {
            RenderSystem.queueFencedTask(release);
        } else {
            release.run();
        }
    }
}
