package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifier;
import com.autyism.ale.verifier.ContainerVerifierGui;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 结果列表里的“容器内容不同”一类：忽略、重置忽略、信息栏里的位置列表都接进验证器原来的流程。
 */
@Mixin(value = SchematicVerifier.class, remap = false)
public abstract class SchematicVerifierSelectMixin {
    @Inject(method = "ignoreStateMismatch(Lfi/dy/masa/litematica/schematic/verifier/SchematicVerifier$BlockMismatch;)V", at = @At("HEAD"), cancellable = true)
    private void ale$ignoreContainers(BlockMismatch mismatch, CallbackInfo ci) {
        if (ContainerVerifierGui.onIgnore((SchematicVerifier) (Object) this, mismatch)) ci.cancel();
    }

    @Inject(method = "resetIgnoredStateMismatches", at = @At("HEAD"))
    private void ale$resetIgnoredContainers(CallbackInfo ci) {
        ContainerVerifier.resetIgnored((SchematicVerifier) (Object) this);
    }

    /** Litematica 整理完自己的位置列表后：容器的框重新按离玩家远近排，信息栏后面接上选中的容器 */
    @Inject(method = "updateMismatchOverlays", at = @At("TAIL"))
    private void ale$containerHud(CallbackInfo ci) {
        SchematicVerifier self = (SchematicVerifier) (Object) this;
        ContainerVerifier.invalidateRender(self);
        ContainerVerifierGui.appendHudLines(self, ((TaskBaseAccessor) self).ale$getInfoHudLines());
    }
}
