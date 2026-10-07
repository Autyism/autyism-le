package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * 预览的显卡部分（只在渲染线程调用）：上传网格、把一个预览模型画进离屏纹理。
 * <ul>
 *     <li>方块网格：原版“方块”渲染管线（与活塞推动中的方块相同），自己的投影矩阵、无雾、固定的方向光；</li>
 *     <li>方块实体：原版的方块实体渲染器，输出改到离屏纹理；某个方块实体画不出来就跳过它；</li>
 *     <li>半透明层（水、玻璃）最后画，按离相机的远近排序。</li>
 * </ul>
 * 各 Minecraft 版本之间渲染接口的差别集中在这个类和 {@link PreviewTarget}。
 */
public final class PreviewRenderer {
    private PreviewRenderer() {
    }

    private static final int MAX_SORTED_QUADS = 200_000;

    @Nullable
    private static PerspectiveProjectionMatrixBuffer projectionBuffer;
    @Nullable
    private static GpuBuffer noFogBuffer;
    @Nullable
    private static GpuBuffer lightsBuffer;

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

    /** 一个要画的方块实体（渲染状态在第一次画的时候在渲染线程取出） */
    public static final class BlockEntityDraw {
        final float x, y, z;
        @Nullable
        BlockEntity blockEntity;
        @Nullable
        BlockEntityRenderState state;
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
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "ALE schematic preview", GpuBuffer.USAGE_VERTEX, data);
        return new Page(layer, buffer, sink.quadCount(), layer == MeshLayer.TRANSLUCENT ? centroids : new float[0], cx, cy, cz);
    }

    private static RenderPipeline pipeline(MeshLayer layer) {
        return switch (layer) {
            case SOLID -> RenderPipelines.SOLID_BLOCK;
            case CUTOUT -> RenderPipelines.CUTOUT_BLOCK;
            case TRIPWIRE -> RenderPipelines.TRIPWIRE_BLOCK;
            case TRANSLUCENT -> RenderPipelines.TRANSLUCENT_MOVING_BLOCK;
        };
    }

    /** 把模型按相机画进目标纹理（会先清空目标） */
    public static void render(PreviewModel model, PreviewCamera camera, PreviewTarget target, boolean blockEntities) {
        RenderSystem.assertOnRenderThread();
        GpuDevice device = RenderSystem.getDevice();
        CommandEncoder encoder = device.createCommandEncoder();
        encoder.clearColorAndDepthTextures(target.colorTexture(), 0, target.depthTexture(), 1.0);
        List<Page> pages = model.pages();
        if (pages.isEmpty() && model.blockEntityDraws().isEmpty()) return;
        ensureStaticBuffers();

        camera.fitTo(target.aspect());
        Matrix4f projection = camera.projection(target.aspect());
        Matrix4f view = camera.viewRotation();
        Vector3f eye = camera.eye();
        GpuBufferSlice projectionSlice = projectionBuffer.getBuffer(projection);
        GpuBufferSlice fogSlice = noFogBuffer.slice(0L, FogRenderer.FOG_UBO_SIZE);
        // 相机平移放进模型视图矩阵（半透明方块的着色器不加 ModelOffset）
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f(view).translate(-eye.x, -eye.y, -eye.z), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
        Minecraft mc = Minecraft.getInstance();
        GpuTextureView atlas = mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getTextureView();
        GpuSampler atlasSampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST, true);
        GpuTextureView lightmap = mc.gameRenderer.lightTexture().getTextureView();
        GpuSampler lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        int maxIndices = 0;
        for (Page p : pages) if (p.layer != MeshLayer.TRANSLUCENT) maxIndices = Math.max(maxIndices, p.quads * 6);
        if (maxIndices > 0) {
            RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            GpuBuffer indexBuffer = quadIndices.getBuffer(maxIndices);
            try (RenderPass pass = encoder.createRenderPass(() -> "ALE schematic preview", target.colorView(), OptionalInt.empty(),
                    target.depthView(), OptionalDouble.empty())) {
                pass.setPipeline(RenderPipelines.SOLID_BLOCK);
                pass.setUniform("Projection", projectionSlice);
                pass.setUniform("Fog", fogSlice);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", atlas, atlasSampler);
                pass.bindTexture("Sampler2", lightmap, lightSampler);
                pass.setIndexBuffer(indexBuffer, quadIndices.type());
                for (MeshLayer layer : new MeshLayer[]{MeshLayer.SOLID, MeshLayer.CUTOUT, MeshLayer.TRIPWIRE}) {
                    pass.setPipeline(pipeline(layer));
                    for (Page p : pages) {
                        if (p.layer != layer || p.quads == 0) continue;
                        pass.setVertexBuffer(0, p.vertices);
                        pass.drawIndexed(0, 0, p.quads * 6, 1);
                    }
                }
            }
        }

        if (blockEntities && !model.blockEntityDraws().isEmpty()) {
            drawBlockEntities(model, target, projectionSlice, fogSlice, view, eye, camera);
        }

        List<Page> translucent = new ArrayList<>();
        for (Page p : pages) if (p.layer == MeshLayer.TRANSLUCENT && p.quads > 0) translucent.add(p);
        if (!translucent.isEmpty()) {
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
            try (RenderPass pass = encoder.createRenderPass(() -> "ALE schematic preview (translucent)", target.colorView(), OptionalInt.empty(),
                    target.depthView(), OptionalDouble.empty())) {
                pass.setPipeline(pipeline(MeshLayer.TRANSLUCENT));
                pass.setUniform("Projection", projectionSlice);
                pass.setUniform("Fog", fogSlice);
                pass.setUniform("DynamicTransforms", transforms);
                pass.bindTexture("Sampler0", atlas, atlasSampler);
                pass.bindTexture("Sampler2", lightmap, lightSampler);
                for (Page p : translucent) {
                    pass.setVertexBuffer(0, p.vertices);
                    if (p.sortedIndices != null) {
                        pass.setIndexBuffer(p.sortedIndices, VertexFormat.IndexType.INT);
                    } else {
                        pass.setIndexBuffer(plainIndices, quadIndices.type());
                    }
                    pass.drawIndexed(0, 0, p.quads * 6, 1);
                }
            }
        }
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
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, data);
            } else {
                encoder.writeToBuffer(p.sortedIndices.slice(), data);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
        p.sortedX = eye.x;
        p.sortedY = eye.y;
        p.sortedZ = eye.z;
    }

    private static void drawBlockEntities(PreviewModel model, PreviewTarget target, GpuBufferSlice projection, GpuBufferSlice fog,
                                          Matrix4f view, Vector3f eye, PreviewCamera previewCamera) {
        Minecraft mc = Minecraft.getInstance();
        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        FeatureRenderDispatcher features = mc.gameRenderer.getFeatureRenderDispatcher();
        CameraRenderState camera = new CameraRenderState();
        camera.initialized = true;
        camera.pos = new Vec3(eye.x, eye.y, eye.z);
        camera.entityPos = camera.pos;
        camera.orientation = new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(previewCamera.yaw()),
                -(float) Math.toRadians(previewCamera.pitch()), 0.0F);
        PoseStack pose = new PoseStack();
        int submitted = 0;
        for (BlockEntityDraw draw : model.blockEntityDraws()) {
            if (draw.failed) continue;
            if (draw.state == null && !extract(dispatcher, draw, camera.pos)) continue;
            pose.pushPose();
            pose.translate(draw.x - eye.x, draw.y - eye.y, draw.z - eye.z);
            try {
                dispatcher.submit(draw.state, pose, features.getSubmitNodeStorage(), camera);
                submitted++;
            } catch (Throwable t) {
                draw.failed = true;
                AleMod.LOGGER.debug("Schematic preview skipped a block entity", t);
            }
            pose.popPose();
        }
        if (submitted == 0) return;

        // 方块实体走原版的渲染类型：投影、雾、光照、模型视图和输出目标都临时换成预览自己的
        GpuBufferSlice oldFog = RenderSystem.getShaderFog();
        GpuBufferSlice oldLights = RenderSystem.getShaderLights();
        ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
        boolean scissorOn = scissor.enabled();
        int sx = scissor.x(), sy = scissor.y(), sw = scissor.width(), sh = scissor.height();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(projection, ProjectionType.PERSPECTIVE);
            RenderSystem.setShaderFog(fog);
            RenderSystem.setShaderLights(lightsBuffer.slice(0L, Lighting.UBO_SIZE));
            if (scissorOn) RenderSystem.disableScissorForRenderTypeDraws();
            modelView.set(view);
            RenderSystem.outputColorTextureOverride = target.colorView();
            RenderSystem.outputDepthTextureOverride = target.depthView();
            features.renderAllFeatures();
            mc.renderBuffers().bufferSource().endBatch();
        } catch (Throwable t) {
            // 某个方块实体在绘制阶段出错：这个预览以后不再画方块实体，其余照常
            model.disableBlockEntities();
            AleMod.LOGGER.warn("Schematic preview: block entities could not be drawn and are skipped for this schematic", t);
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            if (oldFog != null) RenderSystem.setShaderFog(oldFog);
            if (oldLights != null) RenderSystem.setShaderLights(oldLights);
            if (scissorOn) RenderSystem.enableScissorForRenderTypeDraws(sx, sy, sw, sh);
        }
    }

    private static boolean extract(BlockEntityRenderDispatcher dispatcher, BlockEntityDraw draw, Vec3 cameraPos) {
        BlockEntity be = draw.blockEntity;
        if (be == null) {
            draw.failed = true;
            return false;
        }
        try {
            BlockEntityRenderState state = extractState(dispatcher, be, cameraPos);
            if (state == null) {
                draw.failed = true;
                return false;
            }
            // 预览里的方块实体没有世界：箱子会按“物品栏里的单个箱子”画，这里改回它真正的朝向和左右半边
            if (state instanceof ChestRenderState chest) {
                BlockState bs = be.getBlockState();
                if (bs.hasProperty(ChestBlock.TYPE)) chest.type = bs.getValue(ChestBlock.TYPE);
                if (bs.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) chest.angle = bs.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
            }
            draw.state = state;
            draw.blockEntity = null;
            return true;
        } catch (Throwable t) {
            draw.failed = true;
            AleMod.LOGGER.debug("Schematic preview skipped a block entity", t);
            return false;
        }
    }

    @Nullable
    private static <E extends BlockEntity, S extends BlockEntityRenderState> S extractState(BlockEntityRenderDispatcher dispatcher, E be, Vec3 cameraPos) {
        BlockEntityRenderer<E, S> renderer = dispatcher.getRenderer(be);
        if (renderer == null) return null;
        S state = renderer.createRenderState();
        renderer.extractRenderState(be, state, 0.0F, cameraPos, null);
        return state;
    }

    private static void ensureStaticBuffers() {
        GpuDevice device = RenderSystem.getDevice();
        if (projectionBuffer == null) projectionBuffer = new PerspectiveProjectionMatrixBuffer("ALE schematic preview");
        if (noFogBuffer == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, FogRenderer.FOG_UBO_SIZE)
                        .putVec4(0.0F, 0.0F, 0.0F, 0.0F)
                        .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)
                        .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)
                        .get();
                noFogBuffer = device.createBuffer(() -> "ALE schematic preview fog", GpuBuffer.USAGE_UNIFORM, data);
            }
        }
        if (lightsBuffer == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // 与主世界相同的两束方向光（世界坐标）
                Vector3f l0 = new Vector3f(0.2F, 1.0F, -0.7F).normalize();
                Vector3f l1 = new Vector3f(-0.2F, 1.0F, 0.7F).normalize();
                ByteBuffer data = Std140Builder.onStack(stack, Lighting.UBO_SIZE).putVec3(l0).putVec3(l1).get();
                lightsBuffer = device.createBuffer(() -> "ALE schematic preview lights", GpuBuffer.USAGE_UNIFORM, data);
            }
        }
    }
}
