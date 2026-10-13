package com.autyism.ale.mixin.common;

import com.autyism.ale.rail.ExactRails;
import com.autyism.ale.rail.ServerRails;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 处理“对方块使用物品”的包时，把这个玩家刚发来的形状请求（ServerRails）挂上；包处理完就摘掉。
 * 原版第一次在网络线程上调用这个方法，只是把包转交服务端线程，那一次不取请求。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @WrapMethod(method = "handleUseItemOn")
    private void ale$withExactRail(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        ExactRails.Request request = this.player.level().getServer().isSameThread() ? ServerRails.take(this.player) : null;
        if (request == null) {
            original.call(packet);
            return;
        }
        ExactRails.with(request, () -> original.call(packet));
    }
}
