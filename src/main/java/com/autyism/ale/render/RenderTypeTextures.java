//? if >=26.1 {
/*package com.autyism.ale.render;

import com.autyism.ale.mixin.render.RenderSetupAccessor;
import com.autyism.ale.mixin.render.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;

// 26.1+：渲染类型绑定的第一张贴图。贴图绑定类不是公开的，用反射取 location（26.x 不混淆，名字固定）
final class RenderTypeTextures {
    private RenderTypeTextures() {
    }

    @Nullable
    static Identifier first(RenderType type) {
        try {
            Map<String, ?> textures = ((RenderSetupAccessor) (Object) ((RenderTypeAccessor) type).ale$state()).ale$textures();
            if (textures.isEmpty()) return null;
            Object binding = textures.values().iterator().next();
            Method location = binding.getClass().getDeclaredMethod("location");
            location.setAccessible(true);
            return (Identifier) location.invoke(binding);
        } catch (Throwable e) {
            return null;
        }
    }
}
*///?}
