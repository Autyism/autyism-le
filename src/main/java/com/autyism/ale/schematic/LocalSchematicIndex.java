package com.autyism.ale.schematic;

import com.autyism.ale.AleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * The schematic files on this computer, indexed by Syncmatica's content hash (an MD5 of the file, turned into a name UUID).
 * <p>
 * Syncmatica servers keep shared files as syncmatics/&lt;hash&gt;.litematic, and after a server-side reload the shared
 * entries are named after those files. The same file is often still in the player's own schematics folder under its real
 * name: this index finds it by content, so a hash can be shown as that name.
 * <p>
 * Built in the background, only when a hash name actually needs a better name. Hashes of unchanged files (same size and
 * modification time) are kept in config/autyism-le/schematic_hashes.json, so a later scan only reads new or changed files.
 * Everything here is safe to call from any thread.
 */
public final class LocalSchematicIndex {
    private static final Path CACHE = Path.of("config", AleMod.MOD_ID, "schematic_hashes.json");
    /** A hash that is not in the index starts a new scan only this long after the last one (milliseconds) */
    private static final long RESCAN_AFTER_MS = 60_000;

    /** What the local copies of one shared file say about it */
    public record Info(@Nullable String fileName, @Nullable String metaName, @Nullable String author,
                       int sizeX, int sizeY, int sizeZ, List<String> regions) {
        static final Info NONE = new Info(null, null, null, 0, 0, 0, List.of());
    }

    private record Cached(long size, long modified, String hash) {
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ALE schematic name index");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private static final AtomicBoolean SCANNING = new AtomicBoolean();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static volatile Map<UUID, List<Path>> byHash;
    private static volatile long lastScanEnd;
    private static final Map<UUID, Info> INFOS = new ConcurrentHashMap<>();
    private static final Set<UUID> READING = ConcurrentHashMap.newKeySet();

    private LocalSchematicIndex() {
    }

    /** Goes up whenever the index learns something new (cached display names are worked out again) */
    public static int generation() {
        return GENERATION.get();
    }

    /** True once a scan has finished (tests wait for it) */
    public static boolean ready() {
        return byHash != null && !SCANNING.get();
    }

    /**
     * What the local files with this hash say: null while the index is still being built or the file is being read
     * (it is started here), {@code Info.NONE} if there is no local file with this content
     */
    @Nullable
    public static Info lookup(UUID hash) {
        Map<UUID, List<Path>> index = byHash;
        if (index == null) {
            scan();
            return null;
        }
        Info info = INFOS.get(hash);
        if (info != null) return info;
        List<Path> paths = index.get(hash);
        if (paths == null) {
            if (System.currentTimeMillis() - lastScanEnd > RESCAN_AFTER_MS) scan();
            return SCANNING.get() ? null : Info.NONE;
        }
        if (READING.add(hash)) WORKER.execute(() -> {
            try {
                INFOS.put(hash, read(paths));
                GENERATION.incrementAndGet();
            } finally {
                READING.remove(hash);
            }
        });
        return null;
    }

    /**
     * The real name (not a hash) of a local file with this content, right away from the index (no file is read): null
     * while the index is being built (it is started here) or when there is no such file
     */
    @Nullable
    public static String fileName(UUID hash) {
        Map<UUID, List<Path>> index = byHash;
        if (index == null) {
            scan();
            return null;
        }
        List<Path> paths = index.get(hash);
        if (paths == null) {
            if (System.currentTimeMillis() - lastScanEnd > RESCAN_AFTER_MS) scan();
            return null;
        }
        for (Path p : paths) {
            String base = SchematicNames.baseName(p.getFileName().toString());
            if (!SchematicNames.isPlaceholder(base)) return base;
        }
        return null;
    }

    /** Starts a scan in the background (nothing happens if one is running) */
    public static void scan() {
        if (!SCANNING.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try {
                Map<UUID, List<Path>> index = build();
                byHash = index;
                // what was read about files that may have changed is worked out again
                INFOS.clear();
            } catch (Throwable e) {
                AleMod.LOGGER.warn("Could not index the local schematic files", e);
                if (byHash == null) byHash = Map.of();
            } finally {
                lastScanEnd = System.currentTimeMillis();
                SCANNING.set(false);
                GENERATION.incrementAndGet();
            }
        });
    }

