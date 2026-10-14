package com.autyism.ale.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 需求 6：方块的含水状态和投影不同时，在黄色“状态错误”标记之上，在方块每一面画一个蓝色的 “W”。
 * 其他属性同时也不对时照样画（朝向也不对、或者需要填充时，几个字母并排画）。
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

    /**
     * 一个面上并排画几个字母（例如 D、W、F）：一个时占满整面，两个时各占半边，三个时各占三分之一。
     * glyphs[i] 用 colors[i] 的颜色
     */
    public static void emitRow(BufferBuilder buffer, BlockPos rel, java.util.List<float[][]> glyphs, int[] colors) {
        int n = glyphs.size();
        if (n == 0) return;
        if (n == 1) {
            emit(buffer, rel, colors[0], glyphs.get(0));
            return;
        }
        // 每个字母缩小后，字母框（0..1）的中心依次放在 centers 上
        float scale = n == 2 ? 0.55f : 0.36f;
        float halfWidth = n == 2 ? 0.034f : 0.025f;
        float[] centers = n == 2 ? new float[]{0.25f, 0.75f} : new float[]{0.18f, 0.5f, 0.82f};
        for (int i = 0; i < Math.min(n, centers.length); i++) {
            emit(buffer, rel, colors[i], glyphs.get(i), centers[i] - 0.5f * scale, 0.5f - 0.5f * scale, scale, halfWidth);
        }
    }

    /**
     * 大箱子这种两格拼成的方块：rel 和 rel 朝 toOther（EAST 或 SOUTH）的那一格看成一个长方体，只画一个字母：
     * 长的四个面画在正中间（两格的接缝上），两头各画一个
     */
    public static void emitDouble(BufferBuilder buffer, BlockPos rel, int argb, float[][] strokes, Direction toOther) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        for (Direction face : Direction.values()) {
            float shift;
            if (face == toOther) shift = 1f; // 另一格外侧的那一头
            else if (face == toOther.getOpposite()) shift = 0f; // 这一格外侧的那一头
            else shift = 0.5f; // 长的面：挪到两格中间
            float ox = toOther.getStepX() * shift, oz = toOther.getStepZ() * shift;
            for (float[] s : strokes) {
                stroke(buffer, rel, face, s[0], s[1], s[2], s[3], HALF_WIDTH, r, g, b, a, ox, oz);
            }
        }
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
        stroke(buffer, rel, face, u0, v0, u1, v1, halfWidth, r, g, b, a, 0f, 0f);
    }

    private static void stroke(BufferBuilder buffer, BlockPos rel, Direction face, float u0, float v0, float u1, float v1, float halfWidth,
                               float r, float g, float b, float a, float ox, float oz) {
        float du = u1 - u0, dv = v1 - v0;
        float len = (float) Math.sqrt(du * du + dv * dv);
        float nu = -dv / len * halfWidth, nv = du / len * halfWidth;
        float[][] corners = {
                {u0 + nu, v0 + nv}, {u1 + nu, v1 + nv}, {u1 - nu, v1 - nv}, {u0 - nu, v0 - nv}
        };
        for (float[] c : corners) {
            float[] p = toBlock(face, c[0], c[1]);
            buffer.addVertex(rel.getX() + p[0] + ox, rel.getY() + p[1], rel.getZ() + p[2] + oz).setColor(r, g, b, a);
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
