package com.autyism.ale.render;

import com.autyism.ale.materials.MaterialExtras;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.util.BlockInfoAlignment;
import fi.dy.masa.litematica.util.RayTraceUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 需求 5a：看向投影里的实体（物品展示框、矿车、盔甲架、画……）时，像方块一样显示“投影 vs 世界”的对比。
 * 世界里对应的实体：同类型、离投影实体 0.75 格以内最近的那个。
 */
public final class EntityInfoOverlay {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double RANGE = 10.0;

    private EntityInfoOverlay() {
    }

    /** 视线命中的投影实体（比视线命中的方块更近时才算） */
    @Nullable
    public static Entity findTargetedSchematicEntity() {
        WorldSchematic ws = SchematicWorldHandler.getSchematicWorld();
        Entity camera = mc.getCameraEntity();
        if (ws == null || camera == null || mc.level == null) return null;
        Vec3 eye = camera.getEyePosition();
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB search = new AABB(eye, end).inflate(1.0);
        for (Entity e : MaterialExtras.schematicEntities(ws, search)) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.1).clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best == null) return null;
        // 有更近的方块（投影或世界）挡在前面时不显示
        var trace = RayTraceUtils.getGenericTrace(mc.level, camera, RANGE, true, false, false);
        if (trace != null) {
            BlockHitResult bh = trace.getBlockHitResult();
            if (bh != null && bh.getType() == HitResult.Type.BLOCK && eye.distanceToSqr(bh.getLocation()) + 0.01 < bestDist) {
                // 物品展示框贴在方块上，命中点几乎一样；允许 0.3 格的误差
                if (eye.distanceTo(bh.getLocation()) + 0.3 < Math.sqrt(bestDist)) return null;
            }
        }
        return best;
    }

    @Nullable
    public static Entity findWorldCounterpart(Entity schematicEntity) {
        Level level = mc.level;
        if (level == null) return null;
        Entity best = null;
        double bestDist = 0.75 * 0.75;
        for (Entity e : level.getEntities((Entity) null, schematicEntity.getBoundingBox().inflate(0.75), e -> e.getType() == schematicEntity.getType())) {
            double d = e.position().distanceToSqr(schematicEntity.position());
            if (d <= bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 实体的描述行 */
    public static List<String> describe(Entity e) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + e.getType().getDescription().getString());
        if (e instanceof ItemFrame frame) {
            lines.add(StringUtils.translate("autyism-le.info.entity.facing", dir(frame.getDirection())));
            ItemStack item = frame.getItem();
            lines.add(StringUtils.translate("autyism-le.info.entity.item", item.isEmpty() ? "-" : item.getHoverName().getString()));
            lines.add(StringUtils.translate("autyism-le.info.entity.rotation", frame.getRotation()));
        } else if (e instanceof ArmorStand stand) {
            lines.add(StringUtils.translate("autyism-le.info.entity.yaw", Math.round(e.getYRot())));
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
                ItemStack s = stand.getItemBySlot(slot);
                if (!s.isEmpty()) lines.add(slot.getName() + ": " + s.getHoverName().getString());
            }
        } else if (e instanceof Painting painting) {
            lines.add(StringUtils.translate("autyism-le.info.entity.facing", dir(painting.getDirection())));
            lines.add(painting.getVariant().unwrapKey().map(k -> k.identifier().getPath()).orElse("?"));
        } else {
            lines.add(StringUtils.translate("autyism-le.info.entity.yaw", Math.round(e.getYRot())));
        }
        return lines;
    }

    private static String dir(Direction d) {
        return d.getSerializedName();
    }

    public static boolean matches(Entity schematic, @Nullable Entity world) {
        return world != null && describe(schematic).equals(describe(world));
    }

    /** 画对比框：左边投影，右边世界；标题按是否一致着色 */
    public static void render(GuiContext ctx, Entity schematicEntity) {
        Entity world = findWorldCounterpart(schematicEntity);
        List<String> left = describe(schematicEntity);
        List<String> right = world != null ? describe(world) : List.of("§c" + StringUtils.translate("autyism-le.info.entity.missing"));
        boolean same = matches(schematicEntity, world);
        String title = same ? "§a" + StringUtils.translate("autyism-le.info.entity.match")
                : (world == null ? "§c" + StringUtils.translate("autyism-le.info.entity.missing_title") : "§e" + StringUtils.translate("autyism-le.info.entity.mismatch"));
        String hl = StringUtils.translate("autyism-le.info.entity.schematic");
        String hr = StringUtils.translate("autyism-le.info.entity.world");

        var font = mc.font;
        int colW = Math.max(font.width(hl), font.width(hr));
        for (String s : left) colW = Math.max(colW, font.width(s));
        for (String s : right) colW = Math.max(colW, font.width(s));
        colW += 20;
        int lines = Math.max(left.size(), right.size());
        int width = colW * 2 + 12;
        int height = 30 + lines * 10;
        int offY = Configs.InfoOverlays.BLOCK_INFO_OVERLAY_OFFSET_Y.getIntegerValue();
        BlockInfoAlignment align = (BlockInfoAlignment) Configs.InfoOverlays.BLOCK_INFO_OVERLAY_ALIGNMENT.getOptionListValue();
        int x = GuiUtils.getScaledWindowWidth() / 2 - width / 2;
        int y = align == BlockInfoAlignment.CENTER ? GuiUtils.getScaledWindowHeight() / 2 + offY : offY + 6;

        RenderUtils.drawOutlinedBox(ctx, x, y, width, height, 0xFF000000, 0xFF999999);
        ctx.drawString(font, title, x + width / 2 - font.width(title) / 2, y + 4, 0xFFFFFFFF, false);
        ItemStack icon = iconFor(schematicEntity);
        if (!icon.isEmpty()) ctx.renderItem(icon, x + 4, y + 2);
        ctx.drawString(font, "§n" + hl, x + 6, y + 16, 0xFFFFFFFF, false);
        ctx.drawString(font, "§n" + hr, x + 6 + colW + 6, y + 16, 0xFFFFFFFF, false);
        for (int i = 0; i < left.size(); i++) {
            String s = left.get(i);
            boolean diff = world != null && (i >= right.size() || !right.get(i).equals(s));
            ctx.drawString(font, (diff ? "§e" : "") + s, x + 6, y + 28 + i * 10, 0xFFFFFFFF, false);
        }
        for (int i = 0; i < right.size(); i++) {
            String s = right.get(i);
            boolean diff = i >= left.size() || !left.get(i).equals(s);
            ctx.drawString(font, (diff && world != null ? "§e" : "") + s, x + 6 + colW + 6, y + 28 + i * 10, 0xFFFFFFFF, false);
        }
    }

    private static ItemStack iconFor(Entity e) {
        Item item = MaterialExtras.itemForEntity(e);
        return item != null ? new ItemStack(item) : new ItemStack(Items.NAME_TAG);
    }
}
