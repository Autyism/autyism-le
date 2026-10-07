package com.autyism.ale.replace;

import com.autyism.ale.AleMod;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 替换之后让界面马上反映出来：材料列表重新统计，用到这个投影的放置重新渲染（替换时已标记），
 * 正在用的验证器重新检查。放置的投影世界是在之后几个 tick 里重建的，所以过一会儿再统计一次。
 */
public final class MaterialRefresh {
    private MaterialRefresh() {
    }

    private record Pending(Runnable task, int[] ticks) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** 替换完成后调用（对话框已关闭，回到了材料列表） */
    static void afterReplace(MaterialListBase list, MaterialReplacer.Target target) {
        refreshList(list);
        restartVerifiers(target);
        // 投影世界重建要几个 tick：之后再统计一次（放置的材料列表是按投影世界数的）
        later(20, () -> {
            refreshList(list);
            restartVerifiers(target);
        });
    }

    private static void refreshList(MaterialListBase list) {
        try {
            list.reCreateMaterialList();
        } catch (Throwable t) {
            AleMod.LOGGER.warn("Could not refresh the material list after replacing", t);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuiMaterialList gui && gui.getMaterialList() == list) gui.initGui();
    }

    /** 已经开始过的验证重新开始（换了方块以后旧的结果不再对） */
    private static void restartVerifiers(MaterialReplacer.Target target) {
        Minecraft mc = Minecraft.getInstance();
        WorldSchematic world = SchematicWorldHandler.getSchematicWorld();
        if (mc.level == null || world == null) return;
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllPlacementsOfSchematic(target.schematic())) {
            try {
                SchematicVerifier verifier = placement.getSchematicVerifier();
                if (verifier == null || !(verifier.isActive() || verifier.isFinished() || verifier.isPaused())) continue;
                verifier.reset();
                verifier.startVerification(mc.level, world, placement, null);
            } catch (Throwable t) {
                AleMod.LOGGER.warn("Could not restart the schematic verifier after replacing", t);
            }
        }
    }

    static void later(int ticks, Runnable task) {
        PENDING.add(new Pending(task, new int[]{ticks}));
    }

    /** 客户端每 tick 调用 */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (--p.ticks()[0] <= 0) {
                due.add(p.task());
                it.remove();
            }
        }
        for (Runnable r : due) r.run();
    }
}
