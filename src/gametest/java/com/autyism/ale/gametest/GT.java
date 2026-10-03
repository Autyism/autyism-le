package com.autyism.ale.gametest;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.LayerMode;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** gametest 公共工具。 */
@SuppressWarnings("UnstableApiUsage")
public final class GT {
    private GT() {
    }

    public static void log(String msg) {
        System.out.println("[ALE-GT] " + msg);
    }

    public static TestSingleplayerContext newWorld(ClientGameTestContext context) {
        TestSingleplayerContext sp = context.worldBuilder().create();
        // Litematica 会把上一个同名测试世界的投影放置读回来：每个测试开始时清空，避免互相影响
        removeAllPlacements(context);
        sp.getServer().runCommand("gamerule doDaylightCycle false");
        sp.getServer().runCommand("gamerule doMobSpawning false");
        sp.getServer().runCommand("gamerule doWeatherCycle false");
        sp.getServer().runCommand("gamemode survival @a");
        // 相当于“允许作弊”的单人世界
        sp.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            server.getPlayerList().op(player.nameAndId());
        });
        return sp;
    }

    /** 每 tick 在测试线程上检查（可访问服务端），返回用掉的 tick 数 */
    public static int waitServer(ClientGameTestContext context, BooleanSupplier done, int maxTicks, String failMessage) {
        for (int tick = 0; tick <= maxTicks; tick++) {
            if (done.getAsBoolean()) return tick;
            context.waitTick();
        }
        throw new AssertionError(failMessage + " within " + maxTicks + " ticks");
    }

    /** 清空一片区域：y=63 基岩地面，上方空气 */
    public static void clearArena(TestSingleplayerContext sp, int x0, int z0, int x1, int z1, int yTop) {
        sp.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 63, z), Blocks.BEDROCK.defaultBlockState());
                    for (int y = 64; y <= yTop; y++) {
                        level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        });
    }

    /**
     * 真实流程：把客户端世界里 min..max 的方块（含方块实体/实体）截取成 .litematic 文件写到 schematics 目录，
     * 再从文件读回并创建投影放置（origin = 放置原点）。返回放置。
     */
    public static fi.dy.masa.litematica.schematic.placement.SchematicPlacement captureAndPlace(
            ClientGameTestContext context, BlockPos min, BlockPos max, BlockPos origin, String name) {
        throw new UnsupportedOperationException("use captureAndPlace(context, sp, ...)");
    }

    /**
     * 真实流程：在服务端线程把 min..max 的方块（含方块实体内容物/实体）截取成 .litematic 写到 schematics 目录
     * （与 Litematica 单人模式保存一致，客户端世界里没有容器内容物），再在客户端从文件读回并创建投影放置。
     */
    public static fi.dy.masa.litematica.schematic.placement.SchematicPlacement captureAndPlace(
            ClientGameTestContext context, TestSingleplayerContext sp, BlockPos min, BlockPos max, BlockPos origin, String name) {
        java.nio.file.Path dir = context.computeOnClient(client -> DataManager.getSchematicsBaseDirectory());
        boolean written = sp.getServer().computeOnServer(server -> {
            fi.dy.masa.litematica.selection.AreaSelection area = new fi.dy.masa.litematica.selection.AreaSelection();
            area.setName(name);
            area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(min, max, name), false);
            area.setExplicitOrigin(min);
            var schematic = fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromWorld(server.overworld(), area,
                    new fi.dy.masa.litematica.schematic.LitematicaSchematic.SchematicSaveInfo(false, false), "ALE", s -> log("capture: " + s));
            return schematic != null && schematic.writeToFile(dir, name, true);
        });
        if (!written) throw new AssertionError("could not capture/write schematic " + name);
        return context.computeOnClient(client -> {
            var loaded = fi.dy.masa.litematica.schematic.LitematicaSchematic.createFromFile(dir, name + ".litematic");
            if (loaded == null) throw new AssertionError("could not read back schematic " + name);
            var placement = fi.dy.masa.litematica.schematic.placement.SchematicPlacement.createFor(loaded, origin, name, true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
            return placement;
        });
    }

    /** 等待投影世界里 pos 处出现期望的方块（放置是异步载入的） */
    public static void waitSchematicBlock(ClientGameTestContext context, BlockPos pos, net.minecraft.world.level.block.Block block) {
        context.waitFor(client -> {
            WorldSchematic w = SchematicWorldHandler.getSchematicWorld();
            return w != null && w.getBlockState(pos).is(block);
        }, 400);
    }

    public static void removeAllPlacements(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var mgr = DataManager.getSchematicPlacementManager();
            for (var p : new ArrayList<>(mgr.getAllSchematicsPlacements())) mgr.removeSchematicPlacement(p);
        });
    }

    public static List<String> mismatches(TestSingleplayerContext sp, BlockPos min, BlockPos max, Function<BlockPos, BlockState> expected) {
        return sp.getServer().computeOnServer(server -> {
            List<String> result = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                BlockState state = server.overworld().getBlockState(pos);
                BlockState want = expected.apply(pos.immutable());
                if (state != want) result.add(pos.toShortString() + "=" + state + " (want " + want + ")");
            }
            return result;
        });
    }

    public static int countPlaced(TestSingleplayerContext sp, BlockPos min, BlockPos max) {
        return sp.getServer().computeOnServer(server -> {
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (!server.overworld().getBlockState(pos).isAir()) n++;
            }
            return n;
        });
    }


    /** 当前 malilib 界面里显示文字包含 text 的按钮（没有返回 null） */
    @org.jetbrains.annotations.Nullable
    public static fi.dy.masa.malilib.gui.button.ButtonBase findButton(net.minecraft.client.Minecraft client, String text) {
        if (!(client.screen instanceof fi.dy.masa.malilib.gui.GuiBase gui)) return null;
        try {
            java.lang.reflect.Field f = fi.dy.masa.malilib.gui.GuiBase.class.getDeclaredField("buttons");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<fi.dy.masa.malilib.gui.button.ButtonBase> buttons = (List<fi.dy.masa.malilib.gui.button.ButtonBase>) f.get(gui);
            for (var b : buttons) {
                java.lang.reflect.Field ds = fi.dy.masa.malilib.gui.button.ButtonBase.class.getDeclaredField("displayString");
                ds.setAccessible(true);
                String label = String.valueOf(ds.get(b)).replaceAll("§.", "");
                if (label.contains(text)) return b;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return null;
    }

    /** 像真人一样把鼠标移到按钮上并点击左键 */
    public static void clickButton(ClientGameTestContext context, String text) {
        double[] pos = context.computeOnClient(client -> {
            var b = findButton(client, text);
            if (b == null) throw new AssertionError("button '" + text + "' not found on " + client.screen);
            // 按钮可能在可左右滑动的按钮栏里被翻到看不见的地方：先翻过来
            if (client.screen instanceof fi.dy.masa.malilib.gui.GuiBase g) com.autyism.ale.gui.ButtonRail.reveal(g, b);
            double scale = client.getWindow().getGuiScale();
            return new double[]{(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }
}
