package com.autyism.ale.mixin.litematica;

import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.OverlayRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ChunkRenderDataSchematic.class, remap = false)
public interface ChunkRenderDataSchematicInvoker {
    @Invoker("setOverlayTypeStarted")
    void ale$setOverlayTypeStarted(OverlayRenderType type);
}
