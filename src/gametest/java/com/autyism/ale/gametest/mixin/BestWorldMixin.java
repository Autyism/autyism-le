package com.autyism.ale.gametest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仅测试用：gametest 中客户端渲染时服务端线程处于等待状态，Litematica 在渲染线程里读取内置服务端世界会死锁。
 * 测试里让 getBestWorld 返回客户端世界（正常游戏不受影响）。
 */
@Mixin(value = fi.dy.masa.malilib.util.WorldUtils.class, remap = false)
abstract class BestWorldMixin {
    @Inject(method = "getBestWorld", at = @At("HEAD"), cancellable = true)
    private static void ale$clientWorld(Minecraft mc, CallbackInfoReturnable<Level> cir) {
        if (com.autyism.ale.gametest.GTFilter.clientBestWorld && mc.level != null) cir.setReturnValue(mc.level);
    }
}
