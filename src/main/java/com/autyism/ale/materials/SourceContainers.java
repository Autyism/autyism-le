package com.autyism.ale.materials;

import com.autyism.ale.AleMod;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.verifier.ContainerVerifier;
import com.autyism.ale.verifier.OpenedContainers;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.malilib.util.ItemType;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Containers chosen as a material source: their items count as available in Litematica's material lists (the list
 * screen and the info HUD), next to the player's inventory, so "missing" accounts for them.
 * <p>
 * Looking at a container and pressing the hotkey adds it; pressing it again on a chosen container removes it. The list is
 * kept per world / server (config/autyism-le/material_sources.json) until the player removes the containers.
 * <p>
 * Contents are known without opening the container in single player (straight from the world) and on servers with Servux
 * (Litematica's entity data sync), the same way ALE's container check gets them; anywhere, opening a chosen container
 * reads what is in it. The last contents seen are kept with the list, so a container stays counted after leaving its
 * area or the game. Shulker boxes and bundles inside are counted with what they hold (Litematica's own counting).
 * A container whose contents were never seen is not counted and shows as unknown.
 * <p>
 * Everything runs on the client thread.
 */
public final class SourceContainers {
    private static final Path FILE = Path.of("config", AleMod.MOD_ID, "material_sources.json");
    /** Each chosen container is asked for its contents again after this many ticks */
    private static final int REFRESH_TICKS = 100;
    /** While waiting for an answer: ask again after this many ticks */
    private static final int RETRY_TICKS = 10;
    private static final int REQUESTS_PER_TICK = 4;
    /** Unsaved changes are written after this many ticks */
    private static final int SAVE_DELAY_TICKS = 200;
    /** While a chosen container is open, its slots are read again every this many ticks */
    private static final int OPEN_READ_TICKS = 5;

    /** What is known about one container block (one half of a double chest) */
    public static final class Half {
        @Nullable
        List<ItemStack> items;
        /** When the contents were last seen (System.currentTimeMillis), 0 = never */
        long seen;
        /** The block is no longer a container */
        boolean gone;

        public boolean known() {
            return items != null && !gone;
        }

        @Nullable
        public List<ItemStack> items() {
            return items;
        }

        public long seen() {
            return seen;
        }
    }

    /** A chosen container: the position the player chose and the blocks it consists of (both halves of a double chest) */
    public static final class Spot {
        final String dim;
        final BlockPos pos;
        final Map<BlockPos, Half> halves = new LinkedHashMap<>();
        long nextCheck;

        Spot(String dim, BlockPos pos) {
            this.dim = dim;
            this.pos = pos.immutable();
            this.halves.put(this.pos, new Half());
        }

        public String dim() {
            return dim;
        }

        public BlockPos pos() {
            return pos;
        }

        public Map<BlockPos, Half> halves() {
            return halves;
        }

        public boolean known() {
            for (Half h : halves.values()) if (!h.known() && !h.gone) return false;
            return halves.values().stream().anyMatch(Half::known);
        }

        public boolean gone() {
            return halves.values().stream().allMatch(h -> h.gone);
        }

        /** Items in it (shulker boxes and bundles counted with what they hold, like Litematica counts the inventory) */
        public int itemCount() {
            int n = 0;
            for (Half h : halves.values()) {
                if (!h.known()) continue;
                for (int c : MaterialListUtils.getInventoryItemCounts(container(h.items)).values()) n += c;
            }
            return n;
        }

        public long lastSeen() {
            long t = 0;
            for (Half h : halves.values()) t = Math.max(t, h.seen);
            return t;
        }
    }

    private static final List<Spot> SPOTS = new ArrayList<>();
    @Nullable
    private static JsonObject root;
    @Nullable
    private static String worldKey;
    @Nullable
    private static Level level;
    private static long ticks;
    private static long dirtySince = -1;
    /** Goes up when the list or any contents change (cached counts are worked out again) */
    private static int version;
    private static int countsVersion = -1;
    private static Object2IntOpenHashMap<ItemType> counts = new Object2IntOpenHashMap<>();
    /** The registries of the world the list belongs to (items are written with them, also after leaving the world) */
    @Nullable
    private static net.minecraft.core.RegistryAccess registries;
    @Nullable
    private static AbstractContainerMenu openMenu;
    private static long openReadTick;

    private SourceContainers() {
    }

    public static boolean enabled() {
        return AleConfigs.Generic.MATERIAL_SOURCE_CONTAINERS.getBooleanValue();
    }

    public static List<Spot> spots() {
        return SPOTS;
    }

    public static int version() {
        return version;
    }

    // ------------------------------------------------------------------ hotkey

    /** The hotkey: adds the container the player looks at, or removes it when it is already chosen */
    public static boolean toggleLookedAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (!enabled()) {
            message(mc.player, Component.translatable("autyism-le.message.material_source.off"));
            return true;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockEntity(bhr.getBlockPos()) instanceof Container)) {
            message(mc.player, Component.translatable("autyism-le.message.material_source.not_container"));
            return true;
        }
        BlockPos pos = bhr.getBlockPos().immutable();
        Component block = mc.level.getBlockState(pos).getBlock().getName();
        Spot existing = find(dim(mc.level), pos);
        if (existing != null) {
            remove(existing);
            message(mc.player, Component.translatable("autyism-le.message.material_source.removed", block, pos.getX(), pos.getY(), pos.getZ(), SPOTS.size()));
        } else {
            add(dim(mc.level), pos);
            message(mc.player, Component.translatable("autyism-le.message.material_source.added", block, pos.getX(), pos.getY(), pos.getZ(), SPOTS.size()));
        }
        return true;
    }

    /** Adds a container (also used by tests); does nothing if it, or the other half of its double chest, is chosen already */
    public static Spot add(String dim, BlockPos pos) {
        Spot existing = find(dim, pos);
        if (existing != null) return existing;
        Spot spot = new Spot(dim, pos);
        if (level != null && dim.equals(dim(level))) updateHalves(spot, level);
        SPOTS.add(spot);
        changed();
        return spot;
    }

    public static void remove(Spot spot) {
        if (SPOTS.remove(spot)) changed();
    }

    public static void clear() {
        if (SPOTS.isEmpty()) return;
        SPOTS.clear();
        changed();
    }

    /** The chosen container that this block belongs to (either half of a double chest) */
    @Nullable
    public static Spot find(String dim, BlockPos pos) {
        for (Spot s : SPOTS) if (s.dim.equals(dim) && s.halves.containsKey(pos)) return s;
        // the other half of a chosen double chest
        if (level != null && dim.equals(dim(level))) {
            BlockPos other = otherHalf(level, pos);
            if (other != null) for (Spot s : SPOTS) if (s.dim.equals(dim) && s.halves.containsKey(other)) return s;
        }
        return null;
    }

    /** Asks every chosen container in this dimension for its contents again right away */
    public static void refreshNow() {
        for (Spot s : SPOTS) s.nextCheck = 0;
    }

    // ------------------------------------------------------------------ counting

    /** Adds the items in the chosen containers to the entries' available counts (after Litematica counted the inventory) */
    public static void addAvailable(@Nullable List<MaterialListEntry> list) {
        if (list == null || list.isEmpty() || !enabled() || SPOTS.isEmpty()) return;
        Object2IntOpenHashMap<ItemType> c = counts();
        if (c.isEmpty()) return;
        for (MaterialListEntry entry : list) {
            int n = c.getInt(new ItemType(entry.getStack(), true, false));
            if (n > 0) entry.setCountAvailable(entry.getCountAvailable() + n);
        }
    }

    /** All items in the known chosen containers */
    public static Object2IntOpenHashMap<ItemType> counts() {
        if (countsVersion == version) return counts;
        Object2IntOpenHashMap<ItemType> out = new Object2IntOpenHashMap<>();
        for (Spot s : SPOTS) {
            for (Half h : s.halves.values()) {
                if (!h.known()) continue;
                MaterialListUtils.getInventoryItemCounts(container(h.items)).forEach(out::addTo);
            }
        }
        counts = out;
        countsVersion = version;
        return out;
    }

    /** How many chosen containers have no known contents yet */
    public static int unknownCount() {
        int n = 0;
        for (Spot s : SPOTS) if (!s.known() && !s.gone()) n++;
        return n;
    }

    private static Container container(List<ItemStack> items) {
        SimpleContainer c = new SimpleContainer(Math.max(1, items.size()));
        for (int i = 0; i < items.size(); i++) c.setItem(i, items.get(i));
        return c;
    }

    // ------------------------------------------------------------------ tick

    public static void tick(Minecraft mc) {
        ticks++;
        if (mc.level != level) {
            flush();
            level = mc.level;
            load(mc);
        }
        if (mc.level == null || mc.player == null) return;
        if (dirtySince >= 0 && ticks - dirtySince >= SAVE_DELAY_TICKS) flush();
        if (!enabled() || SPOTS.isEmpty()) return;
        readOpened(mc);
        String dim = dim(mc.level);
        int budget = REQUESTS_PER_TICK;
        for (Spot spot : SPOTS) {
            if (budget <= 0) break;
            if (!spot.dim.equals(dim) || spot.nextCheck > ticks) continue;
            if (!mc.level.hasChunkAt(spot.pos)) continue;
            updateHalves(spot, mc.level);
            boolean waiting = false;
            for (var e : spot.halves.entrySet()) {
                BlockPos p = e.getKey();
                Half half = e.getValue();
                if (!mc.level.hasChunkAt(p)) continue;
                if (!(mc.level.getBlockEntity(p) instanceof Container)) {
                    if (!half.gone) {
                        half.gone = true;
                        changed();
                    }
                    continue;
                }
                budget--;
                List<ItemStack> items = ContainerVerifier.actualContents(mc.level, p, false);
                if (items == null) {
                    waiting = true;
                    continue;
                }
                store(half, items);
            }
            spot.nextCheck = ticks + (waiting ? RETRY_TICKS : REFRESH_TICKS);
        }
    }

    /** A chosen container is open: what its screen shows is what is in it */
    private static void readOpened(Minecraft mc) {
        BlockPos open = OpenedContainers.currentPos();
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (open == null || menu == null || menu == mc.player.inventoryMenu) {
            openMenu = null;
            return;
        }
        if (menu == openMenu && ticks - openReadTick < OPEN_READ_TICKS) return;
        openMenu = menu;
        openReadTick = ticks;
        Spot spot = find(dim(mc.level), open);
        if (spot == null) return;
        Container block = null;
        List<ItemStack> slots = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            if (block == null) block = slot.container;
            if (slot.container != block) continue;
            int index = slot.getContainerSlot();
            while (slots.size() <= index) slots.add(ItemStack.EMPTY);
            slots.set(index, slot.getItem().isEmpty() ? ItemStack.EMPTY : slot.getItem().copy());
        }
        if (block == null) return;
        updateHalves(spot, mc.level);
        BlockState state = mc.level.getBlockState(open);
        BlockPos other = otherHalf(mc.level, open);
        if (other != null && slots.size() == 54) {
            // a double chest's screen shows the RIGHT half first (like vanilla puts them together)
            boolean openFirst = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
            store(spot.halves.computeIfAbsent(openFirst ? open : other, p -> new Half()), slots.subList(0, 27));
            store(spot.halves.computeIfAbsent(openFirst ? other : open, p -> new Half()), slots.subList(27, 54));
        } else {
            store(spot.halves.computeIfAbsent(open, p -> new Half()), slots);
        }
    }

    private static void store(Half half, List<ItemStack> slots) {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : slots) {
            // (the crafter's disabled slots come as a placeholder item)
            if (s != null && !s.isEmpty() && s != ContainerVerifier.disabledSlot()) items.add(s.copy());
        }
        half.seen = System.currentTimeMillis();
        if (half.items != null && !half.gone && same(half.items, items)) return;
        half.items = items;
        half.gone = false;
        changed();
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        return true;
    }

    /** Both halves of a double chest belong to the spot; a chest that is single now keeps only the chosen block */
    private static void updateHalves(Spot spot, Level world) {
        if (!world.hasChunkAt(spot.pos)) return;
        BlockPos other = otherHalf(world, spot.pos);
        boolean changed = spot.halves.keySet().removeIf(p -> !p.equals(spot.pos) && !p.equals(other));
        if (other != null && !spot.halves.containsKey(other)) {
            spot.halves.put(other, new Half());
            changed = true;
        }
        if (changed) changed();
    }

    /** The other half of the double chest at pos (same check as vanilla's), null for anything else */
    @Nullable
    public static BlockPos otherHalf(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        BlockState o = world.getBlockState(other);
        if (!o.is(state.getBlock())) return null;
        ChestType t = o.getValue(ChestBlock.TYPE);
        if (t == ChestType.SINGLE || t == state.getValue(ChestBlock.TYPE)) return null;
        return o.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING) ? other.immutable() : null;
    }

    public static String dim(Level world) {
        //? if >=1.21.11 {
        return world.dimension().identifier().toString();
        //?} else
        //return world.dimension().location().toString();
    }

    private static void changed() {
        version++;
        if (dirtySince < 0) dirtySince = ticks;
    }

    private static void message(LocalPlayer player, Component text) {
        //? if >=26.1 {
        /*player.sendOverlayMessage(text);
        *///?} else
        player.displayClientMessage(text, true);
    }

    // ------------------------------------------------------------------ saving

    /** The world or server the list belongs to (the same name Litematica uses for its per-world files) */
    @Nullable
    private static String currentWorldKey() {
        return fi.dy.masa.malilib.util.StringUtils.getWorldOrServerName();
    }

    private static void load(Minecraft mc) {
        SPOTS.clear();
        openMenu = null;
        changed();
        dirtySince = -1;
        worldKey = mc.level != null ? currentWorldKey() : null;
        registries = mc.level != null ? mc.level.registryAccess() : null;
        if (worldKey == null) return;
        JsonObject all = root();
        JsonObject worlds = all.has("worlds") && all.get("worlds").isJsonObject() ? all.getAsJsonObject("worlds") : null;
        if (worlds == null || !worlds.has(worldKey) || !worlds.get(worldKey).isJsonArray()) return;
        DynamicOps<JsonElement> ops = ops();
        for (JsonElement e : worlds.getAsJsonArray(worldKey)) {
            try {
                JsonObject o = e.getAsJsonObject();
                Spot spot = new Spot(o.get("dim").getAsString(), new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
                spot.halves.clear();
                for (JsonElement he : o.getAsJsonArray("halves")) {
                    JsonObject ho = he.getAsJsonObject();
                    Half half = new Half();
                    half.seen = ho.has("seen") ? ho.get("seen").getAsLong() : 0;
                    half.gone = ho.has("gone") && ho.get("gone").getAsBoolean();
                    if (ho.has("items") && ops != null) {
                        List<ItemStack> items = new ArrayList<>();
                        for (JsonElement ie : ho.getAsJsonArray("items")) {
                            ItemStack.CODEC.parse(ops, ie).result().ifPresent(items::add);
                        }
                        half.items = items;
                    }
                    spot.halves.put(new BlockPos(ho.get("x").getAsInt(), ho.get("y").getAsInt(), ho.get("z").getAsInt()), half);
                }
                spot.halves.putIfAbsent(spot.pos, new Half());
                SPOTS.add(spot);
            } catch (Exception ex) {
                AleMod.LOGGER.warn("Skipped a broken material source container entry: {}", ex.toString());
            }
        }
        dirtySince = -1;
    }

    /** Writes unsaved changes now */
    public static void flush() {
        if (dirtySince < 0 || worldKey == null) {
            dirtySince = -1;
            return;
        }
        dirtySince = -1;
        DynamicOps<JsonElement> ops = ops();
        JsonArray list = new JsonArray();
        for (Spot s : SPOTS) {
            JsonObject o = new JsonObject();
            o.addProperty("dim", s.dim);
            o.addProperty("x", s.pos.getX());
            o.addProperty("y", s.pos.getY());
            o.addProperty("z", s.pos.getZ());
            JsonArray halves = new JsonArray();
            for (var e : s.halves.entrySet()) {
                JsonObject ho = new JsonObject();
                ho.addProperty("x", e.getKey().getX());
                ho.addProperty("y", e.getKey().getY());
                ho.addProperty("z", e.getKey().getZ());
                Half h = e.getValue();
                if (h.seen > 0) ho.addProperty("seen", h.seen);
                if (h.gone) ho.addProperty("gone", true);
                if (h.items != null && ops != null) {
                    JsonArray items = new JsonArray();
                    for (ItemStack stack : h.items) ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(items::add);
                    ho.add("items", items);
                }
                halves.add(ho);
            }
            o.add("halves", halves);
            list.add(o);
        }
        JsonObject all = root();
        if (!all.has("worlds") || !all.get("worlds").isJsonObject()) all.add("worlds", new JsonObject());
        JsonObject worlds = all.getAsJsonObject("worlds");
        if (list.isEmpty()) worlds.remove(worldKey);
        else worlds.add(worldKey, list);
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(all, w);
            }
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            AleMod.LOGGER.warn("Could not save {}: {}", FILE, e.toString());
        }
    }

    private static JsonObject root() {
        if (root != null) return root;
        root = new JsonObject();
        if (Files.isRegularFile(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                JsonElement e = JsonParser.parseReader(r);
                if (e.isJsonObject()) root = e.getAsJsonObject();
            } catch (Exception e) {
                AleMod.LOGGER.warn("Could not read {}: {}", FILE, e.toString());
            }
        }
        return root;
    }

    @Nullable
    private static DynamicOps<JsonElement> ops() {
        if (registries == null) return null;
        return registries.createSerializationContext(JsonOps.INSTANCE);
    }
}
