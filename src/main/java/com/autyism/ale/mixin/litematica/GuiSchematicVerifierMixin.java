package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifierGui;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier.BlockMismatchEntry;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 验证器界面：点“容器内容不同”的行、筛选按钮 */
@Mixin(value = GuiSchematicVerifier.class, remap = false)
public abstract class GuiSchematicVerifierMixin {
    @Inject(method = "onSelectionChange(Lfi/dy/masa/litematica/gui/GuiSchematicVerifier$BlockMismatchEntry;)V", at = @At("HEAD"), cancellable = true)
    private void ale$selectContainers(BlockMismatchEntry entry, CallbackInfo ci) {
        if (entry != null && ContainerVerifierGui.onSelection((GuiSchematicVerifier) (Object) this, entry)) ci.cancel();
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$containerModeButton(CallbackInfo ci) {
        ContainerVerifierGui.addModeButton((GuiSchematicVerifier) (Object) this);
    }

    /** 点了 Litematica 自己的筛选按钮：不再只看容器 */
    @Inject(method = "setResultMode", at = @At("HEAD"))
    private void ale$leaveContainerMode(MismatchType type, CallbackInfo ci) {
        ContainerVerifierGui.contentsMode = false;
    }
}
