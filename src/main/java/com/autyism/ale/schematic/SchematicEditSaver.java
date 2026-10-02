package com.autyism.ale.schematic;

import com.autyism.ale.AleMod;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.data.tag.util.DataFileUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Map;

/**
 * 需求 4：把投影放置的内存修改（Schematic Preview 的“替换”、Litematica 编辑模式的修改）永久保存。
 * <p>
 * 这两种修改都直接改的是放置所引用的 {@link LitematicaSchematic}（方块容器），所以直接把它写回文件即可；
 * 投影世界只是它的渲染副本。写之前清理掉方块已被替换、不再有方块实体的残留 NBT。
 */
public final class SchematicEditSaver {
    private SchematicEditSaver() {
    }

    public static final String EXTENSION = ".litematic";

    /** 覆盖保存到放置对应的 .litematic 文件 */
    public static boolean saveOverwrite(SchematicPlacement placement) {
        LitematicaSchematic schematic = placement.getSchematic();
        Path file = placement.getSchematicFile();
        if (file == null) file = schematic.getFile();
        if (file == null) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "autyism-le.message.save_edits.no_file");
            return false;
        }
        if (!file.getFileName().toString().toLowerCase().endsWith(EXTENSION)) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "autyism-le.message.save_edits.not_litematic", file.getFileName().toString());
            return false;
        }
        cleanupStaleBlockEntities(schematic);
        schematic.getMetadata().setTimeModifiedToNow();
        try {
            // 先写临时文件再替换，避免写一半出错把原文件弄坏
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".ale_tmp");
            if (!DataFileUtils.writeCompoundDataToCompressedNbtFile(tmp, schematic.writeToData())) {
                throw new IllegalStateException("write failed");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            AleMod.LOGGER.error("Failed to save schematic edits to {}", file, e);
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "autyism-le.message.save_edits.failed", file.toString());
            return false;
        }
        schematic.getMetadata().clearModifiedSinceSaved();
        InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "autyism-le.message.save_edits.saved", file.getFileName().toString());
        return true;
    }

    /** 另存为新的 .litematic 文件（与原文件同一目录；没有原文件时存到 schematics 目录） */
    public static boolean saveAs(SchematicPlacement placement, String name, boolean override) {
        LitematicaSchematic schematic = placement.getSchematic();
        Path original = placement.getSchematicFile();
        Path dir = original != null && original.getParent() != null ? original.getParent() : DataManager.getSchematicsBaseDirectory();
        String fileName = name.trim();
        if (fileName.isEmpty()) {
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.ERROR, "autyism-le.message.save_edits.empty_name");
            return false;
        }
        cleanupStaleBlockEntities(schematic);
        schematic.getMetadata().setTimeModifiedToNow();
        if (!schematic.writeToFile(dir, fileName, override)) {
            return false;
        }
        String shown = fileName.endsWith(EXTENSION) ? fileName : fileName + EXTENSION;
        InfoUtils.showGuiOrInGameMessage(Message.MessageType.SUCCESS, "autyism-le.message.save_edits.saved_as", shown);
        return true;
    }

    /** 默认的“另存为”文件名：原文件名 + _edited */
    public static String defaultSaveAsName(SchematicPlacement placement) {
        Path file = placement.getSchematicFile();
        String base = file != null ? file.getFileName().toString() : placement.getName();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base + "_edited";
    }

    /** 方块被替换成没有方块实体的方块后，残留的方块实体数据会被写进文件，这里清理掉 */
    public static int cleanupStaleBlockEntities(LitematicaSchematic schematic) {
        int removed = 0;
        for (String region : schematic.getAreas().keySet()) {
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);
            Map<BlockPos, ?> beMap = schematic.getBlockEntityMapForRegion(region);
            if (container == null || beMap == null) continue;
            Iterator<? extends Map.Entry<BlockPos, ?>> it = beMap.entrySet().iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next().getKey();
                if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
                        || pos.getX() >= container.getSize().getX() || pos.getY() >= container.getSize().getY() || pos.getZ() >= container.getSize().getZ()) {
                    continue;
                }
                BlockState state = container.get(pos.getX(), pos.getY(), pos.getZ());
                if (!state.hasBlockEntity()) {
                    it.remove();
                    removed++;
                }
            }
        }
        return removed;
    }
}
