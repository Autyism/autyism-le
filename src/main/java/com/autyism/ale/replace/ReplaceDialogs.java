package com.autyism.ale.replace;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 装了 Schematic Preview 时，材料列表的“替换”默认也打开 ALE 的替换对话框（能用鼠标中键选取方块、告示牌 / 火把等几种形态合成一条）；
 * Schematic Preview 自己的对话框在 ALE 对话框右上角的按钮里。
 */
public final class ReplaceDialogs {
    /** 正在打开原来的对话框（这一次不拦） */
    private static boolean openingOriginal;

    private ReplaceDialogs() {
    }

    /** Schematic Preview 的替换对话框（ALE 的 mixin 给它加的接口） */
    public interface Original {
        @Nullable
        Block ale$initialBlock();
    }

    /**
     * 要打开 Schematic Preview 的替换对话框时调用：换成 ALE 的对话框就返回 true（原来的不打开）
     */
    public static boolean replaceOriginal(Screen original) {
        if (openingOriginal || !AleConfigs.Generic.MATERIAL_REPLACE.getBooleanValue()) return false;
        if (!(original instanceof Original o) || !(original instanceof GuiBase base)) return false;
        Block block = o.ale$initialBlock();
        if (block == null || block.asItem() == Items.AIR) return false;
        if (!(base.getParent() instanceof GuiMaterialList gui)) return false;
        MaterialListBase list = gui.getMaterialList();
        if (list == null || MaterialReplacer.targetOf(list) == null) return false;
        Minecraft.getInstance().setScreen(new ReplaceBlockScreen(list, new ItemStack(block.asItem()), gui, () -> openOriginal(original)));
        return true;
    }

    private static void openOriginal(Screen original) {
        openingOriginal = true;
        try {
            Minecraft.getInstance().setScreen(original);
        } finally {
            openingOriginal = false;
        }
    }
}
