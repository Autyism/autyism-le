package com.autyism.ale.mixin.common;

import com.autyism.ale.rail.ExactRails;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 按请求放铁轨的那一刻，原版不重新计算铁轨的连接：新铁轨保持请求的形状，旁边已经放好的铁轨也不会被拉过来。
 * （原版放下铁轨时 onPlace → updateDir 会照旁边的铁轨改形状并改旁边的铁轨。）
 */
@Mixin(BaseRailBlock.class)
public abstract class BaseRailBlockMixin {
    @Inject(method = "updateDir", at = @At("HEAD"), cancellable = true)
    private void ale$keepExactShape(Level level, BlockPos pos, BlockState state, boolean alwaysPlace, CallbackInfoReturnable<BlockState> cir) {
        if (ExactRails.isPlacing()) cir.setReturnValue(state);
    }
}
