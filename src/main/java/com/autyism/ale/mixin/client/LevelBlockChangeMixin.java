package com.autyism.ale.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block changes in the client world (server updates and the player's own predicted placing / breaking): the wrong
 * block guide scans that section again. Costs one field read while the guide is not in use.
 */
@Mixin(Level.class)
public abstract class LevelBlockChangeMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void ale$noteBlockChange(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (com.autyism.ale.locate.LocateIndex.active && cir.getReturnValueZ() && (Object) this instanceof ClientLevel) {
            com.autyism.ale.locate.LocateIndex.markBlockChanged(pos);
        }
    }
}
