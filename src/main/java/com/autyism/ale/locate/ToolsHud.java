package com.autyism.ale.locate;

import com.autyism.ale.config.AleToolsConfigs;
import com.autyism.ale.diff.DiffSession;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * On-screen part of the wrong block guide: an arrow under the crosshair turning towards the target (in view space: up
 * on the screen is "look up", a target behind you points down), the distance, and what the schematic wants there vs
 * what is there. Also the small summary of the schematic diff in the top right corner.
 */
public final class ToolsHud implements IRenderer {
    public static final ToolsHud INSTANCE = new ToolsHud();
    /** For the tests: frames in which the arrow was drawn, and its last angle (radians, screen space, 0 = right) */
    public static volatile long arrowFrames;
    public static volatile double lastArrowAngle;
    public static volatile boolean lastAhead;
    public static volatile List<String> lastLines = List.of();
    public static volatile String lastDiffSummary = "";

    private ToolsHud() {
    }

    // MaLiLib's in-game overlay callback: 26.1 on an extract pass, 1.21.11 with GuiContext, before with GuiGraphics + Minecraft
    //? if >=26.1 {
    /*@Override
    public void onExtractGuiOverlayPost(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
        draw(ctx);
    }
    *///?} elif >=1.21.11 {
    @Override
    public void onRenderGameOverlayPostAdvanced(GuiContext ctx, float partialTicks, ProfilerFiller profiler) {
        draw(ctx);
    }
    //?} else {
    /*@Override
    public void onRenderGameOverlayPostAdvanced(GuiContext ctx, float partialTicks, ProfilerFiller profiler, Minecraft mc) {
        draw(ctx);
    }
    *///?}

    private static void draw(GuiContext ctx) {
        Minecraft c = Minecraft.getInstance();
        if (c.player == null || c.level == null || c.screen != null || hudHidden(c)) return;
        drawGuide(ctx, c);
        drawDiffSummary(ctx, c);
    }

    private static boolean hudHidden(Minecraft c) {
        //? if >=26.2 {
        /*return c.gui.hud.isHidden();
        *///?} else
        return c.options.hideGui;
    }

    // ---------------- guide ----------------

    private static void drawGuide(GuiContext ctx, Minecraft c) {
        if (!AleToolsConfigs.LOCATE.getBooleanValue()) return;
        WrongBlockGuide.Target t = WrongBlockGuide.target();
        int w = GuiUtils.getScaledWindowWidth(), h = GuiUtils.getScaledWindowHeight();
        int cx = w / 2, cy = h / 2 + 34;
        if (t == null) {
            if (WrongBlockGuide.searching()) {
                String s = StringUtils.translate("autyism-le.hud.locate.searching");
                textBox(ctx, c, List.of(s), cx, h / 2 + 16);
            }
            return;
        }
        Entity camera = c.getCameraEntity() != null ? c.getCameraEntity() : c.player;
        Vec3 eye = camera.getEyePosition();
        BlockPos p = t.pos();
        double dx = p.getX() + 0.5 - eye.x, dy = p.getY() + 0.5 - eye.y, dz = p.getZ() + 0.5 - eye.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        // camera basis from yaw / pitch (yaw 0 looks south, +z)
        double yaw = Math.toRadians(camera.getYRot()), pitch = Math.toRadians(camera.getXRot());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        // up = right x forward
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;
        double sx = dx * rx + dy * ry + dz * rz, sy = dx * ux + dy * uy + dz * uz, sf = dx * fx + dy * fy + dz * fz;
        int color = AleToolsConfigs.LOCATE_ARROW_COLOR.getIntegerValue() | 0xFF000000;
        // straight ahead (within ~4 degrees): a diamond instead of an arrow
        boolean ahead = sf > 0 && Math.hypot(sx, sy) < sf * 0.07;
        double angle = Math.atan2(-sy, sx);
        if (ahead) {
            diamond(ctx, cx, cy, 7, 0xC0000000);
            diamond(ctx, cx, cy, 5, color);
        } else {
            arrow(ctx, cx + 1, cy + 1, angle, 0xC0000000);
            arrow(ctx, cx, cy, angle, color);
        }
        lastArrowAngle = angle;
        lastAhead = ahead;
        arrowFrames++;
        textBox(ctx, c, lines(t, dist), cx, cy + 16);
    }

    static List<String> lines(WrongBlockGuide.Target t, double dist) {
        List<String> out = new ArrayList<>();
        BlockState want = WrongBlockGuide.expectedState(t), have = WrongBlockGuide.foundState(t);
        String kind = StringUtils.translate("autyism-le.hud.locate.kind." + kindId(t));
        out.add(kindColor(t) + kind + "§r  " + StringUtils.translate("autyism-le.hud.locate.distance", String.format(java.util.Locale.ROOT, "%.1f", dist)));
        out.add(StringUtils.translate("autyism-le.hud.locate.expected", name(want)));
        out.add(StringUtils.translate("autyism-le.hud.locate.found", name(have)));
        if (want.getBlock() == have.getBlock() && want != have) {
            List<String> props = WrongBlockGuide.differingProperties(want, have);
            for (int i = 0; i < Math.min(3, props.size()); i++) out.add("§7" + props.get(i));
        }
        lastLines = List.copyOf(out);
        return out;
    }

