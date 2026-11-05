package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.schematic.LocalSchematicIndex;
import com.autyism.ale.schematic.SchematicNames;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Placements and shared schematics without a real name:
 * a) a schematic saved from an unnamed area selection ("Unnamed" in its metadata) shows its file name;
 * b) the same file stored under its Syncmatica hash (sync/&lt;hash&gt;.litematic) shows the name of the local copy with the
 * same content; without any named copy: its region name, or author and size;
 * c) the setting off brings back "Unnamed"; real names are never touched; the stored name stays "Unnamed";
 * d) with Syncmatica installed (-PwithSyncmatica): its entries named after the hash or "Unnamed" show the same names,
 * also in its list's search (the compat mixin is applied).
 */
@SuppressWarnings("UnstableApiUsage")
public final class SchematicNamesGameTest implements FabricClientGameTest {
    private static final BlockPos MIN = new BlockPos(70, 64, 0);
    private static final BlockPos MAX = new BlockPos(72, 64, 0);
    private static final String NAME = "ale_names_farm";
    private static final String LONELY = "ale_names_lonely";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("schnames")) return;
        List<String> failures = new ArrayList<>();
        Path dir = context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());
        Path sync = dir.resolve("sync");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            context.runOnClient(c -> AleConfigs.Generic.SCHEMATIC_NAME_FIX.setBooleanValue(true));
            // what counts as "no name"
            String[][] cases = {{"Unnamed", "true"}, {"unnamed", "true"}, {"", "true"}, {"?", "true"},
                    {"1fdeb0ad-6061-3321-954c-d8e2e3eef4dc", "true"}, {"1fdeb0ad-6061-3321-954c-d8e2e3eef4dc.litematic", "true"},
                    {"d41d8cd98f00b204e9800998ecf8427e", "true"}, {"12万竹子机", "false"}, {"Unnamed farm", "false"}, {"tree", "false"}};
            for (String[] c : cases) {
                if (SchematicNames.isPlaceholder(c[0]) != Boolean.parseBoolean(c[1])) failures.add("placeholder(" + c[0] + ")");
            }
            GT.log("[schnames] placeholder rules checked");

            GT.clearArena(sp, 66, -4, 78, 4, 70);
            sp.getServer().runOnServer(s -> {
                for (int x = MIN.getX(); x <= MAX.getX(); x++) s.overworld().setBlockAndUpdate(new BlockPos(x, 64, 0), Blocks.STONE.defaultBlockState());
            });
            context.waitTicks(5);
            // a schematic file the way Litematica saves one from an area selection nobody renamed: the selection, its box
            // and so the schematic's metadata and region are all "Unnamed"; only the file name the player typed is real
            boolean written = sp.getServer().computeOnServer(server -> {
                fi.dy.masa.litematica.selection.AreaSelection area = new fi.dy.masa.litematica.selection.AreaSelection();
                area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(MIN, MAX, "Unnamed"), false);
                area.setExplicitOrigin(MIN);
                var schematic = LitematicaSchematic.createFromWorld(server.overworld(), area,
                        new LitematicaSchematic.SchematicSaveInfo(false, false), "ALE", m -> GT.log("capture: " + m));
                return schematic != null && "Unnamed".equals(schematic.getMetadata().getName()) && schematic.writeToFile(dir, NAME, true);
            });
            if (!written) throw new AssertionError("[schnames] could not save the unnamed schematic");
            // the same content stored under its Syncmatica hash, like a server's syncmatics folder or a Syncmatica download
            UUID hash = LocalSchematicIndex.hashOf(dir.resolve(NAME + ".litematic"));
            Files.createDirectories(sync);
            Files.copy(dir.resolve(NAME + ".litematic"), sync.resolve(hash + ".litematic"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // a hash file without any named copy: the region is "Unnamed" too
            context.runOnClient(c -> {
                LitematicaSchematic s = LitematicaSchematic.createFromFile(dir, NAME + ".litematic");
                s.getMetadata().setName("Unnamed");
                s.getMetadata().setAuthor("Builder7");
                if (!s.writeToFile(dir, LONELY, true)) throw new AssertionError("could not write " + LONELY);
            });
            UUID lonelyHash = LocalSchematicIndex.hashOf(dir.resolve(LONELY + ".litematic"));
            Files.move(dir.resolve(LONELY + ".litematic"), sync.resolve(lonelyHash + ".litematic"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            context.runOnClient(c -> LocalSchematicIndex.scan());

            // a) "Unnamed" in the metadata: the file name
            String fromFile = placementName(context, dir, NAME + ".litematic");
            GT.log("[schnames] Unnamed schematic file " + NAME + " shows as '" + fromFile + "'");
            if (!NAME.equals(fromFile)) failures.add("file-name:" + fromFile);

            // b) the hash file: the named local copy with the same content (once the index is built)
            waitIndex(context);
            String fromHash = placementName(context, sync, hash + ".litematic");
            GT.log("[schnames] hash file " + hash + " shows as '" + fromHash + "'");
            if (!NAME.equals(fromHash)) failures.add("hash-name:" + fromHash);
            String lonely = placementName(context, sync, lonelyHash + ".litematic");
            GT.log("[schnames] hash file without a named copy shows as '" + lonely + "'");
            String expectLonely = context.computeOnClient(c -> SchematicNames.authorAndSize("Builder7", 3, 1, 1));
            if (!expectLonely.equals(lonely)) failures.add("author-size:" + lonely);
            if (!expectLonely.contains("Builder7") || !expectLonely.contains("3")) failures.add("author-size-text:" + expectLonely);
            String regions = SchematicNames.regionNames(List.of("Unnamed", "Tower", "Base"));
            if (!"Tower, Base".equals(regions)) failures.add("regions:" + regions);

            // c) the stored name stays, and the setting off shows it again; real names are not touched
            String[] stored = context.computeOnClient(c -> {
                SchematicPlacement p = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().getFirst();
                String shown = p.getName();
                String json = p.toJson().get("name").getAsString();
                AleConfigs.Generic.SCHEMATIC_NAME_FIX.setBooleanValue(false);
                String off = p.getName();
                AleConfigs.Generic.SCHEMATIC_NAME_FIX.setBooleanValue(true);
                p.setName("My Farm");
                String real = p.getName();
                return new String[]{shown, json, off, real};
            });
            GT.log("[schnames] shown='" + stored[0] + "' saved='" + stored[1] + "' setting off='" + stored[2] + "' renamed='" + stored[3] + "'");
            if (!"Unnamed".equals(stored[1])) failures.add("saved-name-changed:" + stored[1]);
            if (!"Unnamed".equals(stored[2])) failures.add("off:" + stored[2]);
            if (!"My Farm".equals(stored[3])) failures.add("real-name:" + stored[3]);
            GT.removeAllPlacements(context);

            // the placement list shows the name (screenshot for a look)
            placementName(context, dir, NAME + ".litematic");
            context.runOnClient(c -> c.setScreen(new fi.dy.masa.litematica.gui.GuiSchematicPlacementsList()));
            context.waitTicks(5);
            GT.shot(context, "ale-schnames-placements");
            // the Loaded Schematics list shows the file name too (the placement above loaded the schematic)
            context.runOnClient(c -> {
                fi.dy.masa.litematica.data.SchematicHolder.getInstance().getOrLoad(dir.resolve(NAME + ".litematic"));
                c.setScreen(new fi.dy.masa.litematica.gui.GuiSchematicLoadedList());
            });
            context.waitTicks(5);
            GT.shot(context, "ale-schnames-loaded-list");
            context.runOnClient(c -> c.setScreen(null));

            // d) Syncmatica's entries
            if (FabricLoader.getInstance().isModLoaded("syncmatica")) {
                // (the first time the local copies are still being read; the names settle once they are)
                context.computeOnClient(c -> SyncmaticaCheck.run(hash, lonelyHash));
                context.waitTicks(40);
                String[] sm = context.computeOnClient(c -> SyncmaticaCheck.run(hash, lonelyHash));
                GT.log("[schnames] Syncmatica: hash entry='" + sm[0] + "' unnamed entry='" + sm[1] + "' lonely='" + sm[2] + "' search=" + sm[3]);
                if (!NAME.equals(sm[0])) failures.add("syncmatica-hash:" + sm[0]);
                if (!NAME.equals(sm[1])) failures.add("syncmatica-unnamed:" + sm[1]);
                if (!expectLonely.equals(sm[2])) failures.add("syncmatica-lonely:" + sm[2]);
                if (!sm[3].contains(NAME)) failures.add("syncmatica-search:" + sm[3]);
            } else {
                GT.log("[schnames] Syncmatica not loaded: its part skipped (run with -PwithSyncmatica)");
            }
        } catch (java.io.IOException e) {
            throw new AssertionError("[schnames] " + e, e);
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> c.setScreen(null));
        }
        if (!failures.isEmpty()) throw new AssertionError("[schnames] " + failures);
        GT.log("[schnames] OK");
    }

    /** Loads the file as a new placement (named after the schematic's metadata, like Litematica's Load button) and returns the name it shows */
    private static String placementName(ClientGameTestContext context, Path dir, String file) {
        return context.computeOnClient(c -> {
            LitematicaSchematic s = LitematicaSchematic.createFromFile(dir, file);
            if (s == null) throw new AssertionError("could not read " + file);
            SchematicPlacement p = SchematicPlacement.createFor(s, MIN, s.getMetadata().getName(), true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
            return p.getName();
        });
    }

    private static void waitIndex(ClientGameTestContext context) {
        for (int i = 0; i < 1200; i++) {
            if (LocalSchematicIndex.ready()) return;
            context.waitTick();
        }
        throw new AssertionError("[schnames] the local schematic index was not built within 1200 ticks");
    }

    /** Only touches Syncmatica's classes when it is loaded */
    private static final class SyncmaticaCheck {
        static String[] run(UUID hash, UUID lonelyHash) {
            var owner = ch.endte.syncmatica.extended_core.PlayerIdentifier.MISSING_PLAYER;
            // loaded again on the server: named after the stored hash file
            var byHash = new ch.endte.syncmatica.data.ServerPlacement(UUID.randomUUID(), hash + ".litematic", hash.toString(), hash, owner);
            // shared from an unnamed selection
            var unnamed = new ch.endte.syncmatica.data.ServerPlacement(UUID.randomUUID(), hash + ".litematic", "Unnamed", hash, owner);
            var lonely = new ch.endte.syncmatica.data.ServerPlacement(UUID.randomUUID(), lonelyHash + ".litematic", "Unnamed", lonelyHash, owner);
            // Syncmatica's list search goes through the compat mixin
            String search;
            try {
                Class<?> list = Class.forName("ch.endte.syncmatica.litematica.gui.WidgetListSyncmaticaServerPlacement");
                java.lang.reflect.Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
                f.setAccessible(true);
                Object unsafe = f.get(null);
                Object widget = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, list);
                java.lang.reflect.Method m = list.getDeclaredMethod("getEntryStringsForFilter", ch.endte.syncmatica.data.ServerPlacement.class);
                m.setAccessible(true);
                search = String.valueOf(m.invoke(widget, byHash));
            } catch (ReflectiveOperationException e) {
                search = "error " + e;
            }
            return new String[]{com.autyism.ale.compat.SyncmaticaNames.display(byHash, byHash.getName()),
                    com.autyism.ale.compat.SyncmaticaNames.display(unnamed, unnamed.getName()),
                    com.autyism.ale.compat.SyncmaticaNames.display(lonely, lonely.getName()), search};
        }
    }
}
