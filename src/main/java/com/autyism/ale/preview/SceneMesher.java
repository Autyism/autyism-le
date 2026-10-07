package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import fi.dy.masa.malilib.util.data.tag.CompoundData;
import fi.dy.masa.malilib.util.data.tag.converter.DataConverterNbt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 在后台线程里把投影切成 16x16x16 的区段，用原版的方块模型 / 流体渲染生成网格（与原版区段编译相同的调用），
 * 同时为需要单独渲染的方块（箱子、告示牌、旗帜、床……）建方块实体。
 * 某个方块的模型出错只跳过那一个方块。
 */
final class SceneMesher {
    /** 一批区段的结果：每层一份顶点数据，加上这批里的方块实体 */
    static final class MeshPart {
        final EnumMap<MeshLayer, MeshVertexSink> layers = new EnumMap<>(MeshLayer.class);
        final List<PlacedBlockEntity> blockEntities = new ArrayList<>();
        /** 半透明层每个四边形的中心（排序用） */
        float[] translucentCentroids = new float[0];
        float centerX, centerY, centerZ;

        int quads() {
            int n = 0;
            for (MeshVertexSink s : this.layers.values()) n += s.quadCount();
            return n;
        }

        MeshVertexSink sink(MeshLayer layer) {
            return this.layers.computeIfAbsent(layer, l -> new MeshVertexSink(4096));
        }

        void free() {
            for (MeshVertexSink s : this.layers.values()) s.free();
            this.layers.clear();
        }
    }

    record PlacedBlockEntity(BlockPos pos, BlockEntity blockEntity) {
    }

    private final SchematicView view;
    @Nullable
    private final Map<BlockPos, CompoundData> blockEntityData;
    @Nullable
    private final HolderLookup.Provider registries;
    private final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
    private final RandomSource random = RandomSource.create();
    private final List<BlockModelPart> parts = new ArrayList<>();
    private final PoseStack pose = new PoseStack();
    private final BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
    private int failures;

    /** blockEntityData = null：不建方块实体 */
    SceneMesher(SchematicView view, @Nullable Map<BlockPos, CompoundData> blockEntityData) {
        this.view = view;
        this.blockEntityData = blockEntityData;
        this.registries = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.registryAccess() : null;
    }

    /** 生成一批区段（每个区段是 {sx, sy, sz}）；取消时返回 null（已分配的内存已释放） */
    @Nullable
    MeshPart mesh(List<int[]> sections, BooleanSupplier cancelled) {
        MeshPart part = new MeshPart();
        ModelBlockRenderer.enableCaching();
        try {
            double cx = 0, cy = 0, cz = 0;
            for (int[] s : sections) {
                if (cancelled.getAsBoolean()) {
                    part.free();
                    return null;
                }
                meshSection(part, s[0], s[1], s[2]);
                cx += s[0] * 16 + 8;
                cy += s[1] * 16 + 8;
                cz += s[2] * 16 + 8;
            }
            if (!sections.isEmpty()) {
                part.centerX = (float) (cx / sections.size());
                part.centerY = (float) (cy / sections.size());
                part.centerZ = (float) (cz / sections.size());
            }
        } finally {
            ModelBlockRenderer.clearCache();
        }
        MeshVertexSink translucent = part.layers.get(MeshLayer.TRANSLUCENT);
        if (translucent != null) part.translucentCentroids = centroids(translucent);
        return part;
    }

    private void meshSection(MeshPart part, int sx, int sy, int sz) {
        int x0 = sx << 4, y0 = sy << 4, z0 = sz << 4;
        int x1 = Math.min(x0 + 16, this.view.sizeX()), y1 = Math.min(y0 + 16, this.view.sizeY()), z1 = Math.min(z0 + 16, this.view.sizeZ());
        for (int y = y0; y < y1; y++) {
            for (int z = z0; z < z1; z++) {
                for (int x = x0; x < x1; x++) {
                    BlockState state = this.view.get(x, y, z);
                    if (state.isAir()) continue;
                    this.mpos.set(x, y, z);
                    FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        MeshVertexSink sink = part.sink(layer(ItemBlockRenderTypes.getRenderLayer(fluid)));
                        sink.setOffset(x0, y0, z0);
                        int mark = sink.vertexCount();
                        try {
                            this.dispatcher.renderLiquid(this.mpos, this.view, sink, state, fluid);
                        } catch (Throwable t) {
                            sink.rollback(mark);
                            failed(state, t);
                        }
                    }
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        MeshVertexSink sink = part.sink(layer(ItemBlockRenderTypes.getChunkRenderType(state)));
                        sink.setOffset(x0, y0, z0);
                        int mark = sink.vertexCount();
                        this.pose.pushPose();
                        try {
                            this.random.setSeed(state.getSeed(this.mpos));
                            this.parts.clear();
                            this.dispatcher.getBlockModel(state).collectParts(this.random, this.parts);
                            this.pose.translate(x - x0, y - y0, z - z0);
                            this.dispatcher.renderBatched(state, this.mpos, this.view, this.pose, sink, true, this.parts);
                        } catch (Throwable t) {
                            sink.rollback(mark);
                            failed(state, t);
                        } finally {
                            this.pose.popPose();
                        }
                    }
                    if (this.blockEntityData != null && state.hasBlockEntity()) {
                        BlockEntity be = createBlockEntity(this.mpos.immutable(), state);
                        if (be != null) part.blockEntities.add(new PlacedBlockEntity(this.mpos.immutable(), be));
                    }
                }
            }
        }
    }

    @Nullable
    private BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) return null;
        try {
            BlockEntity be = entityBlock.newBlockEntity(pos, state);
            if (be == null) return null;
            CompoundData data = this.blockEntityData != null ? this.blockEntityData.get(pos) : null;
            if (data != null && this.registries != null) {
                CompoundTag tag = DataConverterNbt.toVanillaCompound(data);
                be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, this.registries, tag));
            }
            return be;
        } catch (Throwable t) {
            failed(state, t);
            return null;
        }
    }

    private void failed(BlockState state, Throwable t) {
        if (this.failures++ < 3) AleMod.LOGGER.warn("Schematic preview skipped a block it could not draw: {}", state, t);
    }

    static MeshLayer layer(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> MeshLayer.SOLID;
            case CUTOUT -> MeshLayer.CUTOUT;
            case TRANSLUCENT -> MeshLayer.TRANSLUCENT;
            case TRIPWIRE -> MeshLayer.TRIPWIRE;
        };
    }

    private static float[] centroids(MeshVertexSink sink) {
        int quads = sink.quadCount();
        float[] c = new float[quads * 3];
        for (int q = 0; q < quads; q++) {
            float x = 0, y = 0, z = 0;
            for (int v = 0; v < 4; v++) {
                int i = q * 4 + v;
                x += sink.posX(i);
                y += sink.posY(i);
                z += sink.posZ(i);
            }
            c[q * 3] = x * 0.25F;
            c[q * 3 + 1] = y * 0.25F;
            c[q * 3 + 2] = z * 0.25F;
        }
        return c;
    }
}
