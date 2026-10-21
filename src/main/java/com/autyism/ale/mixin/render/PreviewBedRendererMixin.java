package com.autyism.ale.mixin.render;

import com.autyism.ale.preview.LegacyBlockEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BedRenderer;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.8 及更早：投影预览里的床不在世界里，原版这时什么都不画。画预览时照世界里的画法画这一半（床头或床尾、朝向），
 * 用传进来的亮度（不在世界里，不和另一半合起来算）
 */
@Mixin(BedRenderer.class)
public abstract class PreviewBedRendererMixin {
    @Shadow
    @Final
    private Model headModel;

    @Shadow
    @Final
    private Model footModel;

    @Shadow
    private void renderPiece(PoseStack pose, MultiBufferSource buffers, Model model, Direction facing, Material material, int light, int overlay, boolean foot) {
        throw new AssertionError();
    }

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BedBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), cancellable = true)
    private void ale$previewBed(BedBlockEntity bed, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos, CallbackInfo ci) {
        if (LegacyBlockEntities.drawing != bed || bed.getLevel() != null) return;
        BlockState state = bed.getBlockState();
        this.renderPiece(pose, buffers, state.getValue(BedBlock.PART) == BedPart.HEAD ? this.headModel : this.footModel,
                state.getValue(BedBlock.FACING), Sheets.getBedMaterial(bed.getColor()), light, overlay, false);
        ci.cancel();
    }
}
