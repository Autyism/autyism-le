package com.autyism.ale.mixin.common;

import com.autyism.ale.rail.ClientRails;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 铁轨的形状请求紧贴在它的使用包前面发出：只有真的发使用包时才发请求，服务端拿到的请求一定属于下一个使用包。
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientPacketSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void ale$exactRailFirst(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundUseItemOnPacket) ClientRails.beforeUseItemOn();
    }
}
