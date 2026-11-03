package com.autyism.ale.mixin.compat.syncmatica;

import ch.endte.syncmatica.data.ServerPlacement;
import com.autyism.ale.compat.SyncmaticaNames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Syncmatica's list rows: shared entries named after a hash or "Unnamed" show a real name (only what is drawn) */
@Mixin(targets = "ch.endte.syncmatica.litematica.gui.WidgetSyncmaticaServerPlacementEntry", remap = false)
public abstract class ServerPlacementEntryNameMixin {
    @WrapOperation(method = "render", require = 0,
            at = @At(value = "INVOKE", target = "Lch/endte/syncmatica/data/ServerPlacement;getName()Ljava/lang/String;"))
    private String ale$shownName(ServerPlacement placement, Operation<String> original) {
        return SyncmaticaNames.display(placement, original.call(placement));
    }
}
