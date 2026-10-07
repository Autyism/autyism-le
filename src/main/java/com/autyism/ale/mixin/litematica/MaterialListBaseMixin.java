package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.materials.MaterialExtras;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * 需求 9c：投影里的实体（物品展示框、矿车、盔甲架、船……）计入材料列表。
 */
@Mixin(value = MaterialListBase.class, remap = false)
public abstract class MaterialListBaseMixin {
    @ModifyVariable(method = "setMaterialListEntries", at = @At("HEAD"), argsOnly = true)
    private List<MaterialListEntry> ale$addEntities(List<MaterialListEntry> list) {
        if (!AleConfigs.Generic.MATERIAL_LIST_ENTITIES.getBooleanValue()) return list;
        //? if >=26.3 {
        /*// 26.3 起 Litematica 的材料列表自己会统计投影里的实体，不再重复添加
        if (true) return list;
        *///?}
        Object self = this;
        try {
            if (self instanceof MaterialListPlacement mlp) {
                SchematicPlacement placement = ((MaterialListPlacementAccessor) mlp).ale$getPlacement();
                LitematicaSchematic schematic = placement.getSchematic();
                return MaterialExtras.withEntities(list, MaterialExtras.entityItems(schematic, schematic.getAreas().keySet()),
                        MaterialExtras.presentEntityItems(placement));
            }
        } catch (Exception e) {
            com.autyism.ale.AleMod.LOGGER.warn("Failed to add schematic entities to the material list", e);
        }
        return list;
    }
}
