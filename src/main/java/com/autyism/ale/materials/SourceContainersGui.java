package com.autyism.ale.materials;

import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Containers" button on Litematica's material list screen: how many chosen containers count, which of them are
 * still unknown (hover), left-click asks them all for their contents again, right-click forgets them all.
 * While the screen is open, the available counts follow when contents become known.
 */
public final class SourceContainersGui {
    private static final int MAX_LINES = 16;
    private static WeakReference<GuiMaterialList> gui = new WeakReference<>(null);
    private static WeakReference<ButtonGeneric> button = new WeakReference<>(null);
    private static int shownVersion = -1;

    private SourceContainersGui() {
    }

    /** Adds the button (material list screen init), or nothing when no containers are chosen */
    public static void attach(GuiMaterialList screen) {
        if (!SourceContainers.enabled() || SourceContainers.spots().isEmpty()) return;
        ButtonGeneric b = new ButtonGeneric(0, 0, -1, 20, label());
        b.setHoverStrings(hover());
        int[] spot = com.autyism.ale.gui.ButtonRail.endOfBottomRow(screen);
        b.setPosition(spot[0], spot[1]);
        screen.addButton(b, (btn, mouseButton) -> {
            if (mouseButton == 1) {
                SourceContainers.clear();
            } else {
                SourceContainers.refreshNow();
            }
            refreshCounts(screen);
            screen.initGui();
        });
        gui = new WeakReference<>(screen);
        button = new WeakReference<>(b);
        shownVersion = SourceContainers.version();
    }

    /** Client tick: contents changed while the screen is open, so the counts and the button follow */
    public static void tick(Minecraft client) {
        GuiMaterialList screen = gui.get();
        if (screen == null || client.screen != screen) return;
        if (shownVersion == SourceContainers.version()) return;
        shownVersion = SourceContainers.version();
        refreshCounts(screen);
        ButtonGeneric b = button.get();
        if (b != null) {
            b.setDisplayString(label());
            b.setHoverStrings(hover());
        }
    }

    @Nullable
    public static ButtonGeneric currentButton() {
        return button.get();
    }

    private static void refreshCounts(GuiMaterialList screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) MaterialListUtils.updateAvailableCounts(screen.getMaterialList().getMaterialsAll(), mc.player);
    }

    public static String label() {
        int n = SourceContainers.spots().size(), unknown = SourceContainers.unknownCount();
        return unknown > 0 ? StringUtils.translate("autyism-le.gui.button.material_sources.unknown", n, unknown)
                : StringUtils.translate("autyism-le.gui.button.material_sources", n);
    }

    private static List<String> hover() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        lines.add(StringUtils.translate("autyism-le.gui.button.material_sources.hover"));
        String dim = mc.level != null ? SourceContainers.dim(mc.level) : "";
        int shown = 0;
        for (SourceContainers.Spot s : SourceContainers.spots()) {
            if (shown++ >= MAX_LINES) {
                lines.add(StringUtils.translate("autyism-le.gui.label.material_sources.more", SourceContainers.spots().size() - MAX_LINES));
                break;
            }
            String where = s.pos().getX() + " " + s.pos().getY() + " " + s.pos().getZ();
            String name = s.dim().equals(dim) && mc.level != null && mc.level.hasChunkAt(s.pos())
                    ? mc.level.getBlockState(s.pos()).getBlock().getName().getString() : s.dim();
            String state;
            if (s.gone()) state = StringUtils.translate("autyism-le.gui.label.material_sources.gone");
            else if (!s.known()) state = StringUtils.translate("autyism-le.gui.label.material_sources.unknown");
            else state = StringUtils.translate("autyism-le.gui.label.material_sources.items", s.itemCount(), ago(s.lastSeen()));
            lines.add("§7" + name + " §f" + where + "§7: " + state);
        }
        lines.add(StringUtils.translate("autyism-le.gui.label.material_sources.clicks"));
        return lines;
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60) return StringUtils.translate("autyism-le.gui.label.ago.now");
        if (s < 3600) return StringUtils.translate("autyism-le.gui.label.ago.minutes", s / 60);
        if (s < 86400) return StringUtils.translate("autyism-le.gui.label.ago.hours", s / 3600);
        return StringUtils.translate("autyism-le.gui.label.ago.days", s / 86400);
    }
}
