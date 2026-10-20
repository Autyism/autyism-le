package com.autyism.ale.gametest.mixin;

import com.autyism.ale.gametest.FastBreakGameTest;
import com.autyism.ale.gametest.RailGameTest;
import com.autyism.ale.mining.FastBreakPayload;
import com.autyism.ale.rail.ExactRailPayload;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 铁轨测试、秒破测试：数客户端发出的请求（服务端没装 ALE 时一个都不该发） */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class RailPacketCounterMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void aleGt$countExactRail(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundCustomPayloadPacket custom && custom.payload() instanceof ExactRailPayload) {
            RailGameTest.SENT.incrementAndGet();
        }
        if (packet instanceof ServerboundCustomPayloadPacket custom && custom.payload() instanceof FastBreakPayload) {
            FastBreakGameTest.countRequest();
        }
    }
}
