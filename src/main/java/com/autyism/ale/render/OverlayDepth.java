package com.autyism.ale.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import fi.dy.masa.malilib.compat.iris.IrisCompat;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * 透过玻璃显示投影时，错误标记（Litematica 的覆盖层）怎么画。
 * <p>
 * 以前 ALE 在原版画半透明层（水、气泡柱、含水方块里的水、冰、染色玻璃、粘液块、蜂蜜块……）之前就把标记画了，
 * 好让玻璃后面的标记也看得到；结果这些半透明方块随后盖在自己格子的标记上，标记被冲淡到几乎看不见。
 * <p>
 * 现在：原版画半透明层之前，把这一刻的深度（不透明的方块、实体和投影方块）复制一份；标记照 Litematica 原来的时机
 * 在整帧最后画，但只和这份深度比较。半透明方块挡不住标记，标记画在它们上面；不透明的方块照样挡住后面的标记。
 * 光影包开着时（Iris）渲染流程不同，保持以前的做法。
 */
public final class OverlayDepth {
    private static @Nullable GpuTexture texture;
    private static @Nullable GpuTextureView view;
    /** 这一帧已经复制好，覆盖层可以用 */
    private static boolean captured;

    private OverlayDepth() {
    }

    public static void newFrame() {
        captured = false;
    }

    /** 在原版画半透明层之前调用（没有渲染通道打开的时候）。复制成功返回 true */
    public static boolean capture() {
        captured = false;
        if (IrisCompat.isShaderActive()) return false;
        // 这一帧根本不画覆盖层（关着、或没有投影）时不复制
        boolean invert = fi.dy.masa.litematica.config.Hotkeys.INVERT_OVERLAY_RENDER_STATE.getKeybind().isKeybindHeld();
        if (fi.dy.masa.litematica.config.Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.getBooleanValue() == invert) return false;
        if (fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld() == null) return false;
        //? if >=26.2 {
        /*RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        *///?} else
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GpuTexture depth = main.getDepthTexture();
        if (depth == null || depth.isClosed()) return false;
        int width = depth.getWidth(0), height = depth.getHeight(0);
        try {
            if (texture == null || texture.isClosed() || texture.getWidth(0) != width || texture.getHeight(0) != height
                    || texture.getFormat() != depth.getFormat()) {
                close();
                GpuDevice device = RenderSystem.getDevice();
                texture = device.createTexture(() -> "ALE overlay depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
                        | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, depth.getFormat(), width, height, 1, 1);
                view = device.createTextureView(texture);
            }
            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(depth, texture, 0, 0, 0, 0, 0, width, height);
            captured = true;
        } catch (RuntimeException e) {
            com.autyism.ale.AleMod.LOGGER.warn("Could not copy the depth buffer for the schematic overlay; drawing it the old way", e);
            close();
        }
        return captured;
    }

    /** 覆盖层的渲染通道用的深度：这一帧复制过、尺寸也对得上时换成复制的那份，否则不变 */
    public static @Nullable GpuTextureView depthFor(@Nullable GpuTextureView original) {
        if (!captured || original == null || view == null || view.isClosed()) return original;
        if (view.getWidth(0) != original.getWidth(0) || view.getHeight(0) != original.getHeight(0)) return original;
        return view;
    }

    public static boolean isCaptured() {
        return captured;
    }

    private static void close() {
        if (view != null) view.close();
        if (texture != null) texture.close();
        view = null;
        texture = null;
        captured = false;
    }
}
