package com.autyism.ale.mixin.litematica;

import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 选中 / 忽略“容器内容不同”后，让验证器重新整理要框出的位置和信息栏 */
@Mixin(value = SchematicVerifier.class, remap = false)
public interface SchematicVerifierInvoker {
    @Invoker("updateMismatchOverlays")
    void ale$updateMismatchOverlays();
}
