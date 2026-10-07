package com.autyism.ale.preview;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * 预览网格的顶点收集器：按原版方块顶点格式（BLOCK）写进自己管理的本机内存。
 * <p>
 * 不用原版 BufferBuilder：光影模组会在渲染世界时改写 BufferBuilder 的顶点格式，后台线程里建网格时可能碰上，
 * 自己写就不受影响。写入的坐标会加上当前区段在投影里的偏移（方块模型和流体都按区段内坐标输出）。
 * 光照一律写成满亮，预览不依赖世界光照。
 */
final class MeshVertexSink implements VertexConsumer {
    static final VertexFormat FORMAT = DefaultVertexFormat.BLOCK;
    static final int STRIDE = FORMAT.getVertexSize();
    private static final int POS = FORMAT.getOffset(VertexFormatElement.POSITION);
    private static final int COLOR = FORMAT.getOffset(VertexFormatElement.COLOR);
    private static final int UV0 = FORMAT.getOffset(VertexFormatElement.UV0);
    private static final int UV2 = FORMAT.getOffset(VertexFormatElement.UV2);
    /** 26.1 起方块格式没有法线（面的明暗已经算进颜色里）：-1 */
    private static final int NORMAL = FORMAT.contains(VertexFormatElement.NORMAL) ? FORMAT.getOffset(VertexFormatElement.NORMAL) : -1;
    private static final short FULL_BRIGHT = 240;

    private long address;
    private int capacity;
    private int vertices;
    /** 当前顶点的起始地址（-1 = 还没有顶点） */
    private long current = -1;
    private float offsetX, offsetY, offsetZ;

    MeshVertexSink(int initialVertices) {
        this.capacity = Math.max(64, initialVertices) * STRIDE;
        this.address = MemoryUtil.nmemAlloc(this.capacity);
        if (this.address == 0L) throw new OutOfMemoryError("ALE preview mesh");
    }

    void setOffset(float x, float y, float z) {
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
    }

    int vertexCount() {
        return this.vertices;
    }

    int quadCount() {
        return this.vertices / 4;
    }

    long address() {
        return this.address;
    }

    /** 某个方块画到一半出错时退回到它之前的状态，避免留下不完整的四边形 */
    void rollback(int vertexCount) {
        this.vertices = Math.min(this.vertices, vertexCount);
        this.current = -1;
    }

    /** 收尾：只保留完整的四边形；返回指向数据的 ByteBuffer（不复制，归调用者 free） */
    ByteBuffer finish() {
        this.vertices -= this.vertices % 4;
        return MemoryUtil.memByteBuffer(this.address, this.vertices * STRIDE);
    }

    void free() {
        if (this.address != 0L) {
            MemoryUtil.nmemFree(this.address);
            this.address = 0L;
        }
    }

    float posX(int vertex) {
        return MemoryUtil.memGetFloat(this.address + (long) vertex * STRIDE + POS);
    }

    float posY(int vertex) {
        return MemoryUtil.memGetFloat(this.address + (long) vertex * STRIDE + POS + 4);
    }

    float posZ(int vertex) {
        return MemoryUtil.memGetFloat(this.address + (long) vertex * STRIDE + POS + 8);
    }

    private void grow() {
        int newCapacity = this.capacity * 2;
        long newAddress = MemoryUtil.nmemRealloc(this.address, newCapacity);
        if (newAddress == 0L) throw new OutOfMemoryError("ALE preview mesh");
        this.current = this.current < 0 ? -1 : newAddress + (this.current - this.address);
        this.address = newAddress;
        this.capacity = newCapacity;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if ((long) (this.vertices + 1) * STRIDE > this.capacity) grow();
        long v = this.address + (long) this.vertices * STRIDE;
        MemoryUtil.memSet(v, 0, STRIDE);
        MemoryUtil.memPutFloat(v + POS, x + this.offsetX);
        MemoryUtil.memPutFloat(v + POS + 4, y + this.offsetY);
        MemoryUtil.memPutFloat(v + POS + 8, z + this.offsetZ);
        MemoryUtil.memPutInt(v + COLOR, -1);
        MemoryUtil.memPutShort(v + UV2, FULL_BRIGHT);
        MemoryUtil.memPutShort(v + UV2 + 2, FULL_BRIGHT);
        this.current = v;
        this.vertices++;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        if (this.current >= 0) {
            MemoryUtil.memPutByte(this.current + COLOR, (byte) r);
            MemoryUtil.memPutByte(this.current + COLOR + 1, (byte) g);
            MemoryUtil.memPutByte(this.current + COLOR + 2, (byte) b);
            MemoryUtil.memPutByte(this.current + COLOR + 3, (byte) a);
        }
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (this.current >= 0) {
            MemoryUtil.memPutFloat(this.current + UV0, u);
            MemoryUtil.memPutFloat(this.current + UV0 + 4, v);
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        // 方块格式没有覆盖层坐标
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        // 预览固定满亮（见类注释）
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (this.current >= 0 && NORMAL >= 0) {
            MemoryUtil.memPutByte(this.current + NORMAL, normal(x));
            MemoryUtil.memPutByte(this.current + NORMAL + 1, normal(y));
            MemoryUtil.memPutByte(this.current + NORMAL + 2, normal(z));
        }
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    private static byte normal(float f) {
        return (byte) ((int) (Math.max(-1.0F, Math.min(1.0F, f)) * 127.0F) & 0xFF);
    }
}
