package com.autyism.ale.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 → 服务端：紧接着的那个“开始挖掘”的包（START_DESTROY_BLOCK）挖的是 pos，能秒破就秒破（见 FastBreak）。
 * 客户端只在发那个包之前一刻发这个（见 ClientFastBreak），服务端只把它用在同一个玩家的下一个开始挖掘的包上（见 ServerFastBreak）。
 */
public record FastBreakPayload(int version, BlockPos pos) implements CustomPacketPayload {
    /** 格式变了就加一；服务端不认识的版本直接忽略（那次挖掘照原版来） */
    public static final int VERSION = 1;
    public static final Type<FastBreakPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("autyism-le", "fast_break"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FastBreakPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, FastBreakPayload::version,
            BlockPos.STREAM_CODEC, FastBreakPayload::pos,
            FastBreakPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
