package com.autyism.ale.mixin.render;

import com.autyism.ale.preview.LegacyBlockEntities;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 1.21.8 及更早：投影预览里的箱子不在世界里，原版这时按“物品栏里的单个箱子”画（朝南）。
 * 画预览时改用它自己的方块状态，朝向和左右半边就对了；不在世界里，所以照旧不和旁边的箱子合起来算开盖和亮度
 */
@Mixin(ChestRenderer.class)
public abstract class PreviewChestRendererMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/phys/Vec3;)V",
            at = @At("STORE"), ordinal = 0)
    private BlockState ale$previewState(BlockState state) {
        var be = LegacyBlockEntities.drawing;
        return be != null && be.getLevel() == null ? be.getBlockState() : state;
    }
}
