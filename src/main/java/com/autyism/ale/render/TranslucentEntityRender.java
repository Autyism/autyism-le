package com.autyism.ale.render;

import com.autyism.ale.config.AleConfigs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fi.dy.masa.litematica.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 需求 5b：投影里的实体渲染成和投影方块一样的半透明效果（透明度 = Litematica 的 ghostBlockAlpha）。
 * <p>
 * Litematica 把投影实体提交到原版的 {@link SubmitNodeCollector}。这里给它套一层代理：
 * <ul>
 *     <li>模型（实体身体、盔甲架、矿车、船、箱子等）：渲染类型换成 entityTranslucent，颜色乘上透明度；</li>
 *     <li>方块模型 / 方块 / 物品（物品展示框的框和里面的物品、矿车里的方块）：改成自定义几何体，用半透明渲染类型，
 *     顶点颜色乘上透明度；</li>
 *     <li>其余（影子、名字、粒子等）原样提交。</li>
 * </ul>
 * 实体渲染器在提交过程中创建的不透明实体渲染类型，会在 {@link #ACTIVE} 期间被替换为半透明版本（见 RenderTypesMixin）。
 */
public final class TranslucentEntityRender {
    private TranslucentEntityRender() {
    }

    /** 正在提交投影实体（RenderTypesMixin 据此把不透明的实体渲染类型换成半透明） */
    public static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    public static boolean enabledForEntities() {
        return AleConfigs.Generic.TRANSLUCENT_ENTITIES.getBooleanValue();
    }

    /** 方块实体（箱子、告示牌…）只在投影方块本身是半透明模式时才跟着半透明，保持与方块一致 */
    public static boolean enabledForBlockEntities() {
        return AleConfigs.Generic.TRANSLUCENT_ENTITIES.getBooleanValue() && Configs.Visuals.RENDER_BLOCKS_AS_TRANSLUCENT.getBooleanValue();
    }

    public static float alpha() {
        return (float) Math.max(0.05, Math.min(1.0, Configs.Visuals.GHOST_BLOCK_ALPHA.getDoubleValue()));
    }

    /** 与投影方块一样的“幽灵”色调：把 Litematica“缺失方块”覆盖色（默认蓝色）和白色按 45% 混合后乘到颜色上 */
    private static final float GHOST_MIX = 0.45f;

    static float[] tint() {
        int c = Configs.Colors.SCHEMATIC_OVERLAY_COLOR_MISSING.getIntegerValue();
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        return new float[]{1 - GHOST_MIX + GHOST_MIX * r, 1 - GHOST_MIX + GHOST_MIX * g, 1 - GHOST_MIX + GHOST_MIX * b};
    }

    /** 颜色乘上幽灵色调并降低透明度 */
    static int mulAlpha(int argb, float alpha) {
        float[] t = tint();
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * t[0]), g = Math.round(((argb >> 8) & 0xFF) * t[1]), b = Math.round((argb & 0xFF) * t[2]);
        return (Math.round(a * alpha) << 24) | (r << 16) | (g << 8) | b;
    }

    public static SubmitNodeCollector wrap(SubmitNodeCollector queue) {
        return (SubmitNodeCollector) Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{SubmitNodeCollector.class}, new Handler(queue));
    }

    private static OrderedSubmitNodeCollector wrapOrdered(OrderedSubmitNodeCollector queue) {
        return (OrderedSubmitNodeCollector) Proxy.newProxyInstance(OrderedSubmitNodeCollector.class.getClassLoader(),
                new Class<?>[]{OrderedSubmitNodeCollector.class}, new Handler(queue));
    }

    private record Handler(OrderedSubmitNodeCollector target) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            float alpha = alpha();
            // 不能按方法名分派：正式环境里方法名是混淆后的 method_xxxxx。按参数类型识别
            String name = KINDS.computeIfAbsent(method, TranslucentEntityRender::kindOf);
            try {
                switch (name) {
                    case "order" -> {
                        return wrapOrdered((OrderedSubmitNodeCollector) method.invoke(target, args));
                    }
                    case "submitModel" -> {
                        // 10 参数：(model, state, pose, rt, light, overlay, tint, sprite, outline, crumbling)
                        // 8 参数默认方法：(model, state, pose, rt, light, overlay, outline, crumbling)，tint 固定为 -1
                        RenderType rt = TranslucentRenderTypes.translucent((RenderType) args[3]);
                        int light = (Integer) args[4], overlay = (Integer) args[5];
                        int tint, outline;
                        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite;
                        net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling;
                        if (args.length == 10) {
                            tint = (Integer) args[6];
                            sprite = (net.minecraft.client.renderer.texture.TextureAtlasSprite) args[7];
                            outline = (Integer) args[8];
                            crumbling = (net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay) args[9];
                        } else {
                            tint = -1;
                            sprite = null;
                            outline = (Integer) args[6];
                            crumbling = (net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay) args[7];
                        }
                        submitModelRaw(target, args[0], args[1], (PoseStack) args[2], rt, light, overlay, mulAlpha(tint, alpha), sprite, outline, crumbling);
                        return null;
                    }
                    case "submitModelPart" -> {
                        // 统一转成参数最全的版本：(part, pose, rt, light, overlay, sprite, sheeted, hasFoil, color, crumbling, outline)
                        Object part = args[0];
                        PoseStack pose = (PoseStack) args[1];
                        RenderType rt = TranslucentRenderTypes.translucent((RenderType) args[2]);
                        int light = (Integer) args[3], overlay = (Integer) args[4];
                        var sprite = (net.minecraft.client.renderer.texture.TextureAtlasSprite) args[5];
                        boolean sheeted = false, foil = false;
                        int color = -1, outline = 0;
                        net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling = null;
                        if (args.length == 8 && args[6] instanceof Boolean b1) {
                            sheeted = b1;
                            foil = (Boolean) args[7];
                        } else if (args.length == 8) {
                            color = (Integer) args[6];
                            crumbling = (net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay) args[7];
                        } else if (args.length == 11) {
                            sheeted = (Boolean) args[6];
                            foil = (Boolean) args[7];
                            color = (Integer) args[8];
                            crumbling = (net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay) args[9];
                            outline = (Integer) args[10];
                        }
                        target.submitModelPart((net.minecraft.client.model.geom.ModelPart) part, pose, rt, light, overlay, sprite,
                                sheeted, foil, mulAlpha(color, alpha), crumbling, outline);
                        return null;
                    }
                    //? if >=26.1 {
                    /*case "submitBlockParts" -> {
                        // 26.1：(pose, rt, parts, tints, light, overlay, outline)。方块模型用方块图集，换成方块图集的半透明类型
                        PoseStack pose = (PoseStack) args[0];
                        @SuppressWarnings("unchecked")
                        List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts =
                                (List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>) args[2];
                        int[] tints = (int[]) args[3];
                        int light = (Integer) args[4], overlay = (Integer) args[5];
                        List<BakedQuad> quads = new java.util.ArrayList<>();
                        for (var part : parts) {
                            for (net.minecraft.core.Direction d : QUAD_SIDES) quads.addAll(part.getQuads(d));
                        }
                        target.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(),
                                (p, consumer) -> putQuads(p, consumer, quads, tints, alpha, light, overlay));
                        return null;
                    }
                    case "submitItemQuads" -> {
                        // 26.1：(pose, ctx, light, overlay, outline, tints, quads, foil)，渲染类型在每个面的材质里：按贴图所在图集分组
                        PoseStack pose = (PoseStack) args[0];
                        int light = (Integer) args[2], overlay = (Integer) args[3];
                        int[] tints = (int[]) args[5];
                        @SuppressWarnings("unchecked")
                        List<BakedQuad> quads = (List<BakedQuad>) args[6];
                        java.util.Map<RenderType, List<BakedQuad>> byType = new java.util.LinkedHashMap<>();
                        for (BakedQuad quad : quads) byType.computeIfAbsent(itemSheetFor(quad), k -> new java.util.ArrayList<>()).add(quad);
                        for (var e : byType.entrySet()) {
                            List<BakedQuad> group = e.getValue();
                            target.submitCustomGeometry(pose, e.getKey(), (p, consumer) -> putQuads(p, consumer, group, tints, alpha, light, overlay));
                        }
                        return null;
                    }
                    *///?} else {
                    case "submitBlockModel" -> {
                        // (pose, rt, model, r, g, b, light, overlay, outline)
                        PoseStack pose = (PoseStack) args[0];
                        BlockStateModel model = (BlockStateModel) args[2];
                        float r = (Float) args[3], g = (Float) args[4], b = (Float) args[5];
                        int light = (Integer) args[6], overlay = (Integer) args[7];
                        // 方块模型用的是方块图集：必须用方块图集的半透明类型，否则贴图错位（展示框变紫）
                        target.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(),
                                (p, consumer) -> ModelBlockRenderer.renderModel(p, new AlphaConsumer(consumer, alpha), model, r, g, b, light, overlay));
                        return null;
                    }
                    case "submitBlockStateModel" -> {
                        // Fabric 渲染 API：(pose, layerFunction, model, r, g, b, light, overlay, outline, blockView, pos, state)
                        PoseStack pose = (PoseStack) args[0];
                        BlockStateModel model = (BlockStateModel) args[2];
                        float r = (Float) args[3], g = (Float) args[4], b = (Float) args[5];
                        int light = (Integer) args[6], overlay = (Integer) args[7];
                        // 方块模型用的是方块图集：必须用方块图集的半透明类型，否则贴图错位（展示框变紫）
                        target.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(),
                                (p, consumer) -> ModelBlockRenderer.renderModel(p, new AlphaConsumer(consumer, alpha), model, r, g, b, light, overlay));
                        return null;
                    }
                    case "submitBlock" -> {
                        // (pose, state, light, overlay, outline)
                        PoseStack pose = (PoseStack) args[0];
                        BlockState state = (BlockState) args[1];
                        int light = (Integer) args[2], overlay = (Integer) args[3];
                        BlockStateModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                        target.submitCustomGeometry(pose, Sheets.translucentBlockItemSheet(),
                                (p, consumer) -> ModelBlockRenderer.renderModel(p, new AlphaConsumer(consumer, alpha), model, 1f, 1f, 1f, light, overlay));
                        return null;
                    }
                    case "submitItem" -> {
                        // (pose, ctx, light, overlay, outline, tints, quads, rt, foil)
                        PoseStack pose = (PoseStack) args[0];
                        int light = (Integer) args[2], overlay = (Integer) args[3];
                        int[] tints = (int[]) args[5];
                        @SuppressWarnings("unchecked")
                        List<BakedQuad> quads = (List<BakedQuad>) args[6];
                        // 物品贴图可能在物品图集也可能在方块图集：按原渲染类型的贴图选对应的半透明类型
                        RenderType itemRt = itemSheetFor((RenderType) args[7]);
                        float[] gt = tint();
                        target.submitCustomGeometry(pose, itemRt, (p, consumer) -> {
                            for (BakedQuad quad : quads) {
                                int tint = quad.tintIndex() >= 0 && tints != null && quad.tintIndex() < tints.length ? tints[quad.tintIndex()] : -1;
                                float r = ((tint >> 16) & 0xFF) / 255f * gt[0], g = ((tint >> 8) & 0xFF) / 255f * gt[1], b = (tint & 0xFF) / 255f * gt[2];
                                consumer.putBulkData(p, quad, r, g, b, alpha, light, overlay);
                            }
                        });
                        return null;
                    }
                    //?}
                    case "submitCustomGeometry" -> {
                        PoseStack pose = (PoseStack) args[0];
                        RenderType rt = (RenderType) args[1];
                        SubmitNodeCollector.CustomGeometryRenderer renderer = (SubmitNodeCollector.CustomGeometryRenderer) args[2];
                        target.submitCustomGeometry(pose, rt, (p, consumer) -> renderer.render(p, new AlphaConsumer(consumer, alpha)));
                        return null;
                    }
                    default -> {
                        //? if >=26.1 {
                        /*// 默认方法（26.1 按贴图提交模型、Fabric 带网格的提交等）在代理上执行自己的方法体，里面再调用的抽象方法会回到这里
                        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                        *///?}
                        if (method.isDefault() && method.getDeclaringClass().isInterface() && !Proxy.isProxyClass(target.getClass())) {
                            return method.invoke(target, args);
                        }
                        return method.invoke(target, args);
                    }
                }
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    //? if >=26.1 {
    /*private static RenderType itemSheetFor(BakedQuad quad) {
        var sprite = quad.materialInfo().sprite();
        return sprite != null && net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation())
                ? Sheets.translucentBlockItemSheet() : Sheets.translucentItemSheet();
    }

    private static final net.minecraft.core.Direction[] QUAD_SIDES = {null, net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP,
            net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST};

    // 26.1：面写进顶点流，颜色 = 染色 × 幽灵色调，透明度降低
    private static void putQuads(PoseStack.Pose pose, VertexConsumer consumer, List<BakedQuad> quads, int[] tints, float alpha, int light, int overlay) {
        com.mojang.blaze3d.vertex.QuadInstance instance = new com.mojang.blaze3d.vertex.QuadInstance();
        for (BakedQuad quad : quads) {
            int index = quad.materialInfo().tintIndex();
            int tint = index >= 0 && tints != null && index < tints.length ? tints[index] : -1;
            instance.setColor(mulAlpha(tint | 0xFF000000, alpha));
            instance.setLightCoords(light);
            instance.setOverlayCoords(overlay);
            consumer.putBakedQuad(pose, quad, instance);
        }
    }
    *///?} else {
    private static RenderType itemSheetFor(RenderType original) {
        try {
            if (original != null && !original.state.textures.isEmpty()
                    && net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS.equals(original.state.textures.values().iterator().next().location())) {
                return Sheets.translucentBlockItemSheet();
            }
        } catch (Throwable ignored) {
        }
        return Sheets.translucentItemSheet();
    }
    //?}

    private static final java.util.Map<Method, String> KINDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 按参数类型识别 SubmitNodeCollector 的方法（开发环境和正式环境都适用） */
    private static String kindOf(Method m) {
        Class<?>[] p = m.getParameterTypes();
        if (p.length == 1 && p[0] == int.class && OrderedSubmitNodeCollector.class.isAssignableFrom(m.getReturnType())) return "order";
        if (p.length == 0) return "other";
        if (net.minecraft.client.model.Model.class.isAssignableFrom(p[0]) && (p.length == 8 || p.length == 10) && p[3] == RenderType.class) return "submitModel";
        if (p[0] == net.minecraft.client.model.geom.ModelPart.class && p.length >= 6) return "submitModelPart";
        if (p[0] == PoseStack.class && p.length >= 3) {
            //? if >=26.1 {
            /*if (p.length == 7 && p[1] == RenderType.class && List.class.isAssignableFrom(p[2]) && p[3] == int[].class) return "submitBlockParts";
            if (p.length == 8 && p[1] == net.minecraft.world.item.ItemDisplayContext.class && p[5] == int[].class && List.class.isAssignableFrom(p[6])) return "submitItemQuads";
            *///?}
            if (p.length == 3 && p[1] == RenderType.class && SubmitNodeCollector.CustomGeometryRenderer.class.isAssignableFrom(p[2])) return "submitCustomGeometry";
            if (p.length >= 8 && BlockStateModel.class.isAssignableFrom(p[2]) && p[3] == float.class) {
                return p[1] == RenderType.class ? "submitBlockModel" : "submitBlockStateModel";
            }
            if (p.length == 5 && p[1] == BlockState.class) return "submitBlock";
            if (p.length == 9 && p[1] == net.minecraft.world.item.ItemDisplayContext.class && p[5] == int[].class && List.class.isAssignableFrom(p[6]) && p[7] == RenderType.class) return "submitItem";
        }
        return "other";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void submitModelRaw(OrderedSubmitNodeCollector target, Object model, Object state, PoseStack pose, RenderType rt,
                                       int light, int overlay, int tint, net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                       int outline, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
        target.submitModel((net.minecraft.client.model.Model) model, state, pose, rt, light, overlay, tint, sprite, outline, crumbling);
    }

    /** 顶点颜色乘上透明度 */
    public record AlphaConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            float[] t = tint();
            delegate.setColor(Math.round(r * t[0]), Math.round(g * t[1]), Math.round(b * t[2]), Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            delegate.setColor(mulAlpha(argb, alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            delegate.addVertex(x, y, z, mulAlpha(color, alpha), u, v, overlay, light, nx, ny, nz);
        }
    }
}
