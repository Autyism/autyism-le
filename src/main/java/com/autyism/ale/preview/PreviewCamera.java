package com.autyism.ale.preview;

import com.autyism.ale.config.AleConfigs;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 预览相机。普通模式绕投影中心转（拖动改角度，滚轮拉近拉远）；自由视角模式在投影里飞（拖动转视角，滚轮和移动键前后左右上下）。
 * 角度规则与原版相同：yaw 0 朝南，pitch 正值朝下看。
 */
public final class PreviewCamera {
    /** 拖动一个界面像素转多少度 */
    public static final float DEGREES_PER_PIXEL = 1.1F;

    private float centerX, centerY, centerZ;
    private float radius = 1.0F;
    private float yaw;
    private float pitch;
    private float distance;
    private float startDistance;
    private float fov;

    private boolean free;
    private float freeX, freeY, freeZ;
    private float freeYaw, freePitch;

    private int version;

    /** 按投影大小重新对准：中心、半径、默认角度、能看到整个投影的距离 */
    public void frame(int sizeX, int sizeY, int sizeZ) {
        this.centerX = sizeX * 0.5F;
        this.centerY = sizeY * 0.5F;
        this.centerZ = sizeZ * 0.5F;
        this.radius = Math.max(0.75F, 0.5F * (float) Math.sqrt((double) sizeX * sizeX + (double) sizeY * sizeY + (double) sizeZ * sizeZ));
        resetView();
    }

    /** 角度、视野回到设置里的默认值，距离回到能看到整个投影 */
    public void resetView() {
        this.fov = (float) AleConfigs.Preview.FOV.getDoubleValue();
        // 设置里的水平角按“转动模型”的方向计：-45 表示从西南角看过去（相机朝东北）
        this.yaw = wrap(180.0F - (float) AleConfigs.Preview.YAW.getDoubleValue());
        this.pitch = clampPitch((float) AleConfigs.Preview.PITCH.getDoubleValue());
        this.startDistance = fitDistance();
        this.distance = this.startDistance;
        this.free = false;
        this.version++;
    }

    private float fitDistance() {
        double half = Math.toRadians(Math.max(5.0, Math.min(170.0, this.fov)) * 0.5);
        return (float) (this.radius / Math.sin(half));
    }

    public void copyFrom(PreviewCamera other) {
        this.centerX = other.centerX;
        this.centerY = other.centerY;
        this.centerZ = other.centerZ;
        this.radius = other.radius;
        this.yaw = other.yaw;
        this.pitch = other.pitch;
        this.distance = other.distance;
        this.startDistance = other.startDistance;
        this.fov = other.fov;
        this.free = other.free;
        this.freeX = other.freeX;
        this.freeY = other.freeY;
        this.freeZ = other.freeZ;
        this.freeYaw = other.freeYaw;
        this.freePitch = other.freePitch;
        this.version++;
    }

    /** 每次相机变化都加一，用来判断要不要重画 */
    public int version() {
        return this.version;
    }

    public boolean isFree() {
        return this.free;
    }

    /** 打开自由视角时从当前位置和朝向开始飞；关掉时回到绕中心转的视角 */
    public void setFree(boolean free) {
        if (free == this.free) return;
        if (free) {
            Vector3f eye = orbitEye();
            this.freeX = eye.x;
            this.freeY = eye.y;
            this.freeZ = eye.z;
            this.freeYaw = this.yaw;
            this.freePitch = this.pitch;
        }
        this.free = free;
        this.version++;
    }

    /** 鼠标拖动（界面像素） */
    public void drag(double dx, double dy) {
        if (dx == 0 && dy == 0) return;
        if (this.free) {
            this.freeYaw = wrap(this.freeYaw + (float) dx * DEGREES_PER_PIXEL);
            this.freePitch = clampPitch(this.freePitch + (float) dy * DEGREES_PER_PIXEL);
        } else {
            this.yaw = wrap(this.yaw + (float) dx * DEGREES_PER_PIXEL);
            this.pitch = clampPitch(this.pitch + (float) dy * DEGREES_PER_PIXEL);
        }
        this.version++;
    }

    /** 滚轮：每格沿视线移动开始距离的四分之一（自由视角时往前飞） */
    public void scroll(double amount) {
        if (amount == 0) return;
        float step = this.startDistance * 0.25F * (float) amount;
        if (this.free) {
            move(step, 0, 0);
        } else {
            this.distance = Math.max(0.05F, this.distance - step);
            this.version++;
        }
    }

    /** 自由视角移动：forward 朝视线方向，strafe 向右，up 向上（方块） */
    public void move(float forward, float strafe, float up) {
        if (!this.free || (forward == 0 && strafe == 0 && up == 0)) return;
        Vector3f f = direction(this.freeYaw, this.freePitch);
        double yawRad = Math.toRadians(this.freeYaw);
        float rx = (float) -Math.cos(yawRad), rz = (float) -Math.sin(yawRad);
        this.freeX += f.x * forward + rx * strafe;
        this.freeY += f.y * forward + up;
        this.freeZ += f.z * forward + rz * strafe;
        this.version++;
    }

    /** 自由视角每秒飞多少格：投影越大越快 */
    public float flySpeed() {
        return Math.max(4.0F, this.radius * 0.6F);
    }

    public float fov() {
        return this.fov;
    }

    public float radius() {
        return this.radius;
    }

    public float yaw() {
        return this.free ? this.freeYaw : this.yaw;
    }

    public float pitch() {
        return this.free ? this.freePitch : this.pitch;
    }

    public float distance() {
        return this.distance;
    }

    /** 相机在投影坐标里的位置 */
    public Vector3f eye() {
        return this.free ? new Vector3f(this.freeX, this.freeY, this.freeZ) : orbitEye();
    }

    private Vector3f orbitEye() {
        Vector3f f = direction(this.yaw, this.pitch);
        return new Vector3f(this.centerX - f.x * this.distance, this.centerY - f.y * this.distance, this.centerZ - f.z * this.distance);
    }

    /** 世界到相机的旋转（与原版相机相同的约定） */
    public Matrix4f viewRotation() {
        Quaternionf q = new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(yaw()), -(float) Math.toRadians(pitch()), 0.0F);
        return new Matrix4f().rotation(q.conjugate());
    }

    public Matrix4f projection(float aspect) {
        Vector3f eye = eye();
        float dx = eye.x - this.centerX, dy = eye.y - this.centerY, dz = eye.z - this.centerZ;
        float toCenter = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float far = toCenter + this.radius * 2.0F + 64.0F;
        float near = Math.max(0.05F, Math.min(1.0F, (toCenter - this.radius) * 0.5F));
        return new Matrix4f().perspective((float) Math.toRadians(Math.max(5.0, Math.min(170.0, this.fov))), Math.max(0.01F, aspect), near, far);
    }

    private static Vector3f direction(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector3f((float) (-Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (Math.cos(y) * Math.cos(p)));
    }

    private static float clampPitch(float p) {
        return Math.max(-90.0F, Math.min(90.0F, p));
    }

    private static float wrap(float a) {
        a %= 360.0F;
        if (a >= 180.0F) a -= 360.0F;
        if (a < -180.0F) a += 360.0F;
        return a;
    }
}
