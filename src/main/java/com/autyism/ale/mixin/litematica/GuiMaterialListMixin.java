package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.materials.MaterialExtras;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListCustom;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

/**
 * 需求 9a：材料列表界面加“容器内容物”按钮，打开投影中所有容器里物品的汇总列表。
 */
@Mixin(value = GuiMaterialList.class, remap = false)
public abstract class GuiMaterialListMixin {
    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$addContentsButton(CallbackInfo ci) {
        if (!AleConfigs.Generic.MATERIAL_LIST_ENTITIES.getBooleanValue()) return;
        GuiMaterialList gui = (GuiMaterialList) (Object) this;
        MaterialListBase list = gui.getMaterialList();
        LitematicaSchematic schematic;
        Collection<String> regions;
        if (list instanceof MaterialListSchematic mls) {
            schematic = ((MaterialListSchematicAccessor) mls).ale$getSchematic();
            regions = ((MaterialListSchematicAccessor) mls).ale$getRegions();
        } else if (list instanceof MaterialListPlacement mlp) {
            schematic = ((MaterialListPlacementAccessor) mlp).ale$getPlacement().getSchematic();
            regions = schematic.getAreas().keySet();
        } else {
            return;
        }
        ButtonGeneric button = new ButtonGeneric(0, 0, -1, false, "autyism-le.gui.button.container_contents");
        button.setHoverStrings(StringUtils.translate("autyism-le.gui.button.container_contents.hover"));
        // 放在最下面那一排按钮的末尾；放不下时那一排会变成可左右滑动的按钮栏（ButtonRail）
        int[] spot = com.autyism.ale.gui.ButtonRail.endOfBottomRow(gui);
        button.setPosition(spot[0], spot[1]);
        gui.addButton(button, (b, mouseButton) -> {
            var items = MaterialExtras.containerContents(schematic, regions);
            String name = StringUtils.translate("autyism-le.gui.title.container_contents", list.getName());
            MaterialListCustom contents = new MaterialListCustom(name, items, null);
            GuiMaterialList contentsGui = new GuiMaterialList(contents);
            contentsGui.setParent(gui);
            GuiBase.openGui(contentsGui);
        });
    }
}
