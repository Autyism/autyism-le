package com.autyism.ale.rail;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.function.Supplier;

/**
 * 客户端：服务端装了 ALE 时，放铁轨前告诉服务端要的形状（不涉及 Litematica，打印机的接口也用这里）。
 */
public final class ClientRails {
    private ClientRails() {
    }

    /** 现在连着的服务端（或单人游戏的内置服务端）能接收形状请求 */
    public static boolean isAvailable() {
        return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(ExactRailPayload.TYPE);
    }

    /**
     * 带着形状请求执行一次放置（里面要通过 MultiPlayerGameMode.useItemOn 发出使用包）。
     * 不可用或 rail 不是铁轨时什么都不做，返回 FAIL。
     */
    public static InteractionResult place(BlockPos pos, BlockState rail, Supplier<InteractionResult> placement) {
        Property<RailShape> property = ExactRails.shapeProperty(rail);
        Minecraft mc = Minecraft.getInstance();
        if (property == null || mc.player == null || !isAvailable()) return InteractionResult.FAIL;
        ExactRails.Request request = new ExactRails.Request(pos.immutable(), rail.getBlock(), rail.getValue(property), mc.player);
        return ExactRails.with(request, placement);
    }

    /** 使用包发出前一刻（ClientPacketSendMixin）：这个线程正带着请求时，先把请求发过去 */
    public static void beforeUseItemOn() {
        ExactRails.Request request = ExactRails.current();
        if (request == null || !isAvailable()) return;
        ClientPlayNetworking.send(new ExactRailPayload(ExactRailPayload.VERSION, request.pos(), request.block(), request.shape().getSerializedName()));
    }
}
