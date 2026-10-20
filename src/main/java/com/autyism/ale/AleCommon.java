package com.autyism.ale;

import com.autyism.ale.mining.FastBreakPayload;
import com.autyism.ale.mining.ServerFastBreak;
import com.autyism.ale.rail.ExactRailPayload;
import com.autyism.ale.rail.ServerRails;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * 客户端和服务端都会运行的部分（服务器上不需要 Litematica / MaLiLib）：
 * 轻松放置和打印机按投影的形状放铁轨，服务端负责接收形状、照原版检查放置并按这个形状放下；
 * 打印机秒破深板岩这类两 tick 才挖得掉的方块（见 mining.FastBreak）。
 * 这里不能碰任何客户端或 Litematica 的类。
 */
public class AleCommon implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(ExactRailPayload.TYPE, ExactRailPayload.CODEC);
        ServerRails.register();
        PayloadTypeRegistry.playC2S().register(FastBreakPayload.TYPE, FastBreakPayload.CODEC);
        ServerFastBreak.register();
    }
}
