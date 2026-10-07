//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.preview;

import com.autyism.ale.AleMod;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 读取浏览器里的各种投影文件（.litematic、Sponge .schem、原版结构 .nbt、MCEdit .schematic），
 * 统一变成 Litematica 的投影对象；另外能只读文件头里的尺寸，用来在加载前判断“太大不做缩略图”。
 */
public final class SchematicFiles {
    private SchematicFiles() {
    }

    /** 读整个投影；不支持或读不出来时返回 null（不抛异常） */
    @Nullable
    public static LitematicaSchematic load(Path file) {
        try {
            Path dir = file.getParent();
            String name = file.getFileName().toString();
            FileType type = FileType.fromFile(file);
            if (type == FileType.LITEMATICA_SCHEMATIC) {
                return LitematicaSchematic.createFromFile(dir, name);
            }
            if (type == FileType.SPONGE_SCHEMATIC || type == FileType.VANILLA_STRUCTURE || type == FileType.SCHEMATICA_SCHEMATIC) {
                return LitematicaSchematic.createFromFile(dir, name, type);
            }
        } catch (Throwable t) {
            AleMod.LOGGER.debug("Could not load {} for a preview", file, t);
        }
        return null;
    }

    /**
     * 只读文件里记录的外框尺寸，返回体积；读不到时返回 -1。
     * 用 NBT 的“只收集指定字段”方式读取，不会把方块数据全部读进内存。
     */
    public static long readVolume(Path file) {
        try {
            FileType type = FileType.fromFile(file);
            CollectFields fields;
            if (type == FileType.LITEMATICA_SCHEMATIC) {
                fields = new CollectFields(new FieldSelector("Metadata", CompoundTag.TYPE, "EnclosingSize"));
            } else if (type == FileType.VANILLA_STRUCTURE) {
                fields = new CollectFields(new FieldSelector(ListTag.TYPE, "size"));
            } else if (type == FileType.SPONGE_SCHEMATIC || type == FileType.SCHEMATICA_SCHEMATIC) {
                fields = new CollectFields(
                        new FieldSelector(ShortTag.TYPE, "Width"), new FieldSelector(ShortTag.TYPE, "Height"), new FieldSelector(ShortTag.TYPE, "Length"),
                        new FieldSelector("Schematic", ShortTag.TYPE, "Width"), new FieldSelector("Schematic", ShortTag.TYPE, "Height"),
                        new FieldSelector("Schematic", ShortTag.TYPE, "Length"));
            } else {
                return -1;
            }
            NbtIo.parseCompressed(file, fields, NbtAccounter.unlimitedHeap());
            Tag result = fields.getResult();
            if (!(result instanceof CompoundTag root)) return -1;
            if (type == FileType.LITEMATICA_SCHEMATIC) {
                CompoundTag size = root.getCompoundOrEmpty("Metadata").getCompoundOrEmpty("EnclosingSize");
                return volume(size.getIntOr("x", 0), size.getIntOr("y", 0), size.getIntOr("z", 0));
            }
            if (type == FileType.VANILLA_STRUCTURE) {
                ListTag size = root.getListOrEmpty("size");
                if (size.size() < 3 || !(size.get(0) instanceof IntTag)) return -1;
                return volume(size.getIntOr(0, 0), size.getIntOr(1, 0), size.getIntOr(2, 0));
            }
            CompoundTag dims = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
            return volume(dims.getShortOr("Width", (short) 0) & 0xFFFF, dims.getShortOr("Height", (short) 0) & 0xFFFF,
                    dims.getShortOr("Length", (short) 0) & 0xFFFF);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static long volume(int x, int y, int z) {
        if (x <= 0 || y <= 0 || z <= 0) return -1;
        return (long) Math.abs(x) * Math.abs(y) * Math.abs(z);
    }
}
//?}
