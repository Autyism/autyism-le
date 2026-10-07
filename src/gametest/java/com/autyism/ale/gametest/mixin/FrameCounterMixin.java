package com.autyism.ale.gametest.mixin;

import com.autyism.ale.gametest.GT;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 仅测试用：数一数画了多少帧（看界面在生成缩略图 / 大预览时是否仍然流畅） */
@Mixin(GameRenderer.class)
abstract class FrameCounterMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void ale$countFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        GT.frames++;
    }
}
