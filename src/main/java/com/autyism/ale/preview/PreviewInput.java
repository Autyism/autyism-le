package com.autyism.ale.preview;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** 预览的键盘状态：界面打开时原版按键绑定不会更新，所以直接读键盘上这个绑定的键是否按着 */
public final class PreviewInput {
    private PreviewInput() {
    }

    public static boolean isDown(KeyMapping mapping) {
        InputConstants.Key key = boundKey(mapping);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    /**
     * 悬停提示：在鼠标右上方画一个小框。MaLiLib 界面里拿到的绘图对象不是原版那一个，原版的延后提示不会显示，
     * 所以这里自己画，并放到新的一层，保证在其他界面元素上面。
     */
    public static void tooltip(net.minecraft.client.gui.GuiGraphics g, int x, int y, String... lines) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String line : lines) java.util.Collections.addAll(list, line.split("\n"));
        int w = 0;
        for (String s : list) w = Math.max(w, font.width(s));
        int h = list.size() * 10 - 2;
        int tx = x + 12, ty = y - 12;
        if (tx + w + 4 > g.guiWidth()) tx = Math.max(4, x - 12 - w);
        if (ty + h + 4 > g.guiHeight()) ty = g.guiHeight() - h - 4;
        ty = Math.max(4, ty);
        g.nextStratum();
        g.fill(tx - 3, ty - 3, tx + w + 3, ty + h + 3, 0xF0101010);
        g.fill(tx - 3, ty - 3, tx + w + 3, ty - 2, 0xFF707070);
        g.fill(tx - 3, ty + h + 2, tx + w + 3, ty + h + 3, 0xFF707070);
        g.fill(tx - 3, ty - 3, tx - 2, ty + h + 3, 0xFF707070);
        g.fill(tx + w + 2, ty - 3, tx + w + 3, ty + h + 3, 0xFF707070);
        for (int i = 0; i < list.size(); i++) GuiCompat.text(g, font, list.get(i), tx, ty + i * 10, 0xFFFFFFFF, true);
    }

    /** 绑定的键（原版按名字存的那个） */
    @org.jetbrains.annotations.Nullable
    private static InputConstants.Key boundKey(KeyMapping mapping) {
        try {
            return InputConstants.getKey(mapping.saveString());
        } catch (Throwable t) {
            return null;
        }
    }

    /** 这个键码是不是某个移动键（自由视角时这些键归预览，不触发浏览器的搜索） */
    public static boolean isMovementKey(int keyCode) {
        var o = Minecraft.getInstance().options;
        for (KeyMapping m : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint}) {
            InputConstants.Key key = boundKey(m);
            if (key != null && key.getType() == InputConstants.Type.KEYSYM && key.getValue() == keyCode) return true;
        }
        return false;
    }
}