    private static String kindId(WrongBlockGuide.Target t) {
        if (t.fromDiff()) return switch (t.kind()) {
            case DiffSession.ADDED -> "added";
            case DiffSession.REMOVED -> "removed";
            default -> "changed";
        };
        return switch (t.kind()) {
            case LocateIndex.MISSING -> "missing";
            case LocateIndex.WRONG_BLOCK -> "wrong_block";
            case LocateIndex.WRONG_STATE -> "wrong_state";
            default -> "extra";
        };
    }

    private static String kindColor(WrongBlockGuide.Target t) {
        if (t.fromDiff()) return switch (t.kind()) {
            case DiffSession.ADDED -> "§a";
            case DiffSession.REMOVED -> "§c";
            default -> "§e";
        };
        return t.kind() == LocateIndex.MISSING ? "§b" : "§c";
    }

    static String name(BlockState state) {
        return state.getBlock().getName().getString();
    }

    private static void textBox(GuiContext ctx, Minecraft c, List<String> lines, int cx, int top) {
        var font = c.font;
        int width = 0;
        for (String s : lines) width = Math.max(width, font.width(s));
        int x = cx - width / 2 - 4, h = lines.size() * 10 + 4;
        RenderUtils.drawRect(ctx, x, top - 2, width + 8, h, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            ctx.drawString(font, s, cx - font.width(s) / 2, top + i * 10, 0xFFFFFFFF, true);
        }
    }

    // ---------------- arrow drawing (rows of 1 px rectangles, so it works the same on every version) ----------------

    private static void arrow(GuiContext ctx, int cx, int cy, double angle, int color) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        // pointing right: head tip (12, 0), wings (1, -8) / (1, 8); shaft (-11, -3) .. (2, 3)
        double[][] head = {{12, 0}, {1, -8}, {1, 8}};
        double[][] shaftA = {{-11, -3}, {2, -3}, {2, 3}};
        double[][] shaftB = {{-11, -3}, {2, 3}, {-11, 3}};
        triangle(ctx, rotate(head, cos, sin, cx, cy), color);
        triangle(ctx, rotate(shaftA, cos, sin, cx, cy), color);
        triangle(ctx, rotate(shaftB, cos, sin, cx, cy), color);
    }

    private static void diamond(GuiContext ctx, int cx, int cy, int r, int color) {
        triangle(ctx, new double[][]{{cx, cy - r}, {cx + r, cy}, {cx - r, cy}}, color);
        triangle(ctx, new double[][]{{cx - r, cy}, {cx + r, cy}, {cx, cy + r}}, color);
    }

    private static double[][] rotate(double[][] pts, double cos, double sin, int cx, int cy) {
        double[][] out = new double[pts.length][2];
        for (int i = 0; i < pts.length; i++) {
            out[i][0] = cx + pts[i][0] * cos - pts[i][1] * sin;
            out[i][1] = cy + pts[i][0] * sin + pts[i][1] * cos;
        }
        return out;
    }

    /** Fill a triangle row by row (pixel centres inside the triangle) */
    private static void triangle(GuiContext ctx, double[][] p, int color) {
        double minY = Math.min(p[0][1], Math.min(p[1][1], p[2][1])), maxY = Math.max(p[0][1], Math.max(p[1][1], p[2][1]));
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            double yc = y + 0.5;
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                double[] a = p[i], b = p[(i + 1) % 3];
                if ((yc < a[1]) == (yc < b[1])) continue; // edge does not cross this row
                double x = a[0] + (yc - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
                lo = Math.min(lo, x);
                hi = Math.max(hi, x);
            }
            if (lo > hi) continue;
            int x0 = (int) Math.round(lo), x1 = (int) Math.round(hi);
            if (x1 > x0) RenderUtils.drawRect(ctx, x0, y, x1 - x0, 1, color);
        }
    }

    // ---------------- diff summary ----------------

    private static void drawDiffSummary(GuiContext ctx, Minecraft c) {
        if (!AleToolsConfigs.DIFF.getBooleanValue()) return;
        String s = DiffSession.summaryLine();
        lastDiffSummary = s == null ? "" : s;
        if (s == null) return;
        var font = c.font;
        int w = GuiUtils.getScaledWindowWidth();
        int x = w - font.width(s) - 6;
        RenderUtils.drawRect(ctx, x - 3, 3, font.width(s) + 6, 12, 0x90000000);
        ctx.drawString(font, s, x, 5, 0xFFFFFFFF, true);
    }
}
