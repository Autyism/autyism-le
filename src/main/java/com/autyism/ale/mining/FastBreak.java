package com.autyism.ale.mining;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 打印机一下挖掉“两 tick 才挖得掉”的方块（默认只有深板岩）。
 * <p>
 * 原版服务端同一时间只认一个玩家在挖的一个方块：一 tick 挖不完的方块只能一个接一个挖，大范围秒挖做不到
 * （深板岩用下界合金镐、效率 V、急迫 II，一 tick 只有 54%）。服务端也装了 ALE 时，客户端在开始挖掘的包前一刻发一个请求，
 * 服务端把紧接着的那个包当作秒破，条件是：方块在 autyism-le:fast_break 标签里（默认只有深板岩），而且按原版的算法
 * 这个玩家一 tick 的挖掘进度至少 50%（要用对工具；效率、急迫、挖掘疲劳、在不在地上、在不在水里都照原版算）。
 * 距离、权限、游戏模式照原版检查。不带请求的挖掘（玩家自己挖、别的模组）和别的方块完全照原版。
 */
public final class FastBreak {
    /** 一 tick 至少这么多进度才秒破（两 tick 以内本来就挖得掉的方块） */
    public static final float MIN_PROGRESS = 0.5F;
    /** 可以秒破的方块；服务器可以用数据包改 */
    public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("autyism-le", "fast_break"));

    private FastBreak() {
    }

    /** 一 tick 挖 progressPerTick 的玩家能不能秒破这个方块（客户端用服务端发来的标签判断） */
    public static boolean eligible(BlockState state, float progressPerTick) {
        return progressPerTick >= MIN_PROGRESS && state.is(BLOCKS);
    }
}
