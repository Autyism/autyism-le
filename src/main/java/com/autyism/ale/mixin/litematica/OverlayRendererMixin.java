package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifier;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.OverlayRenderer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.util.data.Color4f;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 需求 9b：用洋红色框标出容器内容不一致的位置（与验证器的错误标记一起显示）。
 */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class OverlayRendererMixin {
    private static final Color4f ALE_CONTAINER_COLOR = Color4f.fromColor(0xFFFF30FF);

    // ---------------- 需求 5a：流体与实体的信息对比 ----------------

    /** 方块信息检测也命中投影里的流体（水源、岩浆源） */
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderHoverInfo",
            at = @At(value = "INVOKE", remap = true, target = "Lfi/dy/masa/litematica/util/RayTraceUtils;getGenericTrace(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;DZZZ)Lfi/dy/masa/litematica/util/RayTraceUtils$RayTraceWrapper;"),
            index = 4)
    private boolean ale$targetFluids(boolean targetFluids) {
        return targetFluids || com.autyism.ale.config.AleConfigs.Generic.INFO_FLUIDS_ENTITIES.getBooleanValue();
    }

    /** 看向投影实体（比方块更近）时，显示实体的对比框，替代方块信息 */
    @Inject(method = "renderHoverInfo", at = @At("HEAD"), cancellable = true)
    // 1.21.11 之前这个方法还带 Minecraft 参数（1.21.5 时排在最前）
    //? if >=1.21.11 {
    private void ale$entityInfo(fi.dy.masa.malilib.render.GuiContext ctx, ProfilerFiller profiler, CallbackInfo ci) {
    //?} elif >=1.21.6 {
    /*private void ale$entityInfo(net.minecraft.client.gui.GuiGraphics ctx, net.minecraft.client.Minecraft mc, ProfilerFiller profiler, CallbackInfo ci) {
    *///?} else {
    /*private void ale$entityInfo(net.minecraft.client.Minecraft mc, net.minecraft.client.gui.GuiGraphics ctx, ProfilerFiller profiler, CallbackInfo ci) {
    *///?}
        if (!com.autyism.ale.config.AleConfigs.Generic.INFO_FLUIDS_ENTITIES.getBooleanValue()) return;
        if (!fi.dy.masa.litematica.config.Hotkeys.RENDER_INFO_OVERLAY.getKeybind().isKeybindHeld()) return;
        if (!fi.dy.masa.litematica.config.Configs.InfoOverlays.BLOCK_INFO_OVERLAY_ENABLED.getBooleanValue()) return;
        net.minecraft.world.entity.Entity target = com.autyism.ale.render.EntityInfoOverlay.findTargetedSchematicEntity();
        if (target == null) return;
        com.autyism.ale.render.EntityInfoOverlay.render(ctx, target);
        ci.cancel();
    }

    // 验证器界面打开了“高亮告示牌”：有字的告示牌透墙画框（已放置亮绿、未放置深绿）
    @Inject(method = "renderSchematicVerifierMismatches", at = @At("TAIL"))
    //? if >=26.1 {
    /*private void ale$renderSigns(ProfilerFiller profiler, CallbackInfo ci) {
    *///?} else
    private void ale$renderSigns(Matrix4f posMatrix, Matrix4f projMatrix, ProfilerFiller profiler, CallbackInfo ci) {
        ale$drawSigns();
    }

    @org.spongepowered.asm.mixin.Unique
    private static void ale$drawSigns() {
        if (!com.autyism.ale.verifier.SignHighlight.enabled) return;
        List<BlockPos> placed = com.autyism.ale.verifier.SignHighlight.placed();
        List<BlockPos> missing = com.autyism.ale.verifier.SignHighlight.missing();
        if (placed.isEmpty() && missing.isEmpty()) return;
        RenderContext ctx = ale$context(() -> "autyism-le:sign_highlight", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
        BufferBuilder buffer = ctx.getBuilder();
        Color4f placedColor = Color4f.fromColor(com.autyism.ale.verifier.SignHighlight.placedColor(), 1f);
        Color4f missingColor = Color4f.fromColor(com.autyism.ale.verifier.SignHighlight.missingColor(), 1f);
        // 1.21.11 起线宽写在顶点里；之前是绘制时统一设置
        //? if >=1.21.11 {
        for (BlockPos pos : placed) fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, placedColor, 0.004, 3.0f, buffer);
        for (BlockPos pos : missing) fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, missingColor, 0.004, 3.0f, buffer);
        //?} else {
        /*for (BlockPos pos : placed) fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, placedColor, 0.004, buffer);
        for (BlockPos pos : missing) fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, missingColor, 0.004, buffer);
        *///?}
        try {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                //? if <1.21.11
                //ctx.lineWidth(3.0f);
                ctx.draw(meshData, false, true);
                meshData.close();
            }
            ctx.reset();
            // 半透明的面，隔着墙也一眼能看到
            buffer = ale$start(ctx, () -> "autyism-le:sign_highlight/sides", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
            for (BlockPos pos : placed) fi.dy.masa.malilib.render.RenderUtils.renderAreaSidesBatched(pos, pos, new Color4f(placedColor.r, placedColor.g, placedColor.b, 0.3f), 0.002, buffer);
            for (BlockPos pos : missing) fi.dy.masa.malilib.render.RenderUtils.renderAreaSidesBatched(pos, pos, new Color4f(missingColor.r, missingColor.g, missingColor.b, 0.3f), 0.002, buffer);
            MeshData sides = buffer.build();
            if (sides != null) {
                ctx.draw(sides, false, false);
                sides.close();
            }
            ctx.reset();
            ctx.close();
        } catch (Exception ignored) {
        }
    }

    // malilib 的 RenderContext：26.2 起构造和 start 都多一个参数（Litematica 自己传 0）；1.21.5 只认管线
    //? if >=26.2 {
    /*@org.spongepowered.asm.mixin.Unique
    private static RenderContext ale$context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return new RenderContext(name, pipeline, 0);
    }

    @org.spongepowered.asm.mixin.Unique
    private static BufferBuilder ale$start(RenderContext ctx, java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return ctx.start(name, pipeline, 0);
    }
    *///?} elif >=1.21.6 {
    @org.spongepowered.asm.mixin.Unique
    private static RenderContext ale$context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return new RenderContext(name, pipeline);
    }

    @org.spongepowered.asm.mixin.Unique
    private static BufferBuilder ale$start(RenderContext ctx, java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return ctx.start(name, pipeline);
    }
    //?} else {
    /*@org.spongepowered.asm.mixin.Unique
    private static RenderContext ale$context(java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return new RenderContext(pipeline);
    }

    @org.spongepowered.asm.mixin.Unique
    private static BufferBuilder ale$start(RenderContext ctx, java.util.function.Supplier<String> name, RenderPipeline pipeline) {
        return ctx.start(pipeline);
    }
    *///?}

    @Inject(method = "renderSchematicVerifierMismatches", at = @At("TAIL"))
    // 26.1 起 Litematica 的这个方法只剩 ProfilerFiller 参数（两个矩阵本来也没用到）
    //? if >=26.1 {
    /*private void ale$renderContainerMismatches(ProfilerFiller profiler, CallbackInfo ci) {
    *///?} else
    private void ale$renderContainerMismatches(Matrix4f posMatrix, Matrix4f projMatrix, ProfilerFiller profiler, CallbackInfo ci) {
        SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        if (placement == null || !placement.hasVerifier()) return;
        // 选中的（1.21.11 起在验证器结果列表里选；更早的版本暂时是全部），缓存好的、离玩家近的在前
        List<BlockPos> positions = ContainerVerifier.renderPositions(placement.getSchematicVerifier(),
                fi.dy.masa.litematica.config.Configs.InfoOverlays.VERIFIER_ERROR_HILIGHT_MAX_POSITIONS.getIntegerValue());
        if (positions.isEmpty()) return;
        Color4f color = ContainerVerifier.SELECT_IN_VERIFIER ? Color4f.fromColor(ContainerVerifier.BOX_COLOR, 1f) : ALE_CONTAINER_COLOR;
        //? if >=1.21.11 {
        // 和 Litematica 自己的验证器标记一样：看着的那个框加粗，按设置画半透明的面
        net.minecraft.world.phys.BlockHitResult trace = fi.dy.masa.litematica.util.RayTraceUtils.traceToPositions(positions,
                fi.dy.masa.malilib.util.EntityUtils.getCameraEntity(), 128);
        BlockPos lookPos = trace != null && trace.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? trace.getBlockPos() : null;
        //?}
        //? if >=26.2 {
        /*RenderContext ctx = new RenderContext(() -> "autyism-le:container_mismatches", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0);
        *///?} elif >=1.21.6 {
        RenderContext ctx = new RenderContext(() -> "autyism-le:container_mismatches", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
        //?} else {
        /*RenderContext ctx = new RenderContext(MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL);
        *///?}
        BufferBuilder buffer = ctx.getBuilder();
        for (BlockPos pos : positions) {
            // 1.21.11 起线宽写在顶点里；之前是绘制时统一设置（见下面的 lineWidth）
            //? if >=1.21.11 {
            fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, color, 0.004, pos.equals(lookPos) ? 6.0f : 2.0f, buffer);
            //?} else
            //fi.dy.masa.malilib.render.RenderUtils.drawBlockBoundingBoxOutlinesBatchedLinesSimple(pos, color, 0.004, buffer);
        }
        try {
            MeshData meshData = buffer.build();
            if (meshData != null) {
                //? if <1.21.11
                //ctx.lineWidth(3.0f);
                ctx.draw(meshData, false, true);
                meshData.close();
            }
            ctx.reset();
            //? if >=1.21.11 {
            if (fi.dy.masa.litematica.config.Configs.Visuals.RENDER_ERROR_MARKER_SIDES.getBooleanValue()) {
                float alpha = (float) fi.dy.masa.litematica.config.Configs.InfoOverlays.VERIFIER_ERROR_HILIGHT_ALPHA.getDoubleValue();
                Color4f side = new Color4f(color.r, color.g, color.b, alpha);
                buffer = ale$start(ctx, () -> "autyism-le:container_mismatches/sides", MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT_NO_DEPTH_NO_CULL);
                for (BlockPos pos : positions) fi.dy.masa.malilib.render.RenderUtils.renderAreaSidesBatched(pos, pos, side, 0.002, buffer);
                MeshData sides = buffer.build();
                if (sides != null) {
                    ctx.draw(sides, false, false);
                    sides.close();
                }
                ctx.reset();
            }
            //?}
            ctx.close();
        } catch (Exception ignored) {
        }
    }
}
