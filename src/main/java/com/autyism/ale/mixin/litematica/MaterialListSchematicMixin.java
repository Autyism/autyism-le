package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.materials.MaterialExtras;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 需求 9c：MaterialListSchematic 直接给 materialListAll 赋值（不经过 setMaterialListEntries），这里在它重建后追加实体。
 */
@Mixin(value = MaterialListSchematic.class, remap = false)
public abstract class MaterialListSchematicMixin extends MaterialListBase {
    @Shadow
    @Final
    private LitematicaSchematic schematic;
    @Shadow
    @Final
    private ImmutableList<String> regions;

    @Inject(method = "reCreateMaterialList", at = @At("TAIL"))
    private void ale$addEntities(CallbackInfo ci) {
        if (!AleConfigs.Generic.MATERIAL_LIST_ENTITIES.getBooleanValue()) return;
        try {
            this.materialListAll = ImmutableList.copyOf(MaterialExtras.withEntities(this.materialListAll,
                    MaterialExtras.entityItems(this.schematic, this.regions), null));
            this.refreshPreFilteredList();
            this.updateCounts();
        } catch (Exception e) {
            com.autyism.ale.AleMod.LOGGER.warn("Failed to add schematic entities to the material list", e);
        }
    }
}
