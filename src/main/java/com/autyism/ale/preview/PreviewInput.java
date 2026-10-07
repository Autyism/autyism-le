package com.autyism.ale.preview;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** 预览的键盘状态：界面打开时原版按键绑定不会更新，所以直接读键盘上这个绑定的键是否按着 */
public final class PreviewInput {
    private PreviewInput() {
    }

    public static boolean isDown(KeyMapping mapping) {
        InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
        if (key == null || key.getType() != InputConstants.Type.KEYSYM || key.getValue() < 0) return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key.getValue());
    }

    /** 这个键码是不是某个移动键（自由视角时这些键归预览，不触发浏览器的搜索） */
    public static boolean isMovementKey(int keyCode) {
        var o = Minecraft.getInstance().options;
        for (KeyMapping m : new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint}) {
            InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(m);
            if (key != null && key.getType() == InputConstants.Type.KEYSYM && key.getValue() == keyCode) return true;
        }
        return false;
    }
}
