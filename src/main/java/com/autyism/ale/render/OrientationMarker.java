package com.autyism.ale.render;

import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * 方块对、但朝向和投影不同时，在黄色“状态错误”标记之上，在方块每一面画一个红色的 “D”（Direction）。
 * 打印出来的东西不应该有朝向错误，这个标记让它一眼能看出来。含水也不对时和蓝色 W 各占半边一起画。
 */
public final class OrientationMarker {
    private OrientationMarker() {
    }

    /** “D” 的笔画（面内坐标 u 向右、v 向上，0..1）：左边竖线 + 右边的弧 */
    public static final float[][] STROKES = {
            {0.26f, 0.14f, 0.26f, 0.86f},
            {0.26f, 0.86f, 0.52f, 0.86f},
            {0.52f, 0.86f, 0.70f, 0.76f},
            {0.70f, 0.76f, 0.78f, 0.58f},
            {0.78f, 0.58f, 0.78f, 0.42f},
            {0.78f, 0.42f, 0.70f, 0.24f},
            {0.70f, 0.24f, 0.52f, 0.14f},
            {0.52f, 0.14f, 0.26f, 0.14f},
    };

    /** 至少有一个“朝向类”属性和投影不同（不管别的属性是否也不同；不同但同组的方块比较它们都有的属性） */
    public static boolean orientationDiffers(BlockState schematic, BlockState client) {
        for (Property<?> p : schematic.getProperties()) {
            if (isOrientation(p) && client.hasProperty(p) && !schematic.getValue(p).equals(client.getValue(p))) return true;
        }
        return false;
    }

    /** 朝向类属性：朝向、轴向、16 方向旋转、铁轨方向、贴墙/地/天花板、门轴、上下半、合成器朝向、滴水石锥上下 */
    static boolean isOrientation(Property<?> p) {
        Class<?> type = p.getValueClass();
        return type == Direction.class || type == Direction.Axis.class || type == FrontAndTop.class
                || type == RailShape.class || type == AttachFace.class || type == DoorHingeSide.class || type == Half.class
                || p == BlockStateProperties.ROTATION_16;
    }
}
