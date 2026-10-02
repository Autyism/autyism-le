package com.autyism.ale.render;

/**
 * 需求 12：透过玻璃显示投影。
 * <p>
 * 原因：原版的玻璃/染色玻璃在半透明层（TRANSLUCENT）绘制并写入深度；Litematica 的半透明投影方块在它之后画，
 * 错误标记在整帧最后画，二者都做深度测试，玻璃后面的部分全部被裁掉。
 * 处理：在原版画半透明层“之前”先画投影的半透明层和错误标记，玻璃之后会以半透明方式盖在上面，
 * 投影透过玻璃依然可见；同一帧里 Litematica 原本的绘制调用随之跳过，避免画两遍。
 * 如果这一帧没有成功提前绘制（例如被其他渲染模组改写了流程），就保持 Litematica 原来的行为。
 */
public final class GlassRenderState {
    private GlassRenderState() {
    }

    /** 正在由 ALE 提前绘制（此时不要拦截） */
    public static boolean drawingEarly;
    /** 本帧半透明投影方块已提前绘制 */
    public static boolean translucentDrawnEarly;
    /** 本帧错误标记已提前绘制 */
    public static boolean overlaysDrawnEarly;

    public static void newFrame() {
        drawingEarly = false;
        translucentDrawnEarly = false;
        overlaysDrawnEarly = false;
    }
}
