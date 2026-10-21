package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 预览的显卡部分（只在渲染线程调用），Minecraft 1.21.5 版：上传网格、把一个预览模型画进离屏纹理。
 * <p>
 * 和之后的版本画的东西一样（原版区段的地形管线、固定的方向光、方块实体用原版的渲染器、半透明层最后按远近排序），
 * 只是 1.21.5 的着色器参数还来自 RenderSystem 的全局状态：投影、模型视图、雾和光照在画的时候临时换成预览自己的，画完换回。
 * 贴图和光照贴图照原版的渲染类型设置（按层开关 mipmap）。
 */
public final class PreviewRenderer {
    private PreviewRenderer() {
    }

    private static final int MAX_SORTED_QUADS = 200_000;
    /** 与主世界相同的两束方向光（世界坐标） */
    private static final Vector3f LIGHT_0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
    private static final Vector3f LIGHT_1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
    /** 方块实体的顶点：每种渲染类型一块，反复使用 */
    private static final Map<RenderType, ByteBufferBuilder> ENTITY_MEMORY = new HashMap<>();

    /** 显卡上的一块网格 */
    public static final class Page implements AutoCloseable {
        final MeshLayer layer;
        final GpuBuffer vertices;
        final int quads;
        final float centerX, centerY, centerZ;
        /** 半透明层：四边形中心与按距离排好的索引 */
        final float[] centroids;
        @Nullable
        GpuBuffer sortedIndices;
        float sortedX = Float.NaN, sortedY, sortedZ;

        Page(MeshLayer layer, GpuBuffer vertices, int quads, float[] centroids, float cx, float cy, float cz) {
            this.layer = layer;
            this.vertices = vertices;
            this.quads = quads;
            this.centroids = centroids;
            this.centerX = cx;
            this.centerY = cy;
            this.centerZ = cz;
        }

        @Override
        public void close() {
            this.vertices.close();
            if (this.sortedIndices != null) this.sortedIndices.close();
        }
    }

    /** 一个要画的方块实体（1.21.8 及更早没有渲染状态，每次都用方块实体本身画，见 {@link LegacyBlockEntities}） */
    public static final class BlockEntityDraw {
        final float x, y, z;
        @Nullable
        BlockEntity blockEntity;
        boolean failed;

