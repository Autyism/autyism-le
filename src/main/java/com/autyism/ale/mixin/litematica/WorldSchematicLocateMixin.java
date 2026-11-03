package com.autyism.ale.mixin.litematica;

import fi.dy.masa.litematica.world.WorldSchematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Litematica asks for a schematic chunk to be drawn again whenever its blocks changed (placement built, moved, edited):
 * the wrong block guide scans that chunk again. The variant with "immediate" only exists from Litematica for 1.21.11 on.
 */
@Mixin(value = WorldSchematic.class, remap = false)
public abstract class WorldSchematicLocateMixin {
    @Inject(method = "scheduleChunkRenders(II)V", at = @At("HEAD"), require = 0)
    private void ale$noteChunkChange(int chunkX, int chunkZ, CallbackInfo ci) {
        if (com.autyism.ale.locate.LocateIndex.active) com.autyism.ale.locate.LocateIndex.markColumnChanged(chunkX, chunkZ);
    }

    @Inject(method = "scheduleChunkRenders(IIZ)V", at = @At("HEAD"), require = 0)
    private void ale$noteChunkChangeNow(int chunkX, int chunkZ, boolean immediate, CallbackInfo ci) {
        if (com.autyism.ale.locate.LocateIndex.active) com.autyism.ale.locate.LocateIndex.markColumnChanged(chunkX, chunkZ);
    }
}
