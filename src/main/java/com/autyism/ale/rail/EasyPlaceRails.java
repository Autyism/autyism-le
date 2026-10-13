package com.autyism.ale.rail;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.Supplier;

/**
 * Litematica 的轻松放置放铁轨：服务端装了 ALE 时，铁轨直接放成投影里的形状（上坡、弯道都一次放好，
 * 不用先放别的铁轨，也不会把旁边放好的铁轨拉歪）。放不住的形状（上坡那一侧还没有方块）先不放，
 * 在动作栏提示。服务端没装 ALE 时一切照旧。
 */
public final class EasyPlaceRails {
    private EasyPlaceRails() {
    }

    /** 包在轻松放置调用的 MultiPlayerGameMode.useItemOn 外面 */
    public static InteractionResult useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, Supplier<InteractionResult> original) {
        if (!AleConfigs.Generic.EASY_PLACE_EXACT_RAILS.getBooleanValue() || !ClientRails.isAvailable()) return original.get();
        WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) return original.get();
        // 这一下实际会放在哪一格（点到可替换的方块就是那一格，否则是点到的面外面那一格），和服务端算法一样
        BlockPos pos = new BlockPlaceContext(new UseOnContext(player, hand, hit)).getClickedPos();
        BlockState wanted = schematic.getBlockState(pos);
        Property<RailShape> property = ExactRails.shapeProperty(wanted);
        if (property == null) return original.get();
        if (!(player.getItemInHand(hand).getItem() instanceof BlockItem item) || item.getBlock() != wanted.getBlock()) return original.get();
        if (!ExactRails.canStay(player.level(), pos, wanted)) {
            player.displayClientMessage(needsSupportMessage(pos, wanted, property), true);
            return InteractionResult.FAIL;
        }
        return ClientRails.place(pos, wanted, original);
    }

    private static Component needsSupportMessage(BlockPos pos, BlockState rail, Property<RailShape> property) {
        if (!Block.canSupportRigidBlock(net.minecraft.client.Minecraft.getInstance().level, pos.below())) {
            return Component.translatable("autyism-le.message.rail_needs_floor", rail.getBlock().getName());
        }
        Direction high = ExactRails.highSide(rail.getValue(property));
        String side = high == null ? "" : high.getSerializedName();
        return Component.translatable("autyism-le.message.rail_needs_high_side", rail.getBlock().getName(),
                Component.translatable("autyism-le.direction." + side));
    }
}
