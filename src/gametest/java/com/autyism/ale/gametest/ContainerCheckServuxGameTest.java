package com.autyism.ale.gametest;

import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.verifier.ContainerCheck;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * “检查容器内容”在装了 Servux 的服务器上（本地测试服务器，端口 25599）：容器内容从服务端要。
 * 投影：一个装着钻石的箱子、一个第 0 格禁用、第 1 格放铁锭的合成器；世界：箱子里放成绿宝石（不对）、合成器和投影一样。
 * 箱子要框出来、合成器不框；再按住信息显示键看合成器截图（世界那边的数据也是 Servux 发来的）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class ContainerCheckServuxGameTest implements FabricClientGameTest {
    private static final String ADDRESS = "localhost:25599";
    private static final BlockPos CHEST = new BlockPos(40, -60, 20);
    private static final BlockPos CRAFTER = new BlockPos(42, -60, 20);
    private static final String NAME = "ale_ccservux";

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!java.util.Arrays.asList(System.getProperty("ale.gt", "").split(",")).contains("ccservux")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            sp.getServer().runOnServer(s -> {
                var level = s.overworld();
                level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(CRAFTER, Blocks.CRAFTER.defaultBlockState());
                if (level.getBlockEntity(CHEST) instanceof Container c) c.setItem(0, new ItemStack(Items.DIAMOND, 5));
                if (level.getBlockEntity(CRAFTER) instanceof CrafterBlockEntity c) {
                    c.setItem(1, new ItemStack(Items.IRON_INGOT, 1));
                    c.setSlotState(0, false);
                }
            });
            context.waitTicks(5);
            GT.captureAndPlace(context, sp, CHEST, CRAFTER, CHEST, NAME);
            GT.removeAllPlacements(context);
        }
        List<String> failures = new ArrayList<>();
        connect(context);
        try {
            command(context, "gamemode creative");
            command(context, "fill 36 -60 16 46 -55 24 air");
            command(context, "setblock " + xyz(CHEST) + " chest");
            command(context, "setblock " + xyz(CRAFTER) + " crafter{disabled_slots:[I;0]}");
            command(context, "item replace block " + xyz(CHEST) + " container.0 with minecraft:emerald 5");
            command(context, "item replace block " + xyz(CRAFTER) + " container.1 with minecraft:iron_ingot 1");
            command(context, "tp @s 42.5 -59 16.5 0 25");
            context.waitFor(c -> c.level.getBlockState(CRAFTER).is(Blocks.CRAFTER) && c.player.getZ() < 17, 200);
            context.runOnClient(c -> {
                Configs.Visuals.ENABLE_SCHEMATIC_OVERLAY.setBooleanValue(true);
                Configs.Generic.ENTITY_DATA_SYNC.setBooleanValue(true);
                AleConfigs.Generic.CONTAINER_CHECK.setBooleanValue(true);
                LitematicaSchematic schematic = LitematicaSchematic.createFromFile(DataManager.getSchematicsBaseDirectory(), NAME + ".litematic");
                if (schematic == null) throw new AssertionError("[ccservux] could not read " + NAME);
                SchematicPlacement p = SchematicPlacement.createFor(schematic, CHEST, NAME, true, true);
                DataManager.getSchematicPlacementManager().addSchematicPlacement(p, false);
            });
            boolean wrong = waitFor(context, () -> context.computeOnClient(c -> ContainerCheck.isWrong(CHEST)), 600);
            context.waitTicks(100);
            boolean crafterWrong = context.computeOnClient(c -> ContainerCheck.isWrong(CRAFTER));
            GT.log("[ccservux] chest with emeralds boxed=" + wrong + ", crafter like the schematic boxed=" + crafterWrong);
            if (!wrong) failures.add("chest-not-boxed");
            if (crafterWrong) failures.add("crafter-boxed");
            // the crafter in the info overlay, both sides
            command(context, "tp @s 42.5 -59 17.5 0 35");
            context.waitTicks(20);
            context.runOnClient(c -> {
                GT.setGuiHidden(c, false);
                Configs.InfoOverlays.BLOCK_INFO_OVERLAY_ENABLED.setBooleanValue(true);
            });
            GT.setHotkeyHeld(context, Hotkeys.RENDER_INFO_OVERLAY.getKeybind(), true);
            context.waitTicks(40);
            GT.shot(context, "ale-ccservux-crafter-info");
            GT.setHotkeyHeld(context, Hotkeys.RENDER_INFO_OVERLAY.getKeybind(), false);
            // fixing the chest takes the box away
            command(context, "item replace block " + xyz(CHEST) + " container.0 with minecraft:diamond 5");
            boolean fixed = waitFor(context, () -> context.computeOnClient(c -> !ContainerCheck.isWrong(CHEST)), 600);
            GT.log("[ccservux] chest fixed: box gone=" + fixed);
            if (!fixed) failures.add("box-not-gone");
        } finally {
            GT.removeAllPlacements(context);
            disconnect(context);
        }
        if (!failures.isEmpty()) throw new AssertionError("[ccservux] " + failures);
        GT.log("[ccservux] OK");
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
