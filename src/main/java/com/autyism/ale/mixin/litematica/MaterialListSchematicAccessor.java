package com.autyism.ale.mixin.litematica;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MaterialListSchematic.class, remap = false)
public interface MaterialListSchematicAccessor {
    @Accessor("schematic")
    LitematicaSchematic ale$getSchematic();

    @Accessor("regions")
    ImmutableList<String> ale$getRegions();
}
