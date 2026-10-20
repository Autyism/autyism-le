package com.autyism.ale.mining;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端：收客户端的秒破请求，交给同一个玩家紧接着的那个开始挖掘的包（见 ServerPlayerGameModeMixin）。
 * 每个玩家只记一个：新请求顶掉旧的；下一个开始挖掘的包不论挖的是不是这一格都把它取走；
 * 请求和包本来是一起发出的，隔了两个 tick 还没等到包（客户端最后没发）的请求作废。
 */
public final class ServerFastBreak {
    /** 比这更远的请求不记（正常的挖掘距离远小于这个） */
    private static final double MAX_DISTANCE = 32.0;
    private static final int MAX_AGE_TICKS = 2;

    private record Request(BlockPos pos, int tick) {
    }

    private static final Map<UUID, Request> PENDING = new ConcurrentHashMap<>();
    /** 正在处理的开始挖掘的包带着请求时，请求的位置（只在服务端线程上） */
    private static final ThreadLocal<BlockPos> CURRENT = new ThreadLocal<>();

    private ServerFastBreak() {
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(FastBreakPayload.TYPE, ServerFastBreak::receive);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
    }

    /** 在服务端线程上调用（Fabric 把处理交给服务端线程） */
    private static void receive(FastBreakPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (payload.version() != FastBreakPayload.VERSION
                || player.position().distanceToSqr(Vec3.atCenterOf(payload.pos())) > MAX_DISTANCE * MAX_DISTANCE) {
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.put(player.getUUID(), new Request(payload.pos().immutable(), player.level().getServer().getTickCount()));
    }

    /** 这个玩家正要处理的开始挖掘的包带着的请求位置（取走：只对这一个包有效） */
    @Nullable
    public static BlockPos take(ServerPlayer player) {
        Request request = PENDING.remove(player.getUUID());
        if (request == null || player.level().getServer().getTickCount() - request.tick() > MAX_AGE_TICKS) return null;
        return request.pos();
    }

    /** 带着请求处理这个包 */
    public static void with(BlockPos pos, Runnable action) {
        BlockPos previous = CURRENT.get();
        CURRENT.set(pos);
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** 正在处理的包请求了秒破 pos */
    public static boolean isRequested(BlockPos pos) {
        BlockPos current = CURRENT.get();
        return current != null && current.equals(pos);
    }
}
