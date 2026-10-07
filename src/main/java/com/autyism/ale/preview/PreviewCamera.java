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
    /** 拖过整个预览区的宽度（上下拖：高度）转半圈：面板里和全屏里拖同样比例的距离转同样的角度 */
    public static final float DEGREES_PER_VIEW = 180.0F;
    /** 默认视角四周留的边（1.0 = 刚好贴边） */
    private static final double MARGIN = 1.04;

    private float centerX, centerY, centerZ;
    /** 绕着转的点：默认是投影中心，按画面对齐时会挪一点，让投影在画面里居中 */
    private float aimX, aimY, aimZ;
    private float halfX = 0.5F, halfY = 0.5F, halfZ = 0.5F;
    private float radius = 1.0F;
    private float yaw;
    private float pitch;
    private float distance;
    private float startDistance;
    private float fov;
    /** 默认距离是按哪个画面宽高比算的（NaN：还没按画面算过） */
    private float fittedAspect = Float.NaN;

    private boolean free;
    private float freeX, freeY, freeZ;
    private float freeYaw, freePitch;

    private int version;

    /** 按投影大小重新对准：中心、半径、默认角度、能看到整个投影的距离 */
    public void frame(int sizeX, int sizeY, int sizeZ) {
        this.centerX = sizeX * 0.5F;
        this.centerY = sizeY * 0.5F;
        this.centerZ = sizeZ * 0.5F;
        this.halfX = Math.max(0.5F, sizeX * 0.5F);
        this.halfY = Math.max(0.5F, sizeY * 0.5F);
        this.halfZ = Math.max(0.5F, sizeZ * 0.5F);
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
        this.aimX = this.centerX;
        this.aimY = this.centerY;
        this.aimZ = this.centerZ;
        this.fittedAspect = Float.NaN;
        this.free = false;
        this.version++;
    }

    /**
     * 回到默认视角后第一次画的时候调用一次：按这个画面的宽高比对准，
     * 默认角度下整个投影（8 个角）在画面里居中、四周留一点边。
     * 之后（转动、换到全屏）距离和中心都不变，和面板里看到的一样大。
     */
    public void fitTo(float aspect) {
        if (this.free || !(aspect > 0.0F) || !Float.isNaN(this.fittedAspect)) return;
        this.fittedAspect = aspect;
        double tanV = Math.tan(Math.toRadians(Math.max(5.0, Math.min(170.0, this.fov)) * 0.5));
        double tanH = tanV * aspect;
        Vector3f f = direction(this.yaw, this.pitch);
        double yawRad = Math.toRadians(this.yaw);
        Vector3f r = new Vector3f((float) -Math.cos(yawRad), 0.0F, (float) -Math.sin(yawRad));
        Vector3f u = new Vector3f(r).cross(f);
        float[][] corners = new float[8][3];
        double nearest = 0.0;
        for (int i = 0; i < 8; i++) {
            float cx = (i & 1) == 0 ? -this.halfX : this.halfX;
            float cy = (i & 2) == 0 ? -this.halfY : this.halfY;
            float cz = (i & 4) == 0 ? -this.halfZ : this.halfZ;
            corners[i][0] = cx * r.x + cy * r.y + cz * r.z;
            corners[i][1] = cx * u.x + cy * u.y + cz * u.z;
            corners[i][2] = cx * f.x + cy * f.y + cz * f.z;
            nearest = Math.max(nearest, -corners[i][2]);
        }
        // 反复调整：把画面上的外框移到正中，再按外框大小拉远或拉近
        double offX = 0.0, offY = 0.0, d = Math.max(fitDistance(), nearest + 0.5);
        for (int iter = 0; iter < 40; iter++) {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (float[] c : corners) {
                double z = d + c[2];
                double px = (c[0] - offX) / (z * tanH), py = (c[1] - offY) / (z * tanV);
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
            }
            offX += (minX + maxX) * 0.5 * d * tanH;
            offY += (minY + maxY) * 0.5 * d * tanV;
            double fill = Math.max(maxX - minX, maxY - minY) * 0.5 * MARGIN;
            double next = Math.max(nearest + 0.5, d * fill);
            if (Math.abs(next - d) < 1.0E-4 * d) {
                d = next;
                break;
            }
            d = next;
        }
        this.aimX = this.centerX + (float) (offX * r.x + offY * u.x);
        this.aimY = this.centerY + (float) (offX * r.y + offY * u.y);
        this.aimZ = this.centerZ + (float) (offX * r.z + offY * u.z);
        this.startDistance = (float) d;
        this.distance = this.startDistance;
    }

    private float fitDistance() {
        double half = Math.toRadians(Math.max(5.0, Math.min(170.0, this.fov)) * 0.5);
        return (float) (this.radius / Math.sin(half));
    }

    public void copyFrom(PreviewCamera other) {
        this.centerX = other.centerX;
        this.centerY = other.centerY;
        this.centerZ = other.centerZ;
        this.aimX = other.aimX;
        this.aimY = other.aimY;
        this.aimZ = other.aimZ;
        this.halfX = other.halfX;
        this.halfY = other.halfY;
        this.halfZ = other.halfZ;
        this.fittedAspect = other.fittedAspect;
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

    /** 打开自由视角时从当前位置和朝向开始飞；关掉时回到默认视角（默认角度和距离） */
    public void setFree(boolean free) {
        if (free == this.free) return;
        if (!free) {
            resetView();
            return;
        }
        Vector3f eye = orbitEye();
        this.freeX = eye.x;
        this.freeY = eye.y;
        this.freeZ = eye.z;
        this.freeYaw = this.yaw;
        this.freePitch = this.pitch;
        this.free = true;
        this.version++;
    }

    /** 鼠标拖动（界面像素）；viewWidth / viewHeight 是预览区的大小 */
    public void drag(double dx, double dy, double viewWidth, double viewHeight) {
        if (dx == 0 && dy == 0) return;
        float yawStep = (float) (dx / Math.max(1.0, viewWidth)) * DEGREES_PER_VIEW;
        float pitchStep = (float) (dy / Math.max(1.0, viewHeight)) * DEGREES_PER_VIEW;
        if (this.free) {
            this.freeYaw = wrap(this.freeYaw + yawStep);
            this.freePitch = clampPitch(this.freePitch + pitchStep);
        } else {
            this.yaw = wrap(this.yaw + yawStep);
            this.pitch = clampPitch(this.pitch + pitchStep);
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
        return new Vector3f(this.aimX - f.x * this.distance, this.aimY - f.y * this.distance, this.aimZ - f.z * this.distance);
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
        float fovRad = (float) Math.toRadians(Math.max(5.0, Math.min(170.0, this.fov)));
        //? if >=26.2 {
        /*// 26.2 起深度反过来（近处大、清成 0），远近平面对调
        boolean zeroToOne = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        return new Matrix4f().setPerspective(fovRad, Math.max(0.01F, aspect), far, near, zeroToOne);
        *///?} else {
        return new Matrix4f().perspective(fovRad, Math.max(0.01F, aspect), near, far);
        //?}
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
