//? if <1.21.11 {
/*package com.autyism.ale.mixin.render;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 1.21.10 及更早：渲染类型的状态（里面有绑定的贴图）是私有的，半透明兜底要读出贴图。
// 只有这些版本才有，由 AleMixinPlugin 加入（不写进 mixin 配置文件）
@Mixin(RenderType.CompositeRenderType.class)
public interface CompositeRenderTypeAccessor {
    @Accessor("state")
    RenderType.CompositeState ale$state();
}
*///?}
