package com.autyism.ale.preview;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * 离屏纹理（颜色 + 深度），预览先画进这里，再作为一张图贴到界面上。Minecraft 1.21.5 版：
 * 界面按贴图的名字画图，所以颜色纹理以自己的名字登记在贴图管理器里（不归它释放）；贴图时用预乘透明度的混合。
 * 只在渲染线程使用；用完要 close。
 */
public final class PreviewTarget implements AutoCloseable {
    /** 界面贴图（颜色是预乘透明度的） */
    private static final RenderPipeline BLIT_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("autyism-le", "pipeline/preview_blit"))
            .withBlend(new BlendFunction(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_ALPHA))
            .build();
    private static final Function<ResourceLocation, RenderType> BLIT = Util.memoize(id -> RenderType.create("autyism-le_preview_blit", 786432,
            BLIT_PIPELINE, RenderType.CompositeState.builder().setTextureState(new RenderStateShard.TextureStateShard(id, TriState.FALSE, false))
                    .createCompositeState(false)));
    private static int nextId;

    @Nullable
    private GpuTexture color;
    @Nullable
    private GpuTexture depth;
    /** 颜色纹理在贴图管理器里的名字（每次重新创建都换一个，旧的稍后才释放） */
    @Nullable
    private ResourceLocation id;
    private int width;
    private int height;

    /** 确保纹理是这个像素尺寸；尺寸变了（重新创建）时返回 true */
    public boolean ensureSize(int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (this.color != null && this.width == width && this.height == height) return false;
        close();
        GpuDevice device = RenderSystem.getDevice();
        this.color = device.createTexture(() -> "ALE schematic preview", TextureFormat.RGBA8, width, height, 1);
        this.color.setTextureFilter(FilterMode.NEAREST, false);
        this.color.setAddressMode(AddressMode.CLAMP_TO_EDGE);
        this.depth = device.createTexture(() -> "ALE schematic preview depth", TextureFormat.DEPTH32, width, height, 1);
        this.id = ResourceLocation.fromNamespaceAndPath("autyism-le", "preview_target/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(this.id, new Registered(this.color));
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

    GpuTexture depthTexture() {
        return this.depth;
    }

    /** 把纹理贴到界面的 (x0, y0)-(x1, y1)（界面坐标）；纹理的第一行在下面，所以上下翻过来贴 */
    public void blit(GuiGraphics g, int x0, int y0, int x1, int y1) {
        if (this.color == null || this.id == null) return;
        g.blit(BLIT, this.id, x0, y0, 0.0F, this.height, x1 - x0, y1 - y0, this.width, -this.height, this.width, this.height);
    }

    /**
     * 释放纹理。界面上这一帧可能已经登记了要贴这张图（界面在这一帧最后才真正绘制），
     * 所以名字的登记和显卡对象等到这一帧的命令都执行完后再撤掉、关掉。
     */
    @Override
    public void close() {
        GpuTexture c = this.color, d = this.depth;
        ResourceLocation name = this.id;
        this.color = null;
        this.depth = null;
        this.id = null;
        this.width = 0;
        this.height = 0;
        if (c == null) return;
        Runnable release = () -> {
            if (name != null) Minecraft.getInstance().getTextureManager().release(name);
            c.close();
            if (d != null) d.close();
        };
        if (RenderSystem.tryGetDevice() != null) {
            RenderSystem.queueFencedTask(release);
        } else {
            release.run();
        }
    }

    /** 登记在贴图管理器里的颜色纹理；纹理归 PreviewTarget 管，撤掉登记时不关它 */
    private static final class Registered extends AbstractTexture {
        Registered(GpuTexture texture) {
            this.texture = texture;
        }

        @Override
        public void close() {
            this.texture = null;
        }
    }
}
