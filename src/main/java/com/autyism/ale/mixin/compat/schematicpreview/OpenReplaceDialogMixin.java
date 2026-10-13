package com.autyism.ale.mixin.compat.schematicpreview;

import com.autyism.ale.replace.ReplaceDialogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dimaskama.schematicpreview.gui.GuiBlockSelect;

/**
 * Schematic Preview 的材料列表“替换”打开它的对话框时，默认换成 ALE 的替换对话框（右上角按钮可以回到原来的）。
 * 只在装了 Schematic Preview 时应用。
 */
@Mixin(Minecraft.class)
public abstract class OpenReplaceDialogMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void ale$aleReplaceDialog(@Nullable Screen screen, CallbackInfo ci) {
        if (screen instanceof GuiBlockSelect && ReplaceDialogs.replaceOriginal(screen)) ci.cancel();
    }
}
