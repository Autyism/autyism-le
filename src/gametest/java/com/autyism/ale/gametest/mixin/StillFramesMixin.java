package com.autyism.ale.gametest.mixin;

import com.autyism.ale.gametest.GTFilter;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 仅测试用：截图对比时让水、岩浆、海带等动画贴图停在同一帧，两张截图的差别就只剩要测的覆盖层。
 */
@Mixin(TextureManager.class)
abstract class StillFramesMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ale$holdAnimations(CallbackInfo ci) {
        if (GTFilter.stillFrames) ci.cancel();
    }
}
