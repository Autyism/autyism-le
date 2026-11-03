package com.autyism.ale.mixin.litematica;

import com.autyism.ale.materials.SourceContainers;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Litematica counts what is available from the player's inventory (and the ender chest cache): the items in the chosen
 * material source containers are added on top, both when a list is made and every time the counts are refreshed (the
 * material list screen when it opens, the info HUD every two seconds).
 */
@Mixin(value = MaterialListUtils.class, remap = false)
public abstract class MaterialListUtilsSourceMixin {
    @Inject(method = "updateAvailableCounts", at = @At("TAIL"))
    private static void ale$addSourceContainers(List<MaterialListEntry> list, net.minecraft.world.entity.player.Player player, CallbackInfo ci) {
        if (player != null) SourceContainers.addAvailable(list);
    }

    @Inject(method = "getMaterialList", at = @At("RETURN"))
    private static void ale$addSourceContainersToNewList(CallbackInfoReturnable<List<MaterialListEntry>> cir) {
        SourceContainers.addAvailable(cir.getReturnValue());
    }
}
