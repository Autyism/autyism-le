package com.autyism.ale.gametest.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读动作栏上的提示文字（26.2 起在 Gui 的 Hud 里） */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else
@Mixin(Gui.class)
public interface GuiOverlayAccessor {
    @Accessor("overlayMessageString")
    Component aleGt$overlayMessage();
}
