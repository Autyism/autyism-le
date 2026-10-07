//? if >=26.1 {
/*package com.autyism.ale.mixin.render;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// 26.1+：贴图绑定表（值的类型不是公开的，按 Object 取）
@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Accessor("textures")
    Map<String, ?> ale$textures();
}
*///?}
