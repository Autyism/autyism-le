package com.autyism.ale.gui;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 在界面打开时“选取方块”（鼠标中键 / 选取方块键）：取鼠标指着的世界里的方块；
 * 鼠标在界面上时取准星对着的方块。世界里的方块和投影里的方块哪个近取哪个。
 */
public final class WorldPick {
    private static final double RANGE = 160;

    private WorldPick() {
    }

    /** 这次鼠标 / 键盘事件是不是“选取方块”键 */
    public static boolean isPickButton(net.minecraft.client.input.MouseButtonEvent click) {
        return Minecraft.getInstance().options.keyPickItem.matchesMouse(click);
    }

    public static boolean isPickKey(net.minecraft.client.input.KeyEvent event) {
        return Minecraft.getInstance().options.keyPickItem.matches(event);
    }

    /**
     * @param guiX       鼠标在界面上的坐标
     * @param useCursor  true：沿着鼠标所指的方向找；false：沿着准星找
     */
    @Nullable
    public static BlockState pick(double guiX, double guiY, boolean useCursor) {
        Minecraft mc = Minecraft.getInstance();
        Entity viewer = mc.getCameraEntity();
        if (mc.level == null || viewer == null) return null;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Vector3f dir;
        if (useCursor && mc.screen != null && mc.screen.width > 0 && mc.screen.height > 0) {
            double nx = 2.0 * guiX / mc.screen.width - 1.0, ny = 1.0 - 2.0 * guiY / mc.screen.height;
            double tan = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
            double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            // 相机坐标里向前是 -Z、向右是 +X、向上是 +Y
            dir = new Vector3f((float) (nx * tan * aspect), (float) (ny * tan), -1f).normalize().rotate(camera.rotation());
        } else {
            dir = new Vector3f(0f, 0f, -1f).rotate(camera.rotation());
        }
        Vec3 end = eye.add(dir.x() * RANGE, dir.y() * RANGE, dir.z() * RANGE);
        BlockHitResult worldHit = clip(mc.level, eye, end, viewer);
        Level schematic = SchematicWorldHandler.getSchematicWorld();
        BlockHitResult schematicHit = schematic != null ? clip(schematic, eye, end, viewer) : null;
        if (worldHit == null && schematicHit == null) return null;
        if (schematicHit == null || (worldHit != null && worldHit.getLocation().distanceToSqr(eye) <= schematicHit.getLocation().distanceToSqr(eye))) {
            return mc.level.getBlockState(worldHit.getBlockPos());
        }
        return schematic.getBlockState(schematicHit.getBlockPos());
    }

    @Nullable
    private static BlockHitResult clip(Level level, Vec3 from, Vec3 to, Entity viewer) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;
        return level.getBlockState(hit.getBlockPos()).isAir() ? null : hit;
    }
}
