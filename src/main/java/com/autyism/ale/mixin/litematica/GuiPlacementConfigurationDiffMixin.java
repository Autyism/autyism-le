package com.autyism.ale.mixin.litematica;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.diff.DiffPickScreen;
import com.autyism.ale.diff.DiffSession;
import fi.dy.masa.litematica.gui.GuiPlacementConfiguration;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The placement's settings screen: "Compare with..." (schematic diff) and, while a diff of it is shown, its list */
@Mixin(value = GuiPlacementConfiguration.class, remap = false)
public abstract class GuiPlacementConfigurationDiffMixin {
    @Shadow
    @Final
    public SchematicPlacement placement;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ale$addDiffButtons(CallbackInfo ci) {
        if (!AleToolsConfigs.DIFF.getBooleanValue()) return;
        GuiBase gui = (GuiBase) (Object) this;
        // at the end of the bottom row; the row becomes a sliding button rail when it gets too long (ButtonRail)
        int[] pos = com.autyism.ale.gui.ButtonRail.endOfBottomRow(gui);
        ButtonGeneric compare = new ButtonGeneric(pos[0], pos[1], -1, false, "autyism-le.gui.button.diff.compare_with");
        compare.setHoverStrings(StringUtils.translate("autyism-le.gui.button.diff.compare_with.hover"));
        gui.addButton(compare, (button, mouseButton) -> DiffPickScreen.open(this.placement, gui));
        DiffSession s = DiffSession.current();
        if (s != null && s.placement() == this.placement) {
            ButtonGeneric list = new ButtonGeneric(pos[0] + compare.getWidth() + 2, pos[1], -1, false, "autyism-le.gui.button.diff.open_list");
            gui.addButton(list, (button, mouseButton) -> DiffSession.openList(gui));
        }
    }
}
