package com.autyism.ale.mixin.litematica;

import com.autyism.ale.schematic.SchematicNames;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicEntry;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The "Loaded Schematics" list: a schematic called "Unnamed" (or after a hash) shows its file name (without
 * .litematic), like the placement list. Display only; the schematic keeps its stored name.
 */
@Mixin(value = WidgetSchematicEntry.class, remap = false)
public abstract class LoadedSchematicNameMixin {
    @Shadow
    @Final
    private LitematicaSchematic schematic;

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/SchematicMetadata;getName()Ljava/lang/String;"), require = 0)
    private String ale$betterName(String raw) {
        if (!SchematicNames.enabled() || !SchematicNames.isPlaceholder(raw)) return raw;
        try {
            return SchematicNames.schematic(this.schematic, this.schematic.getFile(), raw);
        } catch (Throwable e) {
            return raw;
        }
    }
}
