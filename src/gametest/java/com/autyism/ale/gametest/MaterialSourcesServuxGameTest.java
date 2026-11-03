package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.materials.SourceContainers;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chosen material source containers on a server (the local Servux test server on port 25599, never a real server):
 * a) without entity data sync the contents are unknown and not counted, until the container is opened once;
 * b) with Servux (entity data sync on) they follow changes without opening, shulker boxes inside included.
 * Only with -Pgt=matsourcesservux (the test server has to be started first).
 */
@SuppressWarnings("UnstableApiUsage")
public final class MaterialSourcesServuxGameTest implements FabricClientGameTest {
    private static final String ADDRESS = "localhost:25599";
    private static final BlockPos BARREL = new BlockPos(40, -60, 40);
    private static final BlockPos CHEST = new BlockPos(42, -60, 40);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.explicit("matsourcesservux")) return;
        List<String> failures = new ArrayList<>();
        connect(context);
        try {
            context.runOnClient(c -> {
                SourceContainers.clear();
                AleConfigs.Generic.MATERIAL_SOURCE_CONTAINERS.setBooleanValue(true);
                AleConfigs.Generic.MATERIAL_SOURCE_CONTAINER_KEY.getKeybind().setValueFromString("F15");
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(false);
                Configs.Generic.ENTITY_DATA_SYNC_BACKUP.setBooleanValue(false);
            });
            command(context, "gamemode creative");
            command(context, "fill 36 -60 36 46 -56 46 air");
            command(context, "setblock " + xyz(BARREL) + " barrel");
            command(context, "setblock " + xyz(CHEST) + " chest");
            command(context, "item replace block " + xyz(BARREL) + " container.0 with minecraft:apple 20");
            command(context, "item replace block " + xyz(CHEST) + " container.4 with minecraft:white_shulker_box[minecraft:container=[{slot:0,item:{id:\"minecraft:iron_ingot\",count:10}}]]");
            command(context, "tp @s 40.5 -60 43.5 180 30");
            context.waitFor(c -> c.level.getBlockState(CHEST).is(net.minecraft.world.level.block.Blocks.CHEST) && Math.abs(c.player.getZ() - 43.5) < 0.01, 200);
            context.waitTicks(10);
            press(context, BARREL);
            command(context, "tp @s 42.5 -60 43.5 180 30");
            context.waitFor(c -> Math.abs(c.player.getX() - 42.5) < 0.01, 200);
            context.waitTicks(5);
            press(context, CHEST);
            context.waitTicks(60);
            int unknown = context.computeOnClient(c -> SourceContainers.unknownCount());
            Map<Item, Integer> blind = available(context);
            GT.log("[matsourcesservux] no entity data sync: unknown=" + unknown + " available " + blind);
            if (unknown != 2 || blind.get(Items.APPLE) != 0) failures.add("blind:" + unknown + " " + blind);

            // a) open the chest once: known from its screen
            context.runOnClient(c -> {
                if (c.hitResult instanceof BlockHitResult hit) c.gameMode.useItemOn(c.player, InteractionHand.MAIN_HAND, hit);
            });
            boolean opened = waitFor(context, () -> context.computeOnClient(c -> c.player.containerMenu != c.player.inventoryMenu), 100);
            context.waitTicks(10);
            context.runOnClient(c -> c.player.closeContainer());
            context.waitTicks(5);
            Map<Item, Integer> seen = available(context);
            int unknownAfter = context.computeOnClient(c -> SourceContainers.unknownCount());
            GT.log("[matsourcesservux] opened the chest (" + opened + "): unknown=" + unknownAfter + " available " + seen);
            if (!opened || unknownAfter != 1 || seen.get(Items.IRON_INGOT) != 10) failures.add("opened:" + opened + " " + unknownAfter + " " + seen);

            // b) Servux: known without opening, and follows changes
            context.runOnClient(c -> {
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                SourceContainers.refreshNow();
            });
            boolean servux = waitFor(context, () -> context.computeOnClient(c -> com.autyism.ale.verifier.ServerContainerData.available()), 200);
            boolean known = waitFor(context, () -> context.computeOnClient(c -> SourceContainers.unknownCount() == 0), 400);
            Map<Item, Integer> synced = available(context);
            GT.log("[matsourcesservux] Servux=" + servux + ": all known=" + known + " available " + synced);
            if (!servux || !known || synced.get(Items.APPLE) != 20) failures.add("servux:" + servux + " " + known + " " + synced);
            command(context, "item replace block " + xyz(BARREL) + " container.1 with minecraft:apple 12");
            int[] t = {0};
            boolean followed = waitFor(context, () -> {
                t[0]++;
                return available(context).get(Items.APPLE) == 32;
            }, 400);
            GT.log("[matsourcesservux] barrel changed by command: followed=" + followed + " after " + t[0] + " ticks, available " + available(context));
            if (!followed) failures.add("not-followed");
            GT.shot(context, "ale-matsourcesservux");
        } finally {
            context.runOnClient(c -> SourceContainers.clear());
            disconnect(context);
        }
        if (!failures.isEmpty()) throw new AssertionError("[matsourcesservux] " + failures);
        GT.log("[matsourcesservux] OK");
    }

    private static void press(ClientGameTestContext context, BlockPos target) {
        BlockPos hit = context.computeOnClient(c -> c.hitResult instanceof BlockHitResult b ? b.getBlockPos() : null);
        if (!target.equals(hit)) throw new AssertionError("[matsourcesservux] looking at " + hit + " instead of " + target);
        // (MaLiLib 26.3 reads the real keyboard state: GT.setHotkeyHeld feeds it the simulated key too)
        GT.setHotkeyHeld(context, AleConfigs.Generic.MATERIAL_SOURCE_CONTAINER_KEY.getKeybind(), true);
        context.waitTicks(1);
        GT.setHotkeyHeld(context, AleConfigs.Generic.MATERIAL_SOURCE_CONTAINER_KEY.getKeybind(), false);
        context.waitTicks(3);
    }

    private static Map<Item, Integer> available(ClientGameTestContext context) {
        return context.computeOnClient(c -> {
            List<MaterialListEntry> list = new ArrayList<>();
            for (Item item : List.of(Items.APPLE, Items.IRON_INGOT)) list.add(new MaterialListEntry(new ItemStack(item), 100, 100, 0, 0));
            MaterialListUtils.updateAvailableCounts(list, c.player);
            Map<Item, Integer> m = new HashMap<>();
            for (MaterialListEntry e : list) m.put(e.getStack().getItem(), e.getCountAvailable());
            return m;
        });
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static boolean waitFor(ClientGameTestContext context, java.util.function.BooleanSupplier done, int ticks) {
        for (int i = 0; i < ticks; i++) {
            if (done.getAsBoolean()) return true;
            context.waitTick();
        }
        return done.getAsBoolean();
    }

    private static void connect(ClientGameTestContext context) {
        context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(ADDRESS),
                new ServerData("ale test", ADDRESS, ServerData.Type.OTHER), false, null));
        context.waitFor(client -> client.player != null && client.level != null && client.screen == null, 1200);
        context.waitTicks(40);
    }

    private static void disconnect(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (client.level != null) client.disconnect(new TitleScreen(), false);
        });
        context.waitFor(client -> client.level == null, 600);
        context.waitTicks(20);
    }

    private static void command(ClientGameTestContext context, String cmd) {
        context.runOnClient(client -> client.player.connection.sendCommand(cmd));
        context.waitTicks(5);
    }
}
