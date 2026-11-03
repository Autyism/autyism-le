package com.autyism.ale.mixin.litematica;

import com.autyism.ale.materials.SourceContainerMarker;
import fi.dy.masa.litematica.render.OverlayRenderer;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The chosen material source containers get an outline in Litematica's overlay pass (see SourceContainerMarker) */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class OverlayRendererSourceMixin {
    @Inject(method = "renderBoxes", at = @At("TAIL"), require = 0)
    //? if >=26.1 {
    /*private void ale$renderSourceContainers(ProfilerFiller profiler, CallbackInfo ci) {
    *///?} else
    private void ale$renderSourceContainers(Matrix4f posMatrix, Matrix4f projMatrix, ProfilerFiller profiler, CallbackInfo ci) {
        SourceContainerMarker.render();
    }
}
