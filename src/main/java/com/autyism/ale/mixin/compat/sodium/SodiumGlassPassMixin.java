package com.autyism.ale.mixin.compat.sodium;

import com.autyism.ale.config.AleConfigs;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 装了 Sodium 时的“透过玻璃显示投影”：原版把普通玻璃、玻璃板和红石粉画在半透明层里，在 ALE 取标记用的深度之后，所以标记能透过它们看到。
 * Sodium 会把半透明方块里贴图没有半透明像素的面挪进镂空层，镂空层在取深度之前，玻璃的边框就挡住了标记。
 * 开着“透过玻璃显示投影”时，这些面和原版一样留在半透明层。
 * <p>
 * 只改这一个判断；Sodium 更新后这个方法改了名就不注入（require = 0），只是少了这个改进，不会出错。
 */
@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumGlassPassMixin {
    @WrapOperation(method = "bufferQuad", require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;attemptPassDowngrade(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;"))
    private TerrainRenderPass ale$keepTranslucentForGlass(BlockRenderer self, TextureAtlasSprite sprite, TerrainRenderPass pass,
                                                          Operation<TerrainRenderPass> original) {
        // null：不改层，用方块材质本来的层（Sodium 自己的约定）
        if (pass.isTranslucent() && AleConfigs.Generic.RENDER_THROUGH_GLASS.getBooleanValue()) return null;
        return original.call(self, sprite, pass);
    }
}
