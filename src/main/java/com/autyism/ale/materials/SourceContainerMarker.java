package com.autyism.ale.materials;

import com.autyism.ale.config.AleConfigs;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.util.data.Color4f;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A thin outline around the chosen material source containers (through walls), while a material list is shown
 * (its screen, or its info HUD) or always, depending on the setting. Containers whose contents are not known yet get a
 * dimmer outline.
 */
public final class SourceContainerMarker {
    private SourceContainerMarker() {
    }

    /** Positions to outline now: [known, unknown] */
    public static List<List<BlockPos>> positions() {
        Minecraft mc = Minecraft.getInstance();
        if (!SourceContainers.enabled() || mc.level == null || SourceContainers.spots().isEmpty() || !visible(mc)) return List.of();
        String dim = SourceContainers.dim(mc.level);
        List<BlockPos> known = new ArrayList<>(), unknown = new ArrayList<>();
        for (SourceContainers.Spot spot : SourceContainers.spots()) {
            if (!spot.dim().equals(dim)) continue;
            for (var e : spot.halves().entrySet()) {
                if (e.getValue().gone) continue;
                (e.getValue().known() ? known : unknown).add(e.getKey());
            }
        }
        return List.of(known, unknown);
    }

    // (named client: the 26.2 source replacement moves client.screen to client.gui.screen())
    private static boolean visible(Minecraft client) {
        if (AleConfigs.Generic.MATERIAL_SOURCE_MARKER_ALWAYS.getBooleanValue()) return true;
        if (client.screen instanceof GuiMaterialList) return true;
        MaterialListBase list = DataManager.getMaterialList();
        return list != null && list.getHudRenderer().getShouldRenderCustom();
    }

    /** Draws the outlines (from Litematica's overlay pass) */
    public static void render() {
        List<List<BlockPos>> pos = positions();
        if (pos.isEmpty() || (pos.get(0).isEmpty() && pos.get(1).isEmpty())) return;
        int argb = AleConfigs.Generic.MATERIAL_SOURCE_MARKER_COLOR.getIntegerValue();
        Color4f known = Color4f.fromColor(argb, 1f);
        Color4f unknown = new Color4f(known.r, known.g, known.b, 0.45f);
        RenderContext ctx = context(() -> "autyism-le:material_sources", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
        BufferBuilder buffer = ctx.getBuilder();
        try {
            for (BlockPos p : pos.get(0)) outline(p, known, buffer);
            for (BlockPos p : pos.get(1)) outline(p, unknown, buffer);
            MeshData mesh = buffer.build();
            if (mesh != null) {
                //? if <1.21.11
                //ctx.lineWidth(2.0f);
                ctx.draw(mesh, false, true);
                mesh.close();
            }
            ctx.reset();
            ctx.close();
        } catch (Exception ignored) {
        }
    }

    private static void outline(BlockPos pos, Color4f color, BufferBuilder buffer) {
        // 1.21.11 on: the line width is part of the vertices; before, it is set when drawing
        //? if >=1.21.11 {
        fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, color, 0.006, 2.0f, buffer);
        //?} else
        //fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, color, 0.006, buffer);
    }

    // MaLiLib's RenderContext: one more argument from 26.2 on; 1.21.5 only takes the pipeline
    private static RenderContext context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        //? if >=26.2 {
        /*return new RenderContext(name, pipeline, 0);
        *///?} elif >=1.21.6 {
        return new RenderContext(name, pipeline);
        //?} else {
        /*return new RenderContext(pipeline);
        *///?}
    }
}
