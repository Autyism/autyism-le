package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifierGui;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicVerificationResult;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** 结果列表的列宽也要放得下“容器内容不同”一类的文字 */
@Mixin(value = WidgetSchematicVerificationResult.class, remap = false)
public abstract class VerificationResultWidgetMixin {
    @Shadow
    private static int maxNameLengthExpected;
    @Shadow
    private static int maxNameLengthFound;

    @Inject(method = "setMaxNameLengths", at = @At("TAIL"))
    private static void ale$containerColumns(List<BlockMismatch> mismatches, CallbackInfo ci) {
        int[] widths = ContainerVerifierGui.columnWidths();
        maxNameLengthExpected = Math.max(maxNameLengthExpected, widths[0]);
        maxNameLengthFound = Math.max(maxNameLengthFound, widths[1]);
    }
}
