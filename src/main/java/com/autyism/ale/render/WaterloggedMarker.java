package com.autyism.ale.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 需求 6：方块的含水状态和投影不同时，在黄色“状态错误”标记之上，在方块每一面画一个蓝色的 “W”。
 * 其他属性同时也不对时照样画（朝向也不对时和红色 D 一起画，各占半边）。
 */
public final class WaterloggedMarker {
    private WaterloggedMarker() {
    }

    /** 投影状态与世界状态的含水属性不同（不管别的属性是否也不同；不同但同组的方块比较它们都有的含水属性） */
    public static boolean waterloggedDiffers(BlockState schematic, BlockState client) {
        if (!schematic.hasProperty(BlockStateProperties.WATERLOGGED) || !client.hasProperty(BlockStateProperties.WATERLOGGED)) return false;
        return !schematic.getValue(BlockStateProperties.WATERLOGGED).equals(client.getValue(BlockStateProperties.WATERLOGGED));
    }

    /** 投影状态与世界状态是否只差含水属性 */
    public static boolean onlyWaterloggedDiffers(BlockState schematic, BlockState client) {
        if (schematic.getBlock() != client.getBlock() || !waterloggedDiffers(schematic, client)) return false;
        return schematic.setValue(BlockStateProperties.WATERLOGGED, client.getValue(BlockStateProperties.WATERLOGGED)) == client;
    }

    // “W” 的四条笔画（面内坐标 u 向右、v 向上，0..1）
    public static final float[][] STROKES = {
            {0.14f, 0.84f, 0.31f, 0.16f},
            {0.31f, 0.16f, 0.50f, 0.62f},
            {0.50f, 0.62f, 0.69f, 0.16f},
            {0.69f, 0.16f, 0.86f, 0.84f},
    };
    private static final float HALF_WIDTH = 0.05f;
    /** 两个字母并排时的缩放、笔画半宽和各自的位置（左边 D，右边 W） */
    public static final float PAIR_SCALE = 0.55f;
    private static final float PAIR_HALF_WIDTH = 0.034f;
    public static final float PAIR_LEFT_U = -0.038f;
    public static final float PAIR_RIGHT_U = 0.422f;
    public static final float PAIR_V = 0.225f;
    /** 浮在面外侧的距离，保证盖在黄色标记上面 */
    private static final float LIFT = 0.008f;

    /**
     * 往 POSITION_COLOR 四边形缓冲里写 6 个面的 W。
     *
     * @param rel 方块的区块内相对坐标（与 Litematica 覆盖层一致）
     */
    public static void emit(BufferBuilder buffer, BlockPos rel, int argb) {
        emit(buffer, rel, argb, STROKES);
    }

    /** 往 POSITION_COLOR 四边形缓冲里写 6 个面的字母（笔画格式同 {@link #STROKES}），占满整个面 */
    public static void emit(BufferBuilder buffer, BlockPos rel, int argb, float[][] strokes) {
        emit(buffer, rel, argb, strokes, 0f, 0f, 1f, HALF_WIDTH);
    }

    /** 两个字母并排时的一个：left 为 true 画在左半边，否则画在右半边 */
    public static void emitHalf(BufferBuilder buffer, BlockPos rel, int argb, float[][] strokes, boolean left) {
        emit(buffer, rel, argb, strokes, left ? PAIR_LEFT_U : PAIR_RIGHT_U, PAIR_V, PAIR_SCALE, PAIR_HALF_WIDTH);
    }

    /** 字母缩放 scale 后平移到 (u0, v0) */
    private static void emit(BufferBuilder buffer, BlockPos rel, int argb, float[][] strokes, float u0, float v0, float scale, float halfWidth) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        for (Direction face : Direction.values()) {
            for (float[] s : strokes) {
                stroke(buffer, rel, face, u0 + s[0] * scale, v0 + s[1] * scale, u0 + s[2] * scale, v0 + s[3] * scale, halfWidth, r, g, b, a);
            }
        }
    }

    private static void stroke(BufferBuilder buffer, BlockPos rel, Direction face, float u0, float v0, float u1, float v1, float halfWidth,
                               float r, float g, float b, float a) {
        float du = u1 - u0, dv = v1 - v0;
        float len = (float) Math.sqrt(du * du + dv * dv);
        float nu = -dv / len * halfWidth, nv = du / len * halfWidth;
        float[][] corners = {
                {u0 + nu, v0 + nv}, {u1 + nu, v1 + nv}, {u1 - nu, v1 - nv}, {u0 - nu, v0 - nv}
        };
        for (float[] c : corners) {
            float[] p = toBlock(face, c[0], c[1]);
            buffer.addVertex(rel.getX() + p[0], rel.getY() + p[1], rel.getZ() + p[2]).setColor(r, g, b, a);
        }
    }

    /** 面内坐标 → 方块内坐标（含向外抬起） */
    private static float[] toBlock(Direction face, float u, float v) {
        return switch (face) {
            case NORTH -> new float[]{1 - u, v, -LIFT};
            case SOUTH -> new float[]{u, v, 1 + LIFT};
            case WEST -> new float[]{-LIFT, v, u};
            case EAST -> new float[]{1 + LIFT, v, 1 - u};
            case UP -> new float[]{u, 1 + LIFT, 1 - v};
            case DOWN -> new float[]{u, -LIFT, v};
        };
    }
}
