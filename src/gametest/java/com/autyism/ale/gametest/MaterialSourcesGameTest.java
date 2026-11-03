package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.materials.SourceContainerMarker;
import com.autyism.ale.materials.SourceContainers;
import com.autyism.ale.materials.SourceContainersGui;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chosen material source containers (single player):
 * a) the hotkey on a double chest chooses it (both halves count, a shulker box inside counts with what it holds); the
 * material list's available counts (the list made for a schematic, and the refresh the screen and HUD do) include them;
 * b) a container in another dimension stays unknown and is not counted; the screen's button says so;
 * c) the hotkey on the other half of the chosen double chest removes it again;
 * d) outlines only while a material list is shown (unless "always");
 * e) the setting off counts the inventory only;
 * f) the list with its contents is saved and comes back in the next session of the same world.
 */
@SuppressWarnings("UnstableApiUsage")
public final class MaterialSourcesGameTest implements FabricClientGameTest {
    private static final BlockPos LEFT = new BlockPos(90, 64, 2);
    private static final BlockPos RIGHT = new BlockPos(91, 64, 2);
    private static final BlockPos BARREL = new BlockPos(94, 64, 2);
    private static final BlockPos ROW_MIN = new BlockPos(90, 64, -3);
    private static final BlockPos ROW_MAX = new BlockPos(93, 64, -3);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("matsources")) return;
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            context.runOnClient(c -> {
                SourceContainers.clear();
                AleConfigs.Generic.MATERIAL_SOURCE_CONTAINERS.setBooleanValue(true);
                AleConfigs.Generic.MATERIAL_SOURCE_MARKER_ALWAYS.setBooleanValue(false);
                AleConfigs.Generic.MATERIAL_SOURCE_CONTAINER_KEY.getKeybind().setValueFromString("F15");
            });
            GT.clearArena(sp, 84, -8, 100, 8, 70);
            sp.getServer().runCommand("give @a minecraft:stone 5");
            sp.getServer().runOnServer(s -> {
                var level = s.overworld();
                // a double chest facing south: stone in one half, a shulker box with iron in the other
                level.setBlockAndUpdate(LEFT, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
                level.setBlockAndUpdate(RIGHT, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
                if (level.getBlockEntity(LEFT) instanceof Container c) c.setItem(3, new ItemStack(Items.STONE, 64));
                if (level.getBlockEntity(RIGHT) instanceof Container c) {
                    ItemStack shulker = new ItemStack(Items.WHITE_SHULKER_BOX);
                    NonNullList<ItemStack> inside = NonNullList.withSize(27, ItemStack.EMPTY);
                    inside.set(0, new ItemStack(Items.IRON_INGOT, 10));
                    shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
                    c.setItem(0, shulker);
                }
                level.setBlockAndUpdate(BARREL, Blocks.BARREL.defaultBlockState());
                if (level.getBlockEntity(BARREL) instanceof Container c) c.setItem(0, new ItemStack(Items.APPLE, 20));
                for (int x = ROW_MIN.getX(); x <= ROW_MAX.getX(); x++) level.setBlockAndUpdate(new BlockPos(x, 64, -3), Blocks.STONE.defaultBlockState());
            });
            context.waitTicks(10);
            boolean otherHalf = context.computeOnClient(c -> RIGHT.equals(SourceContainers.otherHalf(c.level, LEFT)));
            if (!otherHalf) failures.add("not-a-double-chest");

            Map<Item, Integer> before = available(context);
            GT.log("[matsources] available before choosing: " + before);
            if (before.get(Items.STONE) != 5 || before.get(Items.IRON_INGOT) != 0) failures.add("before:" + before);

            // a) look at one half and press the hotkey
            look(context, sp, LEFT);
            pressHotkey(context);
            int spots = context.computeOnClient(c -> SourceContainers.spots().size());
            if (spots != 1) failures.add("hotkey-add:" + spots);
            boolean known = waitFor(context, () -> context.computeOnClient(c -> !SourceContainers.spots().isEmpty() && SourceContainers.spots().getFirst().known()), 100);
            if (!known) failures.add("never-known");
            Map<Item, Integer> after = available(context);
            GT.log("[matsources] chose the double chest: known=" + known + " available " + after);
            if (after.get(Items.STONE) != 69) failures.add("stone:" + after.get(Items.STONE));
            if (after.get(Items.IRON_INGOT) != 10) failures.add("iron-in-shulker:" + after.get(Items.IRON_INGOT));
            if (after.get(Items.APPLE) != 0) failures.add("apple-not-chosen:" + after.get(Items.APPLE));

            // the material list of a schematic (4 stone) counts them when it is made
            SchematicPlacement placement = GT.captureAndPlace(context, sp, ROW_MIN, ROW_MAX, ROW_MIN, "ale_matsources");
            int[] stoneEntry = context.computeOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                for (MaterialListEntry e : list.getMaterialsAll()) {
                    if (e.getStack().is(Items.STONE)) return new int[]{e.getCountTotal(), e.getCountAvailable()};
                }
                return new int[]{-1, -1};
            });
            GT.log("[matsources] schematic list: stone total=" + stoneEntry[0] + " available=" + stoneEntry[1]);
            if (stoneEntry[0] != 4 || stoneEntry[1] != 69) failures.add("list-made:" + stoneEntry[0] + "/" + stoneEntry[1]);

            // b) a container in another dimension: unknown, not counted, the button says so
            context.runOnClient(c -> SourceContainers.add("minecraft:the_nether", new BlockPos(1, 70, 1)));
            int unknown = context.computeOnClient(c -> SourceContainers.unknownCount());
            Map<Item, Integer> withUnknown = available(context);
            if (unknown != 1 || withUnknown.get(Items.STONE) != 69) failures.add("unknown:" + unknown + " " + withUnknown);

            // the material list screen: its button, the outlines while it is open
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(10);
            String label = context.computeOnClient(c -> c.screen instanceof GuiMaterialList && GT.findButton(c, SourceContainersGui.label()) != null
                    ? SourceContainersGui.label() : "none");
            List<List<BlockPos>> outlined = context.computeOnClient(c -> SourceContainerMarker.positions());
            GT.log("[matsources] screen button '" + label + "', outlined " + outlined);
            if (!label.contains("2") || !label.contains("1")) failures.add("button:" + label);
            if (outlined.isEmpty() || !outlined.get(0).contains(LEFT) || !outlined.get(0).contains(RIGHT)) failures.add("outline-open:" + outlined);
            GT.shot(context, "ale-matsources-list");
            context.runOnClient(c -> {
                c.setScreen(null);
                DataManager.setMaterialList(null);
            });
            context.waitTicks(2);
            // d) closed: no outlines unless "always"
            int closed = context.computeOnClient(c -> SourceContainerMarker.positions().size());
            context.runOnClient(c -> AleConfigs.Generic.MATERIAL_SOURCE_MARKER_ALWAYS.setBooleanValue(true));
            int always = context.computeOnClient(c -> SourceContainerMarker.positions().isEmpty() ? 0 : SourceContainerMarker.positions().get(0).size());
            context.runOnClient(c -> AleConfigs.Generic.MATERIAL_SOURCE_MARKER_ALWAYS.setBooleanValue(false));
            if (closed != 0 || always != 2) failures.add("outline-closed:" + closed + " always:" + always);
            context.waitTicks(5);
            GT.shot(context, "ale-matsources-world");

            // e) the setting off: inventory only
            context.runOnClient(c -> AleConfigs.Generic.MATERIAL_SOURCE_CONTAINERS.setBooleanValue(false));
            Map<Item, Integer> off = available(context);
            context.runOnClient(c -> AleConfigs.Generic.MATERIAL_SOURCE_CONTAINERS.setBooleanValue(true));
            if (off.get(Items.STONE) != 5) failures.add("off:" + off);

            // c) the hotkey on the other half removes the chest
            look(context, sp, RIGHT);
            pressHotkey(context);
            boolean removed = context.computeOnClient(c -> SourceContainers.find(SourceContainers.dim(c.level), LEFT) == null);
            Map<Item, Integer> afterRemove = available(context);
            GT.log("[matsources] hotkey on the other half: removed=" + removed + " available " + afterRemove);
            if (!removed || afterRemove.get(Items.STONE) != 5) failures.add("remove:" + removed + " " + afterRemove);

            // f) choose the barrel and the chest again, then the next session of this world remembers them with their contents
            look(context, sp, BARREL);
            pressHotkey(context);
            look(context, sp, LEFT);
            pressHotkey(context);
            waitFor(context, () -> context.computeOnClient(c -> SourceContainers.spots().stream().filter(s -> !s.dim().contains("nether")).allMatch(SourceContainers.Spot::known)), 100);
            context.runOnClient(c -> SourceContainers.flush());
            Path file = Path.of("config", "autyism-le", "material_sources.json");
            String saved = Files.isRegularFile(file) ? Files.readString(file) : "";
            if (!saved.contains("minecraft:apple") || !saved.contains("\"x\": 94")) failures.add("saved-file");
            GT.removeAllPlacements(context);
        } catch (java.io.IOException e) {
            throw new AssertionError("[matsources] " + e, e);
        } finally {
            context.runOnClient(c -> c.setScreen(null));
        }
        // the same world name again (gametest worlds all have the same name): the list is read back with its contents
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            // read back when the world was entered, with the contents last seen
            int[] back = context.computeOnClient(c -> {
                int apples = 0;
                for (SourceContainers.Spot s : SourceContainers.spots()) {
                    var half = s.halves().get(BARREL);
                    if (half != null && half.items() != null) for (ItemStack st : half.items()) if (st.is(Items.APPLE)) apples += st.getCount();
                }
                return new int[]{SourceContainers.spots().size(), apples};
            });
            // this new world has no containers there: once checked they are "gone" and no longer counted
            boolean gone = waitFor(context, () -> context.computeOnClient(c -> SourceContainers.spots().stream()
                    .filter(s -> !s.dim().contains("nether")).allMatch(SourceContainers.Spot::gone)), 100);
            int countedApples = context.computeOnClient(c -> {
                int n = 0;
                for (var e : SourceContainers.counts().object2IntEntrySet()) if (e.getKey().getStack().is(Items.APPLE)) n += e.getIntValue();
                return n;
            });
            GT.log("[matsources] next session: " + back[0] + " chosen, barrel remembered with " + back[1] + " apples; gone here=" + gone + ", apples counted=" + countedApples);
            if (back[0] != 3 || back[1] != 20) failures.add("reload:" + back[0] + "/" + back[1]);
            if (!gone || countedApples != 0) failures.add("gone:" + gone + "/" + countedApples);
            context.runOnClient(c -> SourceContainers.clear());
        }
        if (!failures.isEmpty()) throw new AssertionError("[matsources] " + failures);
        GT.log("[matsources] OK");
    }

    /** Litematica's own refresh of the available counts for a stone / iron / apple list (what the screen and the HUD do) */
    private static Map<Item, Integer> available(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            List<MaterialListEntry> list = new ArrayList<>();
            for (Item item : List.of(Items.STONE, Items.IRON_INGOT, Items.APPLE)) list.add(new MaterialListEntry(new ItemStack(item), 100, 100, 0, 0));
            MaterialListUtils.updateAvailableCounts(list, c.player);
            Map<Item, Integer> m = new HashMap<>();
            for (MaterialListEntry e : list) m.put(e.getStack().getItem(), e.getCountAvailable());
            return m;
        });
    }

    /** Stands south of the containers and looks at the given block */
    private static void look(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos target) {
        sp.getServer().runCommand("tp @a " + (target.getX() + 0.5) + " 64 " + (target.getZ() + 3.5) + " 180 30");
        context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - (target.getX() + 0.5)) < 0.01, 200);
        context.waitTicks(3);
        BlockPos hit = context.computeOnClient(c -> c.hitResult instanceof net.minecraft.world.phys.BlockHitResult b ? b.getBlockPos() : null);
        if (!target.equals(hit)) throw new AssertionError("[matsources] looking at " + hit + " instead of " + target);
    }

    private static void pressHotkey(ClientGameTestContext context) {
        int before = context.computeOnClient(c -> SourceContainers.version());
        // (MaLiLib 26.3 reads the real keyboard state: GT.setHotkeyHeld feeds it the simulated key too)
        GT.setHotkeyHeld(context, AleConfigs.Generic.MATERIAL_SOURCE_CONTAINER_KEY.getKeybind(), true);
        context.waitTicks(1);
        GT.setHotkeyHeld(context, AleConfigs.Generic.MATERIAL_SOURCE_CONTAINER_KEY.getKeybind(), false);
        context.waitTicks(2);
        int after = context.computeOnClient(c -> SourceContainers.version());
        if (after == before) throw new AssertionError("[matsources] the hotkey (F15) did nothing");
    }

    private static boolean waitFor(ClientGameTestContext context, java.util.function.BooleanSupplier done, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (done.getAsBoolean()) return true;
            context.waitTick();
        }
        return done.getAsBoolean();
    }
}
