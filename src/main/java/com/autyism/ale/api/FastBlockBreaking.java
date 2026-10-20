package com.autyism.ale.api;

import com.autyism.ale.mining.ClientFastBreak;
import com.autyism.ale.mining.FastBreak;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fast block breaking: the public part of ALE that other client mods (for example a printer) can call.
 * Available since ALE 1.2.0 on Minecraft 1.21.11; client side only, call it on the client thread.
 *
 * <p>A vanilla server tracks one block being mined per player, so a block that takes two ticks to mine can only be
 * mined one after another. When the server runs ALE too (or in single player), a START_DESTROY_BLOCK sent through
 * {@link #breakNow} breaks the block at once when it is in the {@code autyism-le:fast_break} block tag (by default only
 * deepslate) and the player mines at least {@link #MIN_PROGRESS} of it per tick by the vanilla formula (right tool,
 * efficiency, haste, mining fatigue, on the ground, under water). Reach, permissions and game mode are checked as usual,
 * and the block drops, wears the tool and counts like any other block broken at once. Any other mining stays vanilla.
 *
 * <pre>{@code
 * if (FastBlockBreaking.canBreakNow(state, progressPerTick)) {
 *     FastBlockBreaking.breakNow(pos, () -> sendStartDestroyBlock(pos));
 * }
 * }</pre>
 */
public final class FastBlockBreaking {
    /** Raised when this API changes in a way that callers must know about. */
    public static final int API_VERSION = 1;
    /** Mining progress per tick needed for a block in the tag to break at once. */
    public static final float MIN_PROGRESS = FastBreak.MIN_PROGRESS;

    private FastBlockBreaking() {
    }

    /** True while connected to a server (or single player world) that runs ALE's fast block breaking. */
    public static boolean isAvailable() {
        return ClientFastBreak.isAvailable();
    }

    /**
     * Whether the server would break this block at once for a player who mines {@code progressPerTick} of it per tick
     * (the block tag as the server sent it). Blocks that already break in one tick do not need this.
     */
    public static boolean canBreakNow(BlockState state, float progressPerTick) {
        return isAvailable() && FastBreak.eligible(state, progressPerTick);
    }

    /**
     * Runs {@code start}, which must send the START_DESTROY_BLOCK packet for {@code pos} on the calling thread, with the
     * request right in front of that packet. Without ALE on the server it just runs {@code start}.
     */
    public static void breakNow(BlockPos pos, Runnable start) {
        ClientFastBreak.breakNow(pos, start);
    }
}
