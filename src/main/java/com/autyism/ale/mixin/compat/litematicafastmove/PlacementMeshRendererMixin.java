package com.autyism.ale.mixin.compat.litematicafastmove;

//? if >=1.21.11 <26.1 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.injection.At;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Supplier;
//?}
import org.spongepowered.asm.mixin.Mixin;

/**
 * 装了 Litefastica 时：它接管的投影的覆盖层用它自己的渲染通道画。透过玻璃显示投影时，这个通道也换成原版画半透明层之前的深度
 * （见 OverlayDepth、WorldRendererSchematicMixin），水和半透明方块挡不住这些投影上的错误标记。
 */
@Mixin(targets = "ml.mypals.litematicafastmove.mesh.PlacementMeshRenderer", remap = false)
public abstract class PlacementMeshRendererMixin {
    //? if >=1.21.11 <26.1 {
    @WrapOperation(method = "drawOverlays", require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalInt;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"))
    private static RenderPass ale$overlayDepth(CommandEncoder encoder, Supplier<String> label, GpuTextureView color,
                                               OptionalInt clearColor, @Nullable GpuTextureView depth, OptionalDouble clearDepth,
                                               Operation<RenderPass> original) {
        return original.call(encoder, label, color, clearColor, com.autyism.ale.render.OverlayDepth.depthFor(depth), clearDepth);
    }
    //?}
}
