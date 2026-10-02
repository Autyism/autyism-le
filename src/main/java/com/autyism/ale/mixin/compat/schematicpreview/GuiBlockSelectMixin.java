package com.autyism.ale.mixin.compat.schematicpreview;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.mixin.litematica.MaterialListPlacementAccessor;
import com.autyism.ale.mixin.litematica.MaterialListSchematicAccessor;
import com.autyism.ale.schematic.SignReplaceUtils;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.dimaskama.schematicpreview.gui.GuiBlockSelect;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 需求 10：Schematic Preview 材料列表“替换”告示牌时按形态替换（见 {@link SignReplaceUtils}）。
 * 只在“被替换的是告示牌、选的也是告示牌”时接管，其他方块仍走 Schematic Preview 原来的逻辑。
 */
@Mixin(value = GuiBlockSelect.class, remap = false)
public abstract class GuiBlockSelectMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Consumer<Block> ale$wrapSignReplace(Consumer<Block> original, @Nullable Screen parent, String title, @Nullable Block initialBlock) {
        if (!AleConfigs.Generic.SIGN_REPLACE_FIX.getBooleanValue()) return original;
        if (initialBlock == null || !SignReplaceUtils.isSign(initialBlock)) return original;
        if (!(parent instanceof GuiMaterialList gui)) return original;
        return newBlock -> {
            if (!SignReplaceUtils.isSign(newBlock)) {
                original.accept(newBlock);
                return;
            }
            MaterialListBase list = gui.getMaterialList();
            LitematicaSchematic schematic = null;
            Collection<String> regions = null;
            if (list instanceof MaterialListSchematic mls) {
                schematic = ((MaterialListSchematicAccessor) mls).ale$getSchematic();
                regions = ((MaterialListSchematicAccessor) mls).ale$getRegions();
            } else if (list instanceof MaterialListPlacement mlp) {
                schematic = ((MaterialListPlacementAccessor) mlp).ale$getPlacement().getSchematic();
                regions = schematic.getAreas().keySet();
            }
            if (schematic == null) {
                original.accept(newBlock);
                return;
            }
            int count = SignReplaceUtils.replace(initialBlock, newBlock, schematic, regions);
            if (count > 0) list.reCreateMaterialList();
            InfoUtils.showInGameMessage(Message.MessageType.INFO, 5000L, "autyism-le.message.sign_replaced",
                    count, StringUtils.translate(newBlock.getDescriptionId()));
        };
    }
}
