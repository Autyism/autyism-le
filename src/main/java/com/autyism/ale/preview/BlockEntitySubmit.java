package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 预览里的方块实体（1.21.9 起）：取出渲染状态，提交给原版的绘制，PreviewRenderer 随后把它们画进预览纹理。
 * 1.21.8 及更早见 {@link LegacyBlockEntities}
 */
final class BlockEntitySubmit {
    private BlockEntitySubmit() {
    }

    /** 提交画得出来的方块实体，返回提交了几个 */
    static int submit(PreviewModel model, BlockEntityRenderDispatcher dispatcher, net.minecraft.client.renderer.SubmitNodeStorage submits,
                      Vector3f eye, PreviewCamera previewCamera) {
        CameraRenderState camera = new CameraRenderState();
        camera.initialized = true;
        camera.pos = new Vec3(eye.x, eye.y, eye.z);
        //? if <26.1
        camera.entityPos = camera.pos;
        camera.orientation = new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(previewCamera.yaw()),
                -(float) Math.toRadians(previewCamera.pitch()), 0.0F);
        PoseStack pose = new PoseStack();
        int submitted = 0;
        for (PreviewRenderer.BlockEntityDraw draw : model.blockEntityDraws()) {
            if (draw.failed) continue;
            if (draw.state == null && !extract(dispatcher, draw, camera.pos)) continue;
            pose.pushPose();
            pose.translate(draw.x - eye.x, draw.y - eye.y, draw.z - eye.z);
            try {
                dispatcher.submit(draw.state, pose, submits, camera);
                submitted++;
            } catch (Throwable t) {
                draw.failed = true;
                AleMod.LOGGER.debug("Schematic preview skipped a block entity", t);
            }
            pose.popPose();
        }
        return submitted;
    }

    private static boolean extract(BlockEntityRenderDispatcher dispatcher, PreviewRenderer.BlockEntityDraw draw, Vec3 cameraPos) {
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
                //? if >=26.1 {
                /*if (bs.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) chest.facing = bs.getValue(BlockStateProperties.HORIZONTAL_FACING);
                *///?} else {
                if (bs.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) chest.angle = bs.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();
                //?}
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
}
