package com.autyism.ale.mixin.litematica;

import com.autyism.ale.materials.SourceContainers;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fi.dy.masa.litematica.materials.MaterialListHudRenderer;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The material list on the HUD says how many of the picked material containers were never seen yet (their contents
 * are not counted until they are), right in its title line.
 */
@Mixin(value = MaterialListHudRenderer.class, remap = false)
public abstract class MaterialHudUnknownMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/util/StringUtils;translate(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", ordinal = 0), require = 0)
    private String ale$unknownContainers(String title) {
        try {
            if (!SourceContainers.enabled()) return title;
            int unknown = SourceContainers.unknownCount();
            if (unknown <= 0) return title;
            return title + " §6" + StringUtils.translate("autyism-le.hud.material.unknown_containers", unknown) + "§r";
        } catch (Throwable e) {
            return title;
        }
    }
}
