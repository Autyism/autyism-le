package com.autyism.ale.mining;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;

/**
 * 客户端：服务端装了 ALE 时，开始挖掘的包前面紧贴着发秒破请求（不涉及 Litematica，打印机的接口也用这里）。
 */
public final class ClientFastBreak {
    private static final ThreadLocal<BlockPos> REQUEST = new ThreadLocal<>();

    private ClientFastBreak() {
    }

    /** 现在连着的服务端（或单人游戏的内置服务端）能接收秒破请求 */
    public static boolean isAvailable() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(FastBreakPayload.TYPE);
    }

    /** 带着请求执行 start（里面要发出 pos 的开始挖掘的包）。不可用时照常执行 */
    public static void breakNow(BlockPos pos, Runnable start) {
        BlockPos previous = REQUEST.get();
        REQUEST.set(pos.immutable());
        try {
            start.run();
        } finally {
            if (previous == null) REQUEST.remove();
            else REQUEST.set(previous);
        }
    }

    /** 开始挖掘的包发出前一刻（ClientPacketSendMixin）：这个线程正带着这一格的请求时，先把请求发过去（只发一次） */
    public static void beforeStart(ServerboundPlayerActionPacket packet) {
        BlockPos pos = REQUEST.get();
        if (pos == null || !pos.equals(packet.getPos()) || !isAvailable()) return;
        REQUEST.remove();
        ClientPlayNetworking.send(new FastBreakPayload(FastBreakPayload.VERSION, pos));
    }
}