        BlockEntityDraw(int x, int y, int z, BlockEntity blockEntity) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockEntity = blockEntity;
        }
    }

    /** 把后台生成的一层顶点上传成显卡缓冲；调用后 sink 的内存仍归调用者释放 */
    static Page upload(MeshLayer layer, MeshVertexSink sink, float[] centroids, float cx, float cy, float cz) {
        ByteBuffer data = sink.finish();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "ALE schematic preview", BufferType.VERTICES, BufferUsage.STATIC_WRITE, data);
        return new Page(layer, buffer, sink.quadCount(), layer == MeshLayer.TRANSLUCENT ? centroids : new float[0], cx, cy, cz);
    }

    /** 原版区段画这一层用的渲染类型（管线、图集的 mipmap、光照贴图） */
    private static RenderType renderType(MeshLayer layer) {
        return switch (layer) {
            case SOLID -> RenderType.solid();
            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
            case CUTOUT -> RenderType.cutout();
            case TRIPWIRE -> RenderType.tripwire();
            // 区段的半透明层只画在主画面上（“极佳”画质时有单独的输出）；同样的方块用移动方块的半透明类型
            case TRANSLUCENT -> RenderType.translucentMovingBlock();
        };
    }

    /** 实心、镂空和绊线层（半透明层最后单独画），按这个顺序 */
    private static final MeshLayer[] OPAQUE_LAYERS = {MeshLayer.SOLID, MeshLayer.CUTOUT_MIPPED, MeshLayer.CUTOUT, MeshLayer.TRIPWIRE};

    /** 把模型按相机画进目标纹理（会先清空目标） */
    public static void render(PreviewModel model, PreviewCamera camera, PreviewTarget target, boolean blockEntities) {
        RenderSystem.assertOnRenderThread();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(target.colorTexture(), 0, target.depthTexture(), 1.0);
        List<Page> pages = model.pages();
        if (pages.isEmpty() && model.blockEntityDraws().isEmpty()) return;

        camera.fitTo(target.aspect());
        Matrix4f projection = camera.projection(target.aspect());
        Matrix4f view = camera.viewRotation();
        Vector3f eye = camera.eye();

        FogParameters oldFog = RenderSystem.getShaderFog();
        Vector3f[] lights = RenderSystem.getShaderLights();
        Vector3f oldLight0 = lights[0], oldLight1 = lights[1];
        // 界面有时把着色颜色改成半透明或带颜色（淡入之类）：预览按原色画
        float[] color = RenderSystem.getShaderColor();
        float oldR = color[0], oldG = color[1], oldB = color[2], oldA = color[3];
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(projection, ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(FogParameters.NO_FOG);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            // 方块：相机平移放进模型视图矩阵（半透明方块的着色器不加 ModelOffset）
            modelView.set(view).translate(-eye.x, -eye.y, -eye.z);
            drawOpaque(encoder, pages, target);

            if (blockEntities && !model.blockEntityDraws().isEmpty()) {
                // 方块实体：平移在各自的 PoseStack 里
                modelView.set(view);
                RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);
                drawBlockEntities(model, target, eye);
                modelView.set(view).translate(-eye.x, -eye.y, -eye.z);
            }

            drawTranslucent(encoder, pages, target, camera, eye);
        } finally {
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            if (oldFog != null) RenderSystem.setShaderFog(oldFog);
            RenderSystem.setShaderLights(oldLight0, oldLight1);
            RenderSystem.setShaderColor(oldR, oldG, oldB, oldA);
        }
    }

    private static void drawOpaque(CommandEncoder encoder, List<Page> pages, PreviewTarget target) {
        int maxIndices = 0;
        for (Page p : pages) if (p.layer != MeshLayer.TRANSLUCENT) maxIndices = Math.max(maxIndices, p.quads * 6);
        if (maxIndices == 0) return;
        RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = quadIndices.getBuffer(maxIndices);
        for (MeshLayer layer : OPAQUE_LAYERS) {
            boolean any = false;
            for (Page p : pages) any |= p.layer == layer && p.quads > 0;
            if (!any) continue;
            RenderType type = renderType(layer);
            type.setupRenderState();
            try (RenderPass pass = openPass(encoder, target, type.getRenderPipeline())) {
                pass.setIndexBuffer(indexBuffer, quadIndices.type());
                for (Page p : pages) {
                    if (p.layer != layer || p.quads == 0) continue;
                    pass.setVertexBuffer(0, p.vertices);
                    pass.drawIndexed(0, p.quads * 6);
                }
            } finally {
                type.clearRenderState();
            }
        }
    }

    private static void drawTranslucent(CommandEncoder encoder, List<Page> pages, PreviewTarget target, PreviewCamera camera, Vector3f eye) {
        List<Page> translucent = new ArrayList<>();
        for (Page p : pages) if (p.layer == MeshLayer.TRANSLUCENT && p.quads > 0) translucent.add(p);
        if (translucent.isEmpty()) return;
        translucent.sort((a, b) -> Float.compare(dist2(b, eye), dist2(a, eye)));
        // 排序要写显卡缓冲，必须在打开绘制之前做完
        float threshold = Math.max(0.5F, camera.distance() * 0.02F);
        int unsortedIndices = 0;
        for (Page p : translucent) {
            sortIfNeeded(encoder, p, eye, threshold);
            if (p.sortedIndices == null) unsortedIndices = Math.max(unsortedIndices, p.quads * 6);
        }
        RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer plainIndices = unsortedIndices > 0 ? quadIndices.getBuffer(unsortedIndices) : null;
        RenderType type = renderType(MeshLayer.TRANSLUCENT);
        type.setupRenderState();
        try (RenderPass pass = openPass(encoder, target, type.getRenderPipeline())) {
            for (Page p : translucent) {
                pass.setVertexBuffer(0, p.vertices);
                if (p.sortedIndices != null) {
                    pass.setIndexBuffer(p.sortedIndices, VertexFormat.IndexType.INT);
                } else {
                    pass.setIndexBuffer(plainIndices, quadIndices.type());
                }
                pass.drawIndexed(0, p.quads * 6);
            }
        } finally {
            type.clearRenderState();
        }
    }

    /**
     * 画进目标纹理的绘制（不清空，清空在前面做过）：管线、渲染类型设置好的贴图；ModelOffset 为 0。
     * 不用界面的裁剪框（那是屏幕上的坐标）
     */
    private static RenderPass openPass(CommandEncoder encoder, PreviewTarget target, RenderPipeline pipeline) {
        RenderPass pass = encoder.createRenderPass(target.colorTexture(), OptionalInt.empty(), target.depthTexture(), OptionalDouble.empty());
        pass.setPipeline(pipeline);
        for (int i = 0; i < 12; i++) {
            GpuTexture texture = RenderSystem.getShaderTexture(i);
            if (texture != null) pass.bindSampler("Sampler" + i, texture);
        }
        pass.setUniform("ModelOffset", 0.0F, 0.0F, 0.0F);
        return pass;
    }

    private static float dist2(Page p, Vector3f eye) {
        float dx = p.centerX - eye.x, dy = p.centerY - eye.y, dz = p.centerZ - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 半透明四边形按离相机从远到近排序（相机移动超过一点才重新排；太多的不排） */
    private static void sortIfNeeded(CommandEncoder encoder, Page p, Vector3f eye, float threshold) {
        if (p.quads > MAX_SORTED_QUADS) return;
        if (!Float.isNaN(p.sortedX)) {
            float dx = p.sortedX - eye.x, dy = p.sortedY - eye.y, dz = p.sortedZ - eye.z;
            if (dx * dx + dy * dy + dz * dz < threshold * threshold) return;
        }
        int quads = p.quads;
        // 距离的平方是正数，浮点位模式的大小顺序与数值一致：高 32 位放距离，低 32 位放序号，一次排序即可
        long[] keys = new long[quads];
        for (int q = 0; q < quads; q++) {
            float dx = p.centroids[q * 3] - eye.x, dy = p.centroids[q * 3 + 1] - eye.y, dz = p.centroids[q * 3 + 2] - eye.z;
            keys[q] = ((long) Float.floatToRawIntBits(dx * dx + dy * dy + dz * dz) << 32) | q;
        }
        Arrays.sort(keys);
        ByteBuffer data = MemoryUtil.memAlloc(quads * 6 * 4);
        try {
            for (int i = quads - 1; i >= 0; i--) {
                int base = (int) keys[i] * 4;
                data.putInt(base).putInt(base + 1).putInt(base + 2).putInt(base + 2).putInt(base + 3).putInt(base);
            }
            data.flip();
            if (p.sortedIndices == null) {
                p.sortedIndices = RenderSystem.getDevice().createBuffer(() -> "ALE schematic preview (sorted)",
                        BufferType.INDICES, BufferUsage.DYNAMIC_WRITE, data);
            } else {
                encoder.writeToBuffer(p.sortedIndices, data, 0);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
        p.sortedX = eye.x;
        p.sortedY = eye.y;
        p.sortedZ = eye.z;
    }

    /**
     * 方块实体：原版的渲染器写进预览自己的缓冲（每种渲染类型一份），再照原版画渲染类型的方式画进预览纹理。
     * 1.21.5 的渲染类型画的时候自己决定画到哪里（主画面），所以不交给它画
     */
    private static void drawBlockEntities(PreviewModel model, PreviewTarget target, Vector3f eye) {
        Minecraft mc = Minecraft.getInstance();
        Collector collector = new Collector();
        try {
            LegacyBlockEntities.render(model, mc.getBlockEntityRenderDispatcher(), eye, collector);
            collector.drawInto(target);
        } catch (Throwable t) {
            // 某个方块实体在绘制阶段出错：这个预览以后不再画方块实体，其余照常
            collector.discard();
            model.disableBlockEntities();
            AleMod.LOGGER.warn("Schematic preview: block entities could not be drawn and are skipped for this schematic", t);
        }
    }

    /** 按渲染类型收集方块实体的顶点 */
    private static final class Collector implements MultiBufferSource {
        private final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return this.builders.computeIfAbsent(type, t -> new BufferBuilder(
                    ENTITY_MEMORY.computeIfAbsent(t, k -> new ByteBufferBuilder(k.bufferSize())), t.mode(), t.format()));
        }

        void drawInto(PreviewTarget target) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            for (Map.Entry<RenderType, BufferBuilder> e : this.builders.entrySet()) {
                MeshData mesh = e.getValue().build();
                if (mesh != null) draw(encoder, e.getKey(), mesh, target);
            }
            this.builders.clear();
        }

        void discard() {
            for (BufferBuilder builder : this.builders.values()) {
                try {
                    MeshData mesh = builder.build();
                    if (mesh != null) mesh.close();
                } catch (RuntimeException ignored) {
                    // 写到一半的顶点：内存下次重新用
                }
            }
            this.builders.clear();
            for (ByteBufferBuilder memory : ENTITY_MEMORY.values()) memory.discard();
        }

        /** 和原版画一个渲染类型的网格一样（上传顶点、索引，设置贴图），只是画进预览纹理 */
        private static void draw(CommandEncoder encoder, RenderType type, MeshData mesh, PreviewTarget target) {
            RenderPipeline pipeline = type.getRenderPipeline();
            type.setupRenderState();
            try (mesh) {
                GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
                GpuBuffer indices;
                VertexFormat.IndexType indexType;
                if (mesh.indexBuffer() == null) {
                    RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
                    indices = sequential.getBuffer(mesh.drawState().indexCount());
                    indexType = sequential.type();
                } else {
                    indices = pipeline.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
                    indexType = mesh.drawState().indexType();
                }
                try (RenderPass pass = encoder.createRenderPass(target.colorTexture(), OptionalInt.empty(), target.depthTexture(), OptionalDouble.empty())) {
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, vertices);
                    for (int i = 0; i < 12; i++) {
                        GpuTexture texture = RenderSystem.getShaderTexture(i);
                        if (texture != null) pass.bindSampler("Sampler" + i, texture);
                    }
                    pass.setIndexBuffer(indices, indexType);
                    pass.drawIndexed(0, mesh.drawState().indexCount());
                }
            } finally {
                type.clearRenderState();
            }
        }
    }
}
