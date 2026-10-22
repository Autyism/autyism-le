package com.autyism.ale.mixin.litematica;

import com.autyism.ale.rail.EasyPlaceRails;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.litematica.util.WorldUtils;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 轻松放置（旧版代码，Litematica 默认用的这套）放铁轨：见 EasyPlaceRails */
@Mixin(value = WorldUtils.class, remap = false)
public abstract class LegacyEasyPlaceRailMixin {
    @WrapOperation(method = "doEasyPlaceAction", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private static InteractionResult ale$exactRail(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                                   Operation<InteractionResult> original) {
        return EasyPlaceRails.useItemOn(player, hand, hit, () -> original.call(gameMode, player, hand, hit));
    }

    // 放不住的铁轨：Litematica 在 handleEasyPlace 里写完自己的失败提示以后，再显示铁轨的提示（见 EasyPlaceRails.pendingHint）
    @org.spongepowered.asm.mixin.injection.Inject(method = "handleEasyPlace", at = @At("HEAD"))
    private static void ale$railHintStart(net.minecraft.client.Minecraft mc,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        EasyPlaceRails.clearPendingHint();
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "handleEasyPlace", at = @At("RETURN"))
    private static void ale$railHintAgain(net.minecraft.client.Minecraft mc,
                                          org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        EasyPlaceRails.showPendingHint();
    }
}
