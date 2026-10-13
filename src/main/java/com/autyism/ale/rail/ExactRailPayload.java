package com.autyism.ale.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * 客户端 → 服务端：紧接着的那个“对方块使用物品”的包要在 pos 放下 block，形状是 shape（RailShape 的名字）。
 * 客户端只在发那个包之前一刻发这个（见 ClientRails），服务端只把它用在同一个玩家的下一个使用包上（见 ServerRails）。
 */
public record ExactRailPayload(int version, BlockPos pos, Block block, String shape) implements CustomPacketPayload {
    /** 格式变了就加一；服务端不认识的版本直接忽略（那次放置照原版来） */
    public static final int VERSION = 1;
    public static final Type<ExactRailPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("autyism-le", "exact_rail"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExactRailPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExactRailPayload::version,
            BlockPos.STREAM_CODEC, ExactRailPayload::pos,
            ByteBufCodecs.registry(Registries.BLOCK), ExactRailPayload::block,
            ByteBufCodecs.stringUtf8(32), ExactRailPayload::shape,
            ExactRailPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
