package com.autyism.ale.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 需求 6：方块其他状态都正确、只差 WATERLOGGED 时，在黄色“状态错误”标记之上，在方块每一面画一个蓝色的 “W”。
 */
public final class WaterloggedMarker {
    private WaterloggedMarker() {
    }

    /** 投影状态与世界状态是否只差含水属性 */
    public static boolean onlyWaterloggedDiffers(BlockState schematic, BlockState client) {
        if (schematic.getBlock() != client.getBlock()) return false;
        if (!schematic.hasProperty(BlockStateProperties.WATERLOGGED)) return false;
        if (schematic.getValue(BlockStateProperties.WATERLOGGED).equals(client.getValue(BlockStateProperties.WATERLOGGED))) return false;
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

    /** 往 POSITION_COLOR 四边形缓冲里写 6 个面的字母（笔画格式同 {@link #STROKES}） */
    public static void emit(BufferBuilder buffer, BlockPos rel, int argb, float[][] strokes) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        for (Direction face : Direction.values()) {
            for (float[] s : strokes) {
                stroke(buffer, rel, face, s[0], s[1], s[2], s[3], r, g, b, a);
            }
        }
    }

    private static void stroke(BufferBuilder buffer, BlockPos rel, Direction face, float u0, float v0, float u1, float v1,
                               float r, float g, float b, float a) {
        float du = u1 - u0, dv = v1 - v0;
        float len = (float) Math.sqrt(du * du + dv * dv);
        float nu = -dv / len * HALF_WIDTH, nv = du / len * HALF_WIDTH;
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
