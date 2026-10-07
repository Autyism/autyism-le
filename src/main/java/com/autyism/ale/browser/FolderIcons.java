//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.browser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 投影浏览器里文件夹（和投影文件）的自定义图标。存在 ALE 自己的设置文件里，
 * 以相对 schematics 目录的路径为键，不往用户的投影文件夹里写任何东西。
 */
public final class FolderIcons {
    private FolderIcons() {
    }

    /** 图标放在哪里 */
    public enum Placement {
        /** 代替名字前面的小图标 */
        SMALL("small"),
        /** 大图标（列表左边的图标栏 / 网格的图片区） */
        LARGE("large"),
        /** 小图标 + 图片区显示文件夹里第一个投影的预览 */
        SMALL_WITH_PREVIEW("preview");

        public final String id;

        Placement(String id) {
            this.id = id;
        }

        public Placement next() {
            Placement[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        public String displayName() {
            return StringUtils.translate("autyism-le.gui.icon_placement." + this.id);
        }

        static Placement of(String id) {
            for (Placement p : values()) if (p.id.equals(id)) return p;
            return SMALL;
        }
    }

    /** 一个条目的设置：物品 ID（空 = 默认图标）和位置 */
    public record Choice(String itemId, Placement placement) {
        public boolean hasItem() {
            return !this.itemId.isBlank();
        }

        /** 物品图标；ID 不对时为 null */
        @Nullable
        public ItemStack stack() {
            return stackOf(this.itemId);
        }
    }

    private static final Map<String, Choice> ICONS = new LinkedHashMap<>();
    private static final Map<String, ItemStack> STACKS = new LinkedHashMap<>();

    @Nullable
    public static Choice get(Path path) {
        return ICONS.get(key(path));
    }

    /** choice = null：恢复默认 */
    public static void set(Path path, @Nullable Choice choice) {
        String k = key(path);
        if (choice == null || (!choice.hasItem() && choice.placement() == Placement.SMALL)) ICONS.remove(k);
        else ICONS.put(k, choice);
    }

    /** 物品 ID 能不能用（空也算能用：表示默认图标） */
    public static boolean isValidItem(String id) {
        return id.isBlank() || stackOf(id) != null;
    }

    @Nullable
    public static ItemStack stackOf(String id) {
        String s = id.trim();
        if (s.isEmpty()) return null;
        ItemStack cached = STACKS.get(s);
        if (cached != null) return cached.isEmpty() ? null : cached;
        Identifier rl = Identifier.tryParse(s.contains(":") ? s : "minecraft:" + s);
        Item item = rl != null ? BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR) : Items.AIR;
        ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        STACKS.put(s, stack);
        return stack.isEmpty() ? null : stack;
    }

    /** 相对 schematics 根目录的路径（用 / 分隔）；不在根目录下时用完整路径 */
    static String key(Path path) {
        Path p = path.toAbsolutePath().normalize();
        try {
            Path root = DataManager.getSchematicsBaseDirectory().toAbsolutePath().normalize();
            if (p.startsWith(root)) return root.relativize(p).toString().replace('\\', '/');
        } catch (Exception ignored) {
        }
        return p.toString().replace('\\', '/');
    }

    public static void read(@Nullable JsonElement element) {
        ICONS.clear();
        if (element == null || !element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject o = e.getValue().getAsJsonObject();
            String item = o.has("item") ? o.get("item").getAsString() : "";
            String placement = o.has("position") ? o.get("position").getAsString() : "small";
            ICONS.put(e.getKey(), new Choice(item, Placement.of(placement)));
        }
    }

    public static JsonElement write() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Choice> e : ICONS.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("item", e.getValue().itemId());
            o.addProperty("position", e.getValue().placement().id);
            root.add(e.getKey(), o);
        }
        return root;
    }
}
//?}
