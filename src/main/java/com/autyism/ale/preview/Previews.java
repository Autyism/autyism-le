//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.preview;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 投影浏览器预览功能的总开关和缓存管理。
 * 装了 DimasKama 的 Schematic Preview 时这些功能由它负责，ALE 的版本不出现。
 * 离开浏览器（以及它打开的全屏预览、图标对话框）后释放所有缩略图、预览网格和显卡纹理。
 */
public final class Previews {
    private Previews() {
    }

    public static final boolean ORIGINAL_INSTALLED = FabricLoader.getInstance().isModLoaded("schematicpreview");

    private static final ThumbnailCache THUMBNAILS = new ThumbnailCache();
    /** 文件夹 → 里面第一个投影（“小图标 + 预览”用）；后台查找，查好之前为 null */
    private static final Map<Path, Optional<Path>> FIRST_SCHEMATIC = new HashMap<>();

    /** 浏览器的显示方式、缩略图、大预览和图标是否由 ALE 提供 */
    public static boolean browserActive() {
        return !ORIGINAL_INSTALLED && AleConfigs.Generic.BROWSER_PREVIEWS.getBooleanValue();
    }

    /** 材料列表的“替换”是否由 ALE 提供 */
    public static boolean replaceActive() {
        return !ORIGINAL_INSTALLED && AleConfigs.Generic.MATERIAL_REPLACE.getBooleanValue();
    }

    public static ThumbnailCache thumbnails() {
        return THUMBNAILS;
    }

    /** 文件夹里按名字排第一个的投影文件；还在查或没有时返回 null */
    @Nullable
    public static Path firstSchematicIn(Path dir) {
        Optional<Path> found = FIRST_SCHEMATIC.get(dir);
        if (found != null) return found.orElse(null);
        FIRST_SCHEMATIC.put(dir, Optional.empty());
        PreviewWorkers.submit(() -> {
            Path first = null;
            try (Stream<Path> files = Files.list(dir)) {
                first = files.filter(Files::isRegularFile).filter(Previews::isSchematicFile)
                        .min(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT))).orElse(null);
            } catch (Exception ignored) {
            }
            Path result = first;
            Minecraft.getInstance().execute(() -> FIRST_SCHEMATIC.put(dir, Optional.ofNullable(result)));
        });
        return null;
    }

    static boolean isSchematicFile(Path p) {
        String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".schematic") || n.endsWith(".nbt");
    }

    /** 每个客户端 tick：不在浏览器相关的界面时释放所有缓存 */
    public static void tick(Minecraft mc) {
        if (THUMBNAILS.isEmpty() && PreviewSession.current() == null && FIRST_SCHEMATIC.isEmpty()) return;
        if (!isPreviewScreen(GuiCompat.screen())) releaseAll();
        else THUMBNAILS.dropUnused();
    }

    public static boolean isPreviewScreen(@Nullable Screen screen) {
        return screen instanceof GuiSchematicBrowserBase || screen instanceof PreviewOwnedScreen;
    }

    public static void releaseAll() {
        THUMBNAILS.clear();
        FIRST_SCHEMATIC.clear();
        PreviewSession.closeCurrent();
    }

    /** ALE 自己从浏览器打开的界面（全屏预览、图标对话框）：打开它们时不释放缓存 */
    public interface PreviewOwnedScreen {
    }
}
//?}
