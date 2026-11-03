package com.autyism.ale.mixin.litematica;

import fi.dy.masa.litematica.render.OverlayRenderer;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wrong block guide's box and the schematic diff's boxes, drawn with Litematica's other overlay boxes */
@Mixin(value = OverlayRenderer.class, remap = false)
public abstract class OverlayToolBoxesMixin {
    @Inject(method = "renderBoxes", at = @At("TAIL"))
    // 26.1 起 Litematica 的这个方法只剩 ProfilerFiller 参数
    //? if >=26.1 {
    /*private void ale$renderToolBoxes(ProfilerFiller profiler, CallbackInfo ci) {
    *///?} else
    private void ale$renderToolBoxes(Matrix4f posMatrix, Matrix4f projMatrix, ProfilerFiller profiler, CallbackInfo ci) {
        com.autyism.ale.render.ToolBoxes.draw();
    }
}
