package com.autyism.ale.mixin.litematica;

import com.autyism.ale.materials.SourceContainersGui;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The material list screen gets the "Containers" button of the chosen material source containers */
@Mixin(value = GuiMaterialList.class, remap = false)
public abstract class GuiMaterialListSourceMixin {
    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$addSourcesButton(CallbackInfo ci) {
        SourceContainersGui.attach((GuiMaterialList) (Object) this);
    }
}
