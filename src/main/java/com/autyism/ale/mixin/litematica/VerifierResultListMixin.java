package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifierGui;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier.BlockMismatchEntry;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicVerificationResults;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicVerificationResult;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 结果列表加上“容器内容不同”一类，这一类的行用 ALE 自己的部件画 */
@Mixin(value = WidgetListSchematicVerificationResults.class, remap = false)
public abstract class VerifierResultListMixin extends WidgetListBase<BlockMismatchEntry, WidgetSchematicVerificationResult> {
    @Shadow
    @Final
    private GuiSchematicVerifier guiSchematicVerifier;

    private VerifierResultListMixin(int x, int y, int width, int height, ISelectionListener<BlockMismatchEntry> listener) {
        super(x, y, width, height, listener);
    }

    @Inject(method = "refreshBrowserEntries", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lfi/dy/masa/litematica/gui/widgets/WidgetListSchematicVerificationResults;reCreateListEntryWidgets()V"))
    private void ale$addContainerCategory(CallbackInfo ci) {
        ContainerVerifierGui.addEntries(this.listContents, this.guiSchematicVerifier);
    }

    @Inject(method = "createListEntryWidget(IIIZLfi/dy/masa/litematica/gui/GuiSchematicVerifier$BlockMismatchEntry;)Lfi/dy/masa/litematica/gui/widgets/WidgetSchematicVerificationResult;",
            at = @At("HEAD"), cancellable = true)
    private void ale$containerRow(int x, int y, int listIndex, boolean isOdd, BlockMismatchEntry entry,
                                  CallbackInfoReturnable<WidgetSchematicVerificationResult> cir) {
        if (entry != null && ContainerVerifierGui.isOurs(entry)) {
            cir.setReturnValue(new ContainerVerifierGui.ResultWidget(x, y, this.browserEntryWidth, this.getBrowserEntryHeightFor(entry), isOdd,
                    (WidgetListSchematicVerificationResults) (Object) this, this.guiSchematicVerifier, entry, listIndex));
        }
    }
}
