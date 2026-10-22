package com.autyism.ale.mixin.litematica;

import com.autyism.ale.rail.EasyPlaceRails;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 轻松放置（新版代码，Litematica 的 easyPlacePostRewrite 开着时）放铁轨：见 EasyPlaceRails */
@Mixin(value = EasyPlaceUtils.class, remap = false)
public abstract class EasyPlaceRailMixin {
    @WrapOperation(method = "handleEasyPlace", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private static InteractionResult ale$exactRail(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                                   Operation<InteractionResult> original) {
        return EasyPlaceRails.useItemOn(player, hand, hit, () -> original.call(gameMode, player, hand, hit));
    }

    // Litematica 自己先看放不放得住（26.3 的 0.29 起）：放不住的铁轨在这里就停了，记下铁轨的提示
    @WrapOperation(method = "handleEasyPlace", require = 0, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/block/state/BlockState;canSurvive(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean ale$railCanSurvive(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.LevelReader level,
                                              net.minecraft.core.BlockPos pos, Operation<Boolean> original) {
        boolean ok = original.call(state, level, pos);
        if (!ok) EasyPlaceRails.noteCannotStay(state, pos);
        return ok;
    }

    // 铁轨放不住时的提示别被 Litematica 自己的失败提示盖掉（见 EasyPlaceRails.pendingHint）
    @org.spongepowered.asm.mixin.injection.Inject(method = "handleEasyPlaceWithMessage", at = @At("HEAD"))
    private static void ale$railHintStart(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        EasyPlaceRails.clearPendingHint();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "handleEasyPlaceWithMessage", at = @At("RETURN"))
    private static void ale$railHintAgain(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        EasyPlaceRails.showPendingHint();
    }
}
