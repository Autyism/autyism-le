package com.autyism.ale.mixin.render;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
//? if >=1.21.11 <26.1 {
import com.autyism.ale.verifier.SlotHints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/**
 * 打开投影里的容器时每一格的提示（见 SlotHints）：格子底色和边框画在物品下面，缺的格子在空格里画半透明的虚影，
 * 鼠标停在缺东西的空格上时显示该放什么。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSlotHintsMixin {
    //? if >=1.21.11 <26.1 {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void ale$slotHintUnder(GuiGraphics g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        SlotHints.Hint hint = SlotHints.hint(this.menu, slot);
        if (hint == null) return;
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, hint.status().fill());
        ale$outline(g, slot, hint.status().border());
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void ale$slotHintGhost(GuiGraphics g, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        SlotHints.Hint hint = SlotHints.hint(this.menu, slot);
        if (hint == null || hint.status() != SlotHints.Status.MISSING || slot.hasItem() || hint.expected().isEmpty()) return;
        // 虚影：物品和数量照常画，再盖一层浅色让它看起来是半透明的，最后是这一类的浅色和边框
        g.renderItem(hint.expected(), slot.x, slot.y);
        g.renderItemDecorations(Minecraft.getInstance().font, hint.expected(), slot.x, slot.y);
        int shade = Math.round((1.0f - SlotHints.GHOST_ALPHA) * 255.0f);
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, shade << 24 | SlotHints.GHOST_SHADE);
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, hint.status().ghostMask());
        ale$outline(g, slot, hint.status().border());
    }

    @Inject(method = "renderTooltip", at = @At("RETURN"))
    private void ale$slotHintTooltip(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        Slot slot = this.hoveredSlot;
        if (slot == null || slot.hasItem() || !this.menu.getCarried().isEmpty()) return;
        SlotHints.Hint hint = SlotHints.hint(this.menu, slot);
        if (hint != null && hint.status() == SlotHints.Status.MISSING && !hint.expected().isEmpty()) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, hint.expected(), mouseX, mouseY);
        }
    }

    @Unique
    private static void ale$outline(GuiGraphics g, Slot slot, int color) {
        int x = slot.x, y = slot.y;
        g.fill(x, y, x + 16, y + 1, color);
        g.fill(x, y + 15, x + 16, y + 16, color);
        g.fill(x, y + 1, x + 1, y + 15, color);
        g.fill(x + 15, y + 1, x + 16, y + 15, color);
    }
    //?}
}
