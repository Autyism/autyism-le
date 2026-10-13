package com.autyism.ale.api;

import com.autyism.ale.rail.ClientRails;
import com.autyism.ale.rail.ExactRails;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * Exact rail placement: the public part of ALE that other client mods (for example a printer) can call.
 * Available since ALE 1.2.0 on Minecraft 1.21.11; client side only, call it on the client thread.
 *
 * <p>When the server runs ALE too (or in single player), a rail placed through {@link #place} lands in exactly
 * the given shape. The server checks the placement like any other one (reach, permissions, the item in hand,
 * whether the spot can be replaced, support below), then puts the rail down in that shape. It does not reshape it
 * from the rails around it and does not bend rails that are already placed. A shape that cannot stay (an ascending
 * rail without a solid block on its high side, or any rail without support below) is not placed at all: no item is
 * used and nothing drops. Without ALE on the server nothing is sent and {@link #isAvailable()} is false.
 *
 * <pre>{@code
 * if (ExactRailPlacement.isAvailable() && ExactRailPlacement.canStay(mc.level, pos, wanted)) {
 *     InteractionResult result = ExactRailPlacement.place(pos, wanted,
 *             () -> mc.gameMode.useItemOn(mc.player, hand, hitResult));
 * }
 * }</pre>
 *
 * <p>The placement must place {@code wanted}'s block at {@code pos} with one use-item-on packet sent on the calling
 * thread inside the callback (normally {@code MultiPlayerGameMode.useItemOn}): a click on a face of a neighbouring
 * block, or on a replaceable block at {@code pos}. The item in hand must place that block. Any other placement in
 * the callback is not affected.
 */
public final class ExactRailPlacement {
    /** Raised when this API changes in a way that callers must know about. */
    public static final int API_VERSION = 1;

    private ExactRailPlacement() {
    }

    /** True while connected to a server (or single player world) that runs ALE's exact rail placement. */
    public static boolean isAvailable() {
        return ClientRails.isAvailable();
    }

    /** True for rail, powered rail, detector rail and activator rail states (anything with a rail shape). */
    public static boolean isRail(BlockState state) {
        return ExactRails.shapeProperty(state) != null;
    }

    /**
     * Whether a rail in this state can stay at {@code pos} with the blocks that are there now, by the same rules as
     * vanilla: support below, and for ascending shapes a solid block on the high side.
     */
    public static boolean canStay(BlockGetter level, BlockPos pos, BlockState rail) {
        return ExactRails.canStay(level, pos, rail);
    }

    /**
     * Runs {@code placement} so that the rail it places at {@code pos} gets exactly {@code rail}'s shape.
     *
     * @return the result of {@code placement}, or {@link InteractionResult#FAIL} without running it when exact
     * placement is not available here or {@code rail} is not a rail
     */
    public static InteractionResult place(BlockPos pos, BlockState rail, Supplier<InteractionResult> placement) {
        return ClientRails.place(pos, rail, placement);
    }
}
