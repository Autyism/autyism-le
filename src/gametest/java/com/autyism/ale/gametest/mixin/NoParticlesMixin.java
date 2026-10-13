package com.autyism.ale.gametest.mixin;

import com.autyism.ale.gametest.GTFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 仅测试用：截图对比时不产生粒子（气泡柱的气泡会让两张截图不一样）。
 */
@Mixin(ParticleEngine.class)
abstract class NoParticlesMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ale$noParticles(Particle particle, CallbackInfo ci) {
        if (GTFilter.stillFrames) ci.cancel();
    }
}
