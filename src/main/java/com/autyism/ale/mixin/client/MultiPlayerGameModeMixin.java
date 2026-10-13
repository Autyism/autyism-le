package com.autyism.ale.mixin.client;

import com.autyism.ale.verifier.OpenedContainers;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 记下玩家最近点的方块：随后打开的容器界面就是它的（“需要填充”标记比较内容用） */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void ale$rememberClickedBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
        OpenedContainers.onUseItemOn(hit.getBlockPos());
    }
}
