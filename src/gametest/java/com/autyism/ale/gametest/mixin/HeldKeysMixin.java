package com.autyism.ale.gametest.mixin;

import com.autyism.ale.gametest.GT;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.3 起 MaLiLib 直接读 SDL 的真实键盘状态来判断热键是否按住，gametest 模拟的按键不会出现在那里。
 * 测试按住的键（GT.HELD_KEYS）在这里也算按下。
 */
@Mixin(value = KeybindMulti.class, remap = false)
public abstract class HeldKeysMixin {
    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true)
    private static void ale$testHeldKeys(int keyCode, CallbackInfoReturnable<Boolean> cir) {
        if (GT.HELD_KEYS.contains(keyCode)) cir.setReturnValue(true);
    }
}
