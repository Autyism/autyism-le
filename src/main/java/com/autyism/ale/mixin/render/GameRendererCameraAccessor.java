//? if >=26.2 {
/*package com.autyism.ale.mixin.render;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 26.2 起 GameRenderer 不再公开主摄像机，Litematica 画投影覆盖层仍然要它
@Mixin(GameRenderer.class)
public interface GameRendererCameraAccessor {
    @Accessor("mainCamera")
    Camera ale$mainCamera();
}
*///?}
