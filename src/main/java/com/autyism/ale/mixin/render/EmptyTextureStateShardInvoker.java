//? if <1.21.11 {
/*package com.autyism.ale.mixin.render;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

// 1.21.10 及更早：贴图部分绑定的贴图（单张贴图和多张贴图的子类各自实现）
@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface EmptyTextureStateShardInvoker {
    @Invoker("cutoutTexture")
    Optional<Identifier> ale$cutoutTexture();
}
*///?}
