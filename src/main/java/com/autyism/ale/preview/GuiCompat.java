package com.autyism.ale.preview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** 原版界面绘制里各版本改了名字的方法，集中在这里 */
public final class GuiCompat {
    private GuiCompat() {
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    /** 当前打开的界面 */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.client.gui.screens.Screen screen() {
        //? if >=26.2 {
        /*return net.minecraft.client.Minecraft.getInstance().gui.screen();
        *///?} else {
        return net.minecraft.client.Minecraft.getInstance().screen;
        //?}
    }

    public static void setScreen(@org.jetbrains.annotations.Nullable net.minecraft.client.gui.screens.Screen screen) {
        //? if >=26.2 {
        /*net.minecraft.client.Minecraft.getInstance().gui.setScreen(screen);
        *///?} else {
        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        //?}
    }

    public static void item(GuiGraphics g, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        /*g.item(stack, x, y);
        *///?} else {
        g.renderItem(stack, x, y);
        //?}
    }
}
