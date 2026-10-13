package com.autyism.ale.mixin.common;

import com.autyism.ale.rail.ExactRails;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 放方块的最后一步：这次放置带着形状请求（见 ExactRails）而且是请求说的那个铁轨时，换成请求的形状再放。
 * 原版在这之前的检查（能不能放、能不能替换、下面有没有支撑、有没有东西挡着）都已经照常做完；
 * 请求的形状待不住时整个不放（返回 false，原版当作放置失败，物品不消耗）。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @WrapOperation(method = "place", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BlockItem;placeBlock(Lnet/minecraft/world/item/context/BlockPlaceContext;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean ale$placeExactRail(BlockItem self, BlockPlaceContext ctx, BlockState state, Operation<Boolean> original) {
        ExactRails.Request request = ExactRails.current();
        if (request == null || !ExactRails.matches(request, ctx, state)) return original.call(self, ctx, state);
        BlockState exact = ExactRails.exactState(ctx, state, request);
        if (exact == null) return false;
        return ExactRails.placing(() -> original.call(self, ctx, exact));
    }
}
