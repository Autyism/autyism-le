package com.autyism.ale.mixin.litematica;

import com.autyism.ale.verifier.ContainerVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 需求 9b：验证器检查容器内容物。
 */
@Mixin(value = SchematicVerifier.class, remap = false)
public abstract class SchematicVerifierMixin {
    @Shadow
    private ClientLevel worldClient;
    @Shadow
    private WorldSchematic worldSchematic;

    @Inject(method = "checkBlockStates", at = @At("TAIL"))
    private void ale$checkContainer(int x, int y, int z, BlockState stateSchematic, BlockState stateClient, CallbackInfo ci) {
        ContainerVerifier.onBlockChecked((SchematicVerifier) (Object) this, this.worldSchematic, this.worldClient,
                new BlockPos(x, y, z), stateSchematic, stateClient);
    }

    @Inject(method = "clearData", at = @At("HEAD"))
    private void ale$clear(CallbackInfo ci) {
        ContainerVerifier.reset((SchematicVerifier) (Object) this);
    }

    @Inject(method = "verifyChunks", at = @At(value = "INVOKE", target = "Lfi/dy/masa/litematica/schematic/verifier/SchematicVerifier;notifyListener()V"))
    private void ale$onFinished(net.minecraft.util.profiling.ProfilerFiller profiler, CallbackInfoReturnable<Boolean> cir) {
        int n = ContainerVerifier.countMismatches((SchematicVerifier) (Object) this);
        if (n > 0) {
            InfoUtils.showGuiAndInGameMessage(Message.MessageType.WARNING, 8000, "autyism-le.message.verifier.container_mismatch", n);
        }
    }
}