    /** Syncmatica's hash of a file: the MD5 of its bytes, made into a name-based UUID */
    public static UUID hashOf(Path file) throws java.io.IOException {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            for (int n; (n = in.read(buffer)) > 0; ) md5.update(buffer, 0, n);
        }
        return UUID.nameUUIDFromBytes(md5.digest());
    }

    private static List<Path> roots() {
        Set<Path> roots = new LinkedHashSet<>();
        try {
            roots.add(fi.dy.masa.litematica.data.DataManager.getSchematicsBaseDirectory());
        } catch (Throwable ignored) {
        }
        // Syncmatica keeps its downloads in the game folder's schematics/sync, which is not Litematica's folder when that one is moved
        roots.add(FabricLoader.getInstance().getGameDir().resolve("schematics"));
        List<Path> out = new ArrayList<>();
        Set<Path> real = new java.util.HashSet<>();
        for (Path r : roots) {
            try {
                if (!Files.isDirectory(r)) continue;
                if (real.add(r.toRealPath())) out.add(r);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static Map<UUID, List<Path>> build() throws Exception {
        Map<String, Cached> cache = readCache();
        Map<String, Cached> seen = new HashMap<>();
        Map<UUID, List<Path>> index = new HashMap<>();
        for (Path root : roots()) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic"))
                        .filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                try {
                    String key = file.toAbsolutePath().normalize().toString();
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    long size = attrs.size(), modified = attrs.lastModifiedTime().toMillis();
                    Cached c = cache.get(key);
                    String hash = c != null && c.size() == size && c.modified() == modified ? c.hash() : hashOf(file).toString();
                    seen.put(key, new Cached(size, modified, hash));
                    index.computeIfAbsent(UUID.fromString(hash), h -> new ArrayList<>()).add(file);
                } catch (Exception ignored) {
                    // unreadable file: left out
                }
            }
        }
        writeCache(seen);
        return index;
    }

    /** Reads the metadata of the local copies (only the first readable one) and picks a file name that is not a hash */
    private static Info read(List<Path> paths) {
        String fileName = null;
        for (Path p : paths) {
            String base = SchematicNames.baseName(p.getFileName().toString());
            if (!SchematicNames.isPlaceholder(base)) {
                fileName = base;
                break;
            }
        }
        for (Path p : paths) {
            try {
                CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
                CompoundTag meta = root.getCompoundOrEmpty("Metadata");
                CompoundTag size = meta.getCompoundOrEmpty("EnclosingSize");
                List<String> regions = new ArrayList<>(root.getCompoundOrEmpty("Regions").keySet());
                return new Info(fileName, meta.getStringOr("Name", null), meta.getStringOr("Author", null),
                        Math.abs(size.getIntOr("x", 0)), Math.abs(size.getIntOr("y", 0)), Math.abs(size.getIntOr("z", 0)), regions);
            } catch (Exception ignored) {
                // not readable: try the next copy
            }
        }
        return new Info(fileName, null, null, 0, 0, 0, List.of());
    }

    private static Map<String, Cached> readCache() {
        Map<String, Cached> out = new HashMap<>();
        if (!Files.isRegularFile(CACHE)) return out;
        try (Reader r = Files.newBufferedReader(CACHE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (!root.isJsonObject()) return out;
            for (var e : root.getAsJsonObject().entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                out.put(e.getKey(), new Cached(o.get("size").getAsLong(), o.get("modified").getAsLong(), o.get("hash").getAsString()));
            }
        } catch (Exception e) {
            AleMod.LOGGER.warn("Could not read {}: {}", CACHE, e.toString());
        }
        return out;
    }

    private static void writeCache(Map<String, Cached> entries) {
        JsonObject root = new JsonObject();
        for (var e : entries.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("size", e.getValue().size());
            o.addProperty("modified", e.getValue().modified());
            o.addProperty("hash", e.getValue().hash());
            root.add(e.getKey(), o);
        }
        try {
            Files.createDirectories(CACHE.getParent());
            Path tmp = CACHE.resolveSibling(CACHE.getFileName() + ".tmp");
            Gson gson = new GsonBuilder().create();
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(root, w);
            }
            Files.move(tmp, CACHE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AleMod.LOGGER.warn("Could not write {}: {}", CACHE, e.toString());
        }
    }
}
