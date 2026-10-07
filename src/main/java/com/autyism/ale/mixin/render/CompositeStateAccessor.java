//? if <1.21.11 {
/*package com.autyism.ale.mixin.render;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 1.21.10 及更早：渲染类型状态里的贴图部分（见 CompositeRenderTypeAccessor）
@Mixin(RenderType.CompositeState.class)
public interface CompositeStateAccessor {
    @Accessor("textureState")
    RenderStateShard.EmptyTextureStateShard ale$textureState();
}
*///?}
