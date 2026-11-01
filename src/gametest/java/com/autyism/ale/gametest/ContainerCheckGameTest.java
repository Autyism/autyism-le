package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.verifier.ContainerCheck;
import com.autyism.ale.verifier.SlotHints;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * “检查容器内容”和“容器格子提示”：
 * <ul>
 * <li>投影和世界放同样的容器、不同的内容：一样的不框；放错东西、放错格子、数量不对、投影里空着世界里有东西、合成器禁用的格子不同的框出来；
 * 世界里还空着的（要填）不框（画 F）；</li>
 * <li>打开放错格子的箱子：投影里有、这格空着的是“缺”（虚影），投影里空着、这格有东西的是“多出”；数量不对的是“数量”；</li>
 * <li>按住信息显示键看投影里的合成器（截图，查合成器显示的问题）。</li>
 * </ul>
 */
@SuppressWarnings("UnstableApiUsage")
public final class ContainerCheckGameTest implements FabricClientGameTest {
    private static final int X0 = 460, STEP = 3, Y = 64, Z = 0;

    /** name, what the schematic holds, what the world holds, should it be boxed */
    private record Case(String name, Consumer<Container> schematic, Consumer<Container> world, boolean wrong, boolean crafter) {
    }

    private static List<Case> cases() {
        List<Case> list = new ArrayList<>();
        list.add(new Case("same", c -> c.setItem(0, new ItemStack(Items.DIAMOND, 5)), c -> c.setItem(0, new ItemStack(Items.DIAMOND, 5)), false, false));
        list.add(new Case("wrong-item", c -> c.setItem(0, new ItemStack(Items.DIAMOND, 5)), c -> c.setItem(0, new ItemStack(Items.EMERALD, 5)), true, false));
        list.add(new Case("wrong-slot", c -> c.setItem(0, new ItemStack(Items.DIAMOND, 5)), c -> c.setItem(3, new ItemStack(Items.DIAMOND, 5)), true, false));
        list.add(new Case("count", c -> c.setItem(0, new ItemStack(Items.DIAMOND, 5)), c -> c.setItem(0, new ItemStack(Items.DIAMOND, 3)), true, false));
        list.add(new Case("empty-world", c -> c.setItem(0, new ItemStack(Items.DIAMOND, 5)), c -> {
        }, false, false));
        list.add(new Case("extra", c -> {
        }, c -> c.setItem(2, new ItemStack(Items.DIRT, 1)), true, false));
        list.add(new Case("crafter-disabled", c -> {
            c.setItem(1, new ItemStack(Items.IRON_INGOT, 1));
            ((CrafterBlockEntity) c).setSlotState(0, false);
        }, c -> c.setItem(1, new ItemStack(Items.IRON_INGOT, 1)), true, true));
        return list;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("containercheck")) return;
        List<Case> cases = cases();
        int xMax = X0 + STEP * (cases.size() - 1);
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, X0 - 4, -6, xMax + 4, 8, 72);
            ViewShots.begin(context, sp);
            ViewShots.lookAt(context, sp, new BlockPos(X0 + STEP * 3, Y, Z));
            context.waitTicks(20);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                AleConfigs.Generic.CONTAINER_CHECK.setBooleanValue(true);
                AleConfigs.Generic.CONTAINER_SLOT_HINTS.setBooleanValue(true);
            });
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int i = 0; i < cases.size(); i++) {
                    BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                    level.setBlock(pos, (cases.get(i).crafter ? Blocks.CRAFTER : Blocks.CHEST).defaultBlockState(), ViewShots.QUIET);
                    if (level.getBlockEntity(pos) instanceof Container c) cases.get(i).schematic.accept(c);
                }
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, new BlockPos(X0, Y, Z), new BlockPos(xMax, Y, Z), new BlockPos(X0, Y, Z), "ale_containercheck");
            GT.waitSchematicBlock(context, new BlockPos(X0, Y, Z), Blocks.CHEST);
            // the world: the same containers with the world's contents
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (int i = 0; i < cases.size(); i++) {
                    BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                    if (level.getBlockEntity(pos) instanceof Container c) {
                        c.clearContent();
                        if (c instanceof CrafterBlockEntity cr) for (int k = 0; k < 9; k++) cr.setSlotState(k, true);
                        cases.get(i).world.accept(c);
                    }
                }
            });
            ViewShots.lookAt(context, sp, new BlockPos(X0 + STEP * 3, Y, Z));
            context.waitTicks(120);

            List<String> failures = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                BlockPos pos = new BlockPos(X0 + STEP * i, Y, Z);
                boolean boxed = context.computeOnClient(c -> ContainerCheck.isWrong(pos));
                boolean ok = boxed == cases.get(i).wrong;
                GT.log(String.format(java.util.Locale.ROOT, "[containercheck] %-18s boxed=%s want=%s %s", cases.get(i).name, boxed, cases.get(i).wrong, ok ? "ok" : "WRONG"));
                if (!ok) failures.add(cases.get(i).name);
            }
            GT.shot(context, "ale-containercheck-boxes");

            // slot hints in the opened chest whose diamonds are in the wrong slot
            Map<Integer, SlotHints.Status> slotWrongSlot = open(context, sp, new BlockPos(X0 + STEP * 2, Y, Z));
            GT.shot(context, "ale-containercheck-slots-wrongslot");
            GT.log("[containercheck] wrong-slot chest hints: " + slotWrongSlot);
            if (slotWrongSlot.get(0) != SlotHints.Status.MISSING || slotWrongSlot.get(3) != SlotHints.Status.EXTRA || slotWrongSlot.size() != 2) failures.add("slots-wrong-slot");
            Map<Integer, SlotHints.Status> slotCount = open(context, sp, new BlockPos(X0 + STEP * 3, Y, Z));
            GT.log("[containercheck] count chest hints: " + slotCount);
            if (slotCount.get(0) != SlotHints.Status.COUNT || slotCount.size() != 1) failures.add("slots-count");
            Map<Integer, SlotHints.Status> slotItem = open(context, sp, new BlockPos(X0 + STEP, Y, Z));
            GT.log("[containercheck] wrong-item chest hints: " + slotItem);
            if (slotItem.get(0) != SlotHints.Status.WRONG || slotItem.size() != 1) failures.add("slots-wrong-item");
            Map<Integer, SlotHints.Status> slotEmpty = open(context, sp, new BlockPos(X0 + STEP * 4, Y, Z));
            GT.shot(context, "ale-containercheck-slots-ghost");
            GT.log("[containercheck] empty chest hints: " + slotEmpty);
            if (slotEmpty.get(0) != SlotHints.Status.MISSING) failures.add("slots-ghost");
            // putting the missing item in by hand clears the hint right away, without reopening the chest
            net.minecraft.world.item.ItemStack want = context.computeOnClient(c -> c.screen instanceof AbstractContainerScreen<?> s
                    ? SlotHints.hint(s.getMenu(), s.getMenu().slots.get(0)).expected().copy() : net.minecraft.world.item.ItemStack.EMPTY);
            // what the client shows right after the player clicks the item in: the slot changed, no new state id from the server
            context.runOnClient(c -> {
                if (c.screen instanceof AbstractContainerScreen<?> s) s.getMenu().slots.get(0).set(want.copy());
            });
            for (int i = 0; i < 40 && context.computeOnClient(c -> c.screen instanceof AbstractContainerScreen<?> sc
                    && SlotHints.hint(sc.getMenu(), sc.getMenu().slots.get(0)) != null); i++) context.waitTicks(1);
            Map<Integer, SlotHints.Status> afterPut = context.computeOnClient(c -> {
                Map<Integer, SlotHints.Status> out = new java.util.TreeMap<>();
                if (c.screen instanceof AbstractContainerScreen<?> screen) {
                    for (Slot slot : screen.getMenu().slots) {
                        SlotHints.Hint h = SlotHints.hint(screen.getMenu(), slot);
                        if (h != null) out.put(slot.getContainerSlot(), h.status());
                    }
                }
                return out;
            });
            GT.log("[containercheck] after putting " + want + " in by hand: " + afterPut + " slot0 now " + context.computeOnClient(c -> c.screen instanceof AbstractContainerScreen<?> s ? s.getMenu().slots.get(0).getItem().toString() : "no screen"));
            if (afterPut.containsKey(0)) failures.add("slots-live-update");

            // the crafter in Litematica's info overlay (hold the key), for the crafter display problem
            BlockPos crafter = new BlockPos(X0 + STEP * 6, Y, Z);
            context.runOnClient(c -> c.setScreen(null));
            context.waitTicks(5);
            ViewShots.lookAt(context, sp, crafter);
            context.waitTicks(10);
            context.runOnClient(c -> {
                GT.setGuiHidden(c, false);
                Configs.InfoOverlays.BLOCK_INFO_OVERLAY_ENABLED.setBooleanValue(true);
            });
            GTFilter.clientBestWorld = true;
            GT.setHotkeyHeld(context, Hotkeys.RENDER_INFO_OVERLAY.getKeybind(), true);
            context.waitTicks(4);
            GT.shot(context, "ale-containercheck-crafter-info");
            GT.setHotkeyHeld(context, Hotkeys.RENDER_INFO_OVERLAY.getKeybind(), false);
            GTFilter.clientBestWorld = false;

            if (!failures.isEmpty()) throw new AssertionError("[containercheck] failed: " + failures);
            GT.log("[containercheck] OK");
        } finally {
            GTFilter.clientBestWorld = false;
            GT.removeAllPlacements(context);
        }
    }

    /** Opens the container at pos like a right click would (the client remembers the block), returns the hints by slot */
    private static Map<Integer, SlotHints.Status> open(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos pos) {
        context.runOnClient(c -> c.setScreen(null));
        context.waitTicks(5);
        ViewShots.lookAt(context, sp, pos);
        context.runOnClient(c -> com.autyism.ale.verifier.OpenedContainers.onUseItemOn(pos));
        sp.getServer().runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            if (s.overworld().getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider menu) player.openMenu(menu);
        });
        context.waitFor(c -> c.screen instanceof AbstractContainerScreen<?>, 100);
        context.waitTicks(10);
        return context.computeOnClient(c -> {
            Map<Integer, SlotHints.Status> out = new java.util.TreeMap<>();
            if (c.screen instanceof AbstractContainerScreen<?> screen) {
                for (Slot slot : screen.getMenu().slots) {
                    SlotHints.Hint h = SlotHints.hint(screen.getMenu(), slot);
                    if (h != null) out.put(slot.getContainerSlot(), h.status());
                }
            }
            return out;
        });
    }
}
