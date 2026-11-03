package com.autyism.ale.schematic;

import com.autyism.ale.config.AleConfigs;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.SchematicMetadata;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/**
 * Names for schematics and placements that have none worth showing.
 * <p>
 * Litematica names a schematic saved from an area selection after that selection, and a selection that was never renamed
 * is called "Unnamed"; a placement takes the schematic's name, so most placements of shared files read "Unnamed".
 * Syncmatica servers store shared files under their content hash, and entries loaded again on the server are named after
 * those files. Wherever such a name is shown, this gives a better one instead (the stored name is never changed):
 * the file's own metadata name, else its file name, else the name of a local file with the same content
 * (LocalSchematicIndex), else its region names, else its author and size.
 */
public final class SchematicNames {
    private static final Pattern UUID_NAME = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern HEX_NAME = Pattern.compile("[0-9a-fA-F]{32,64}");

    private record Cached(String raw, Object source, int generation, String name) {
    }

    private static final Map<Object, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private SchematicNames() {
    }

    public static boolean enabled() {
        return AleConfigs.Generic.SCHEMATIC_NAME_FIX.getBooleanValue();
    }

    /** The file name without the .litematic (or other schematic) extension */
    public static String baseName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : new String[]{".litematic", ".schematic", ".schem", ".nbt"}) {
            if (lower.endsWith(ext)) return fileName.substring(0, fileName.length() - ext.length());
        }
        return fileName;
    }

    /** No name worth showing: empty, Litematica's default "Unnamed", "?", or a hash (with or without the file extension) */
    public static boolean isPlaceholder(@Nullable String name) {
        if (name == null) return true;
        String n = baseName(name.trim());
        if (n.isEmpty() || n.equals("?") || n.equalsIgnoreCase("Unnamed")) return true;
        return UUID_NAME.matcher(n).matches() || HEX_NAME.matcher(n).matches();
    }

    /** The hash a hash name stands for (Syncmatica's file names), null if it is no UUID */
    @Nullable
    public static UUID hashName(@Nullable String name) {
        if (name == null) return null;
        String n = baseName(name.trim());
        if (!UUID_NAME.matcher(n).matches()) return null;
        try {
            return UUID.fromString(n);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ Litematica placements

    /** What to show for a placement whose stored name is raw */
    public static String placement(SchematicPlacement placement, String raw) {
        if (!enabled() || !isPlaceholder(raw)) return raw;
        LitematicaSchematic schematic = placement.getSchematic();
        if (schematic == null) return raw;
        int generation = LocalSchematicIndex.generation();
        Cached c = CACHE.get(placement);
        if (c != null && c.raw().equals(raw) && c.source() == schematic && c.generation() == generation) return c.name();
        Path file = placement.getSchematicFile();
        if (file == null) file = schematic.getFile();
        String name = schematic(schematic, file, raw);
        CACHE.put(placement, new Cached(raw, schematic, generation, name));
        return name;
    }

    /** A loaded schematic: its metadata name, file name, a local file with the same content, region names, author and size */
    public static String schematic(LitematicaSchematic schematic, @Nullable Path file, String raw) {
        SchematicMetadata meta = schematic.getMetadata();
        if (!isPlaceholder(meta.getName())) return meta.getName();
        String fileName = file != null && file.getFileName() != null ? baseName(file.getFileName().toString()) : null;
        if (!isPlaceholder(fileName)) return fileName;
        UUID hash = hashName(fileName);
        if (hash != null) {
            LocalSchematicIndex.Info info = LocalSchematicIndex.lookup(hash);
            if (info != null && !isPlaceholder(info.fileName())) return info.fileName();
        }
        String regions = regionNames(schematic.getAreas().keySet());
        if (regions != null) return regions;
        var size = meta.getEnclosingSize();
        String bySize = authorAndSize(meta.getAuthor(), size.getX(), size.getY(), size.getZ());
        return bySize != null ? bySize : raw;
    }

    /** What the local files with this content say (for Syncmatica's entries): null while it is still being looked up */
    @Nullable
    public static String fromLocalCopies(UUID hash) {
        LocalSchematicIndex.Info info = LocalSchematicIndex.lookup(hash);
        if (info == null || info == LocalSchematicIndex.Info.NONE) return null;
        if (!isPlaceholder(info.metaName())) return info.metaName();
        if (!isPlaceholder(info.fileName())) return info.fileName();
        String regions = regionNames(info.regions());
        if (regions != null) return regions;
        return authorAndSize(info.author(), info.sizeX(), info.sizeY(), info.sizeZ());
    }

    /** The region names that are real names (at most three), or null */
    @Nullable
    public static String regionNames(Collection<String> regions) {
        List<String> good = new ArrayList<>();
        for (String r : regions) if (!isPlaceholder(r) && !good.contains(r)) good.add(r);
        if (good.isEmpty()) return null;
        if (good.size() > 3) return String.join(", ", good.subList(0, 3)) + ", ...";
        return String.join(", ", good);
    }

    /** "Unnamed by author, x×y×z" (or only the size when there is no author), null with neither */
    @Nullable
    public static String authorAndSize(@Nullable String author, int x, int y, int z) {
        boolean hasAuthor = author != null && !author.isBlank() && !author.equals("?") && !author.equalsIgnoreCase("unknown");
        boolean hasSize = x > 0 && y > 0 && z > 0;
        if (hasAuthor && hasSize) return StringUtils.translate("autyism-le.name.author_size", author, x, y, z);
        if (hasAuthor) return StringUtils.translate("autyism-le.name.author", author);
        if (hasSize) return StringUtils.translate("autyism-le.name.size", x, y, z);
        return null;
    }

    /** Remembered names are worked out again (for example after the setting changed) */
    public static void clearCache() {
        CACHE.clear();
    }

    /** Keeps a name worked out for any other object (Syncmatica's entries) until its raw name or the index changes */
    public static String cached(Object key, String raw, java.util.function.Supplier<String> compute) {
        int generation = LocalSchematicIndex.generation();
        Cached c = CACHE.get(key);
        if (c != null && c.raw().equals(raw) && c.generation() == generation) return c.name();
        String name = compute.get();
        // (the value must not hold the key: the map only forgets entries whose key nothing else holds)
        CACHE.put(key, new Cached(raw, null, generation, name));
        return name;
    }
}
