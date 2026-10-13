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
}
