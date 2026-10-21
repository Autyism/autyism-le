package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 预览里的方块实体（1.21.8 及更早，还没有渲染状态）：渲染器直接画进原版的缓冲，PreviewRenderer 已经把输出换成了预览纹理。
 * 全亮，和 1.21.9 起一样。
 * <p>
 * 预览里的方块实体不在世界里，这时原版的箱子渲染器按“物品栏里的单个箱子”画（朝南），床的渲染器什么都不画。
 * 画预览的这段时间里，这两个按方块实体自己的方块状态来画（见 mixin/render/PreviewChestRendererMixin、PreviewBedRendererMixin）。
 */
public final class LegacyBlockEntities {
    /** 正在画的预览里的方块实体（只在渲染线程上用；不画预览时是 null） */
    @Nullable
    public static BlockEntity drawing;

    private LegacyBlockEntities() {
    }

    /** 画进 buffers（1.21.6 起是原版的缓冲，之后由原版画；1.21.5 是预览自己的，见 src/v1215 的 PreviewRenderer） */
    static void render(PreviewModel model, BlockEntityRenderDispatcher dispatcher, Vector3f eye, MultiBufferSource buffers) {
        Vec3 cameraPos = new Vec3(eye.x, eye.y, eye.z);
        PoseStack pose = new PoseStack();
        try {
            for (PreviewRenderer.BlockEntityDraw draw : model.blockEntityDraws()) {
                if (draw.failed || draw.blockEntity == null) continue;
                pose.pushPose();
                pose.translate(draw.x - eye.x, draw.y - eye.y, draw.z - eye.z);
                try {
                    drawing = draw.blockEntity;
                    renderOne(dispatcher, draw.blockEntity, pose, buffers, cameraPos);
                } catch (Throwable t) {
                    draw.failed = true;
                    AleMod.LOGGER.debug("Schematic preview skipped a block entity", t);
                }
                pose.popPose();
            }
        } finally {
            drawing = null;
        }
    }

    private static <E extends BlockEntity> void renderOne(BlockEntityRenderDispatcher dispatcher, E be, PoseStack pose, MultiBufferSource buffers, Vec3 cameraPos) {
        BlockEntityRenderer<E> renderer = dispatcher.getRenderer(be);
        if (renderer == null) return;
        renderer.render(be, 0.0F, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, cameraPos);
    }
}
