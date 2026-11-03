package com.autyism.ale.render;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.diff.DiffSession;
import com.autyism.ale.locate.WrongBlockGuide;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.core.BlockPos;

/**
 * Boxes of the wrong block guide and the schematic diff, drawn through walls with Litematica's other overlay boxes
 * (OverlayRenderer.renderBoxes, so shader-pack routers like LitematicaPass take them along like Litematica's own).
 * Outline plus translucent sides, like the verifier's markers.
 */
public final class ToolBoxes {
    /** Boxes drawn in the last frame (for the tests) */
    public static volatile int lastGuideBoxes, lastDiffBoxes;
    public static volatile long guideFrames, diffFrames;

    private ToolBoxes() {
    }

    public static void draw() {
        drawGuide();
        drawDiff();
    }

    private static void drawGuide() {
        WrongBlockGuide.Target t = WrongBlockGuide.target();
        if (t == null || !AleToolsConfigs.LOCATE.getBooleanValue()) {
            lastGuideBoxes = 0;
            return;
        }
        int argb = AleToolsConfigs.LOCATE_BOX_COLOR.getIntegerValue();
        // a slow pulse so it stands out from the verifier's boxes
        float pulse = 0.5f + 0.5f * (float) Math.sin((System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2);
        Color4f line = Color4f.fromColor(argb, 1f);
        Color4f side = new Color4f(line.r, line.g, line.b, 0.18f + 0.22f * pulse);
        BlockPos pos = t.pos();
        draw(new long[]{pos.asLong()}, new int[]{0}, new Color4f[]{line}, new Color4f[]{side}, 1, 5.0f, "guide");
        lastGuideBoxes = 1;
        guideFrames++;
    }

    private static void drawDiff() {
        DiffSession.RenderList list = DiffSession.renderList();
        if (list == null || list.count() == 0 || !AleToolsConfigs.DIFF.getBooleanValue() || !AleToolsConfigs.DIFF_SHOW.getBooleanValue()) {
            lastDiffBoxes = 0;
            return;
        }
        Color4f[] lines = new Color4f[3], sides = new Color4f[3];
        int[] colors = {AleToolsConfigs.DIFF_ADDED_COLOR.getIntegerValue(), AleToolsConfigs.DIFF_REMOVED_COLOR.getIntegerValue(),
                AleToolsConfigs.DIFF_CHANGED_COLOR.getIntegerValue()};
        for (int i = 0; i < 3; i++) {
            lines[i] = Color4f.fromColor(colors[i], 1f);
            sides[i] = new Color4f(lines[i].r, lines[i].g, lines[i].b, 0.22f);
        }
        draw(list.positions(), list.kinds(), lines, sides, list.count(), 2.0f, "diff");
        lastDiffBoxes = list.count();
        diffFrames++;
    }

    /** kinds[i] indexes the color arrays */
    private static void draw(long[] positions, int[] kinds, Color4f[] lines, Color4f[] sides, int count, float width, String name) {
        RenderContext ctx = context(() -> "autyism-le:" + name, MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
        BufferBuilder buffer = ctx.getBuilder();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            m.set(positions[i]);
            // 1.21.11 起线宽写在顶点里；之前是绘制时统一设置
            //? if >=1.21.11 {
            RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(m, lines[kinds[i]], 0.004, width, buffer);
            //?} else
            //RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(m, lines[kinds[i]], 0.004, buffer);
        }
        try {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                //? if <1.21.11
                //ctx.lineWidth(width);
                ctx.draw(meshData, false, true);
                meshData.close();
            }
            ctx.reset();
            buffer = start(ctx, () -> "autyism-le:" + name + "/sides", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
            for (int i = 0; i < count; i++) {
                m.set(positions[i]);
                RenderUtils.renderAreaSidesBatched(m, m, sides[kinds[i]], 0.002, buffer);
            }
            MeshData meshSides = buffer.build();
            if (meshSides != null) {
                ctx.draw(meshSides, false, false);
                meshSides.close();
            }
            ctx.reset();
            ctx.close();
        } catch (Exception ignored) {
        }
    }

    // MaLiLib 的 RenderContext：26.2 起构造和 start 都多一个参数（Litematica 自己传 0）；1.21.5 只认管线
    //? if >=26.2 {
    /*private static RenderContext context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return new RenderContext(name, pipeline, 0);
    }

    private static BufferBuilder start(RenderContext ctx, java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return ctx.start(name, pipeline, 0);
    }
    *///?} elif >=1.21.6 {
    private static RenderContext context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return new RenderContext(name, pipeline);
    }

    private static BufferBuilder start(RenderContext ctx, java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return ctx.start(name, pipeline);
    }
    //?} else {
    /*private static RenderContext context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return new RenderContext(pipeline);
    }

    private static BufferBuilder start(RenderContext ctx, java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return ctx.start(pipeline);
    }
    *///?}
}
