//? if >=26.1 {
/*package com.autyism.ale.mixin.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 26.1 起渲染类型的设置是私有的（以前由 Fabric API 开放）：半透明兜底要读出它绑定的贴图
@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Accessor("state")
    RenderSetup ale$state();
}
*///?}
