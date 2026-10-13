package com.autyism.ale.rail;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端：收客户端的形状请求，交给同一个玩家紧接着的那个使用包（见 ServerGamePacketListenerImplMixin）。
 * 每个玩家只记一个：新请求顶掉旧的，下一个使用包不论放没放都把它取走。放置本身照原版的检查走
 * （距离、权限、手里的物品、能不能替换、能不能存活），只是形状换成请求的那个。
 */
public final class ServerRails {
    /** 比这更远的请求不记（正常的放置距离远小于这个） */
    private static final double MAX_DISTANCE = 32.0;
    private static final Map<UUID, ExactRails.Request> PENDING = new ConcurrentHashMap<>();

    private ServerRails() {
    }

    public static void register() {
        registerReceiver();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
    }

    /** 收形状请求（服务端把能收的频道告诉客户端，客户端据此知道服务端装了 ALE） */
    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ExactRailPayload.TYPE, ServerRails::receive);
    }

    /** 在服务端线程上调用（Fabric 把处理交给服务端线程） */
    private static void receive(ExactRailPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        ExactRails.Request request = decode(payload, player);
        if (request == null) PENDING.remove(player.getUUID());
        else PENDING.put(player.getUUID(), request);
    }

    /** 检查请求：版本、方块是铁轨、形状属于这种铁轨、位置在玩家附近。不合格的返回 null */
    @Nullable
    static ExactRails.Request decode(ExactRailPayload payload, ServerPlayer player) {
        if (payload.version() != ExactRailPayload.VERSION) return null;
        Property<RailShape> property = ExactRails.shapeProperty(payload.block().defaultBlockState());
        RailShape shape = ExactRails.shapeByName(payload.shape());
        if (property == null || shape == null || !property.getPossibleValues().contains(shape)) return null;
        if (player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_DISTANCE * MAX_DISTANCE) return null;
        return new ExactRails.Request(payload.pos().immutable(), payload.block(), shape, player);
    }

    /** 这个玩家正要处理的使用包带着的请求（取走：只对这一个包有效） */
    @Nullable
    public static ExactRails.Request take(ServerPlayer player) {
        return PENDING.remove(player.getUUID());
    }
}
