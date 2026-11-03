package com.autyism.ale.mixin.litematica;

import com.autyism.ale.schematic.SchematicNames;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Placements called "Unnamed" (or after a hash) show a real name everywhere Litematica shows the placement's name: the
 * placement list, its search, the material list and verifier titles, the tool HUD. The stored name is not changed
 * (Litematica saves the field itself), so turning the setting off brings the old names back.
 */
@Mixin(value = SchematicPlacement.class, remap = false)
public abstract class SchematicPlacementNameMixin {
    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void ale$betterName(CallbackInfoReturnable<String> cir) {
        String raw = cir.getReturnValue();
        if (!SchematicNames.isPlaceholder(raw) || !SchematicNames.enabled()) return;
        try {
            cir.setReturnValue(SchematicNames.placement((SchematicPlacement) (Object) this, raw));
        } catch (Throwable ignored) {
            // the placement is still being set up: keep the stored name
        }
    }
}
