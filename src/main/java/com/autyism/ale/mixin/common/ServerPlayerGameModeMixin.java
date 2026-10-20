package com.autyism.ale.mixin.common;

import com.autyism.ale.mining.FastBreak;
import com.autyism.ale.mining.ServerFastBreak;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 秒破请求（ServerFastBreak）：开始挖掘的包带着这一格的请求时，方块在标签里、一 tick 的进度至少 50%，就按“一 tick 挖得完”处理，
 * 原版自己就会当场破坏（和本来就能秒破的方块走同一条路：掉落、经验、工具磨损、统计都照原版）。别的包、别的方块不变。
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapMethod(method = "handleBlockBreakAction")
    private void ale$withFastBreak(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction direction, int maxBuildHeight,
                                   int sequence, Operation<Void> original) {
        BlockPos requested = action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK ? ServerFastBreak.take(this.player) : null;
        if (requested == null || !requested.equals(pos)) {
            original.call(pos, action, direction, maxBuildHeight, sequence);
            return;
        }
        ServerFastBreak.with(pos, () -> original.call(pos, action, direction, maxBuildHeight, sequence));
    }

    @ModifyExpressionValue(method = "handleBlockBreakAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float ale$fastBreak(float progress, @Local(argsOnly = true) BlockPos pos, @Local(argsOnly = true) ServerboundPlayerActionPacket.Action action) {
        if (progress >= 1.0F || action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK || !ServerFastBreak.isRequested(pos)) return progress;
        return FastBreak.eligible(this.player.level().getBlockState(pos), progress) ? 1.0F : progress;
    }
}
