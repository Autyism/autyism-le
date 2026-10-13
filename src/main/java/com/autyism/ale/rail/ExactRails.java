package com.autyism.ale.rail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 铁轨按指定形状放下（客户端和服务端共用）。
 * <p>
 * 一次放置带着“要的形状”（{@link Request}）时：铁轨直接以这个形状放下，原版不再照旁边的铁轨改它的形状，
 * 也不去拉动旁边已经放好的铁轨。放不住的形状（下面没有支撑，或者上坡那一侧没有支撑）整个不放 ——
 * 原版会先放下再掉成物品，这里不会。
 * <p>
 * 请求挂在当前线程上：客户端预测放置时由轻松放置或打印机设置（{@link ClientRails}），服务端处理那个玩家的使用包时
 * 由 {@link ServerRails} 设置。只有方块、位置、玩家都对得上的那次放置才会用它。
 */
public final class ExactRails {
    /** 在 pos 放 block，形状是 shape；player 是放的人（对不上的放置不用这个请求） */
    public record Request(BlockPos pos, Block block, RailShape shape, @Nullable Player player) {
    }

    private static final ThreadLocal<Request> REQUEST = new ThreadLocal<>();
    /** 正在按请求放铁轨（这期间原版的铁轨连接计算不做） */
    private static final ThreadLocal<int[]> PLACING = ThreadLocal.withInitial(() -> new int[1]);

    private ExactRails() {
    }

    @Nullable
    public static Request current() {
        return REQUEST.get();
    }

    /** 带着请求执行 action（结束后恢复原来的请求） */
    public static <T> T with(Request request, Supplier<T> action) {
        Request previous = REQUEST.get();
        REQUEST.set(request);
        try {
            return action.get();
        } finally {
            if (previous == null) REQUEST.remove();
            else REQUEST.set(previous);
        }
    }

    /** 原版此刻是否不该改铁轨的形状 */
    public static boolean isPlacing() {
        return PLACING.get()[0] > 0;
    }

    /** 按请求放置期间执行（原版的铁轨连接计算跳过） */
    public static boolean placing(Supplier<Boolean> action) {
        int[] depth = PLACING.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }

    /** 铁轨的形状属性（普通铁轨有弯道，动力/探测/激活铁轨只有直的），不是铁轨时为 null */
    @Nullable
    public static Property<RailShape> shapeProperty(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock rail ? rail.getShapeProperty() : null;
    }

    @Nullable
    public static RailShape shapeByName(String name) {
        for (RailShape shape : RailShape.values()) {
            if (shape.getSerializedName().equals(name)) return shape;
        }
        return null;
    }

    /** 上坡铁轨高的那一侧（需要支撑的那一侧），平的为 null */
    @Nullable
    public static Direction highSide(RailShape shape) {
        return switch (shape) {
            case ASCENDING_EAST -> Direction.EAST;
            case ASCENDING_WEST -> Direction.WEST;
            case ASCENDING_NORTH -> Direction.NORTH;
            case ASCENDING_SOUTH -> Direction.SOUTH;
            default -> null;
        };
    }

    /** 这个形状的铁轨能不能待在 pos：和原版判断铁轨掉落的规则一样（下面要能支撑；上坡时高的那一侧也要） */
    public static boolean canStay(BlockGetter level, BlockPos pos, BlockState rail) {
        Property<RailShape> property = shapeProperty(rail);
        if (property == null || !Block.canSupportRigidBlock(level, pos.below())) return false;
        Direction high = highSide(rail.getValue(property));
        return high == null || Block.canSupportRigidBlock(level, pos.relative(high));
    }

    /** 这次放置（ctx 要放 state）是不是请求说的那一次 */
    public static boolean matches(Request request, BlockPlaceContext ctx, BlockState state) {
        return state.getBlock() == request.block()
                && ctx.getClickedPos().equals(request.pos())
                && (request.player() == null || ctx.getPlayer() == request.player());
    }

    /**
     * 请求的形状放到 state 上。形状不属于这种铁轨时返回原来的 state（照原版放）；
     * 这个形状在这里待不住时返回 null（整个不放）。
     */
    @Nullable
    public static BlockState exactState(BlockPlaceContext ctx, BlockState state, Request request) {
        Property<RailShape> property = shapeProperty(state);
        if (property == null || !property.getPossibleValues().contains(request.shape())) return state;
        BlockState exact = state.setValue(property, request.shape());
        return canStay(ctx.getLevel(), ctx.getClickedPos(), exact) ? exact : null;
    }
}
