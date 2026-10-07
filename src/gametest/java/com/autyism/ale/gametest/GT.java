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

    /** 测试正按住的键（MaLiLib 26.3 起读真实键盘状态，见 HeldKeysMixin） */
    public static final java.util.Set<Integer> HELD_KEYS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void log(String msg) {
        System.out.println("[ALE-GT] " + msg);
    }

    public static TestSingleplayerContext newWorld(ClientGameTestContext context) {
        TestSingleplayerContext sp = context.worldBuilder().create();
        //? if <1.21.9 {
        /*waitClientLoaded(context, sp);
        *///?}
        // Litematica 会把上一个同名测试世界的投影放置读回来：每个测试开始时清空，避免互相影响
        removeAllPlacements(context);
        //? if >=1.21.11 {
        sp.getServer().runCommand("gamerule advance_time false");
        sp.getServer().runCommand("gamerule spawn_mobs false");
        sp.getServer().runCommand("gamerule advance_weather false");
        // 1.21.11 改了游戏规则的名字（doDaylightCycle → advance_time……），旧名字的指令会静默失败：读回来确认
        sp.getServer().runOnServer(server -> {
            var rules = server.overworld().getGameRules();
            if (rules.get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME)
                    || rules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS)
                    || rules.get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_WEATHER)) {
                throw new AssertionError("[GT] gamerule commands did not apply");
            }
        });
        //?} else {
        /*sp.getServer().runCommand("gamerule doDaylightCycle false");
        sp.getServer().runCommand("gamerule doMobSpawning false");
        sp.getServer().runCommand("gamerule doWeatherCycle false");
        sp.getServer().runOnServer(server -> {
            var rules = server.overworld().getGameRules();
            if (rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)
                    || rules.getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                    || rules.getBoolean(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE)) {
                throw new AssertionError("[GT] gamerule commands did not apply");
            }
        });
        *///?}
        sp.getServer().runCommand("gamemode survival @a");
        // 相当于“允许作弊”的单人世界
        sp.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            //? if >=1.21.9 {
            server.getPlayerList().op(player.nameAndId());
            //?} else
            //server.getPlayerList().op(player.getGameProfile());
        });
        return sp;
    }

    //? if <1.21.9 {
    /*// 1.21.9 以前 create() 不等“下载地形”界面关闭就返回；客户端发出“已载入”之前，服务端会忽略玩家的操作。
    // 1.21.9 起 create() 本来就会等到这一步，这里补上同样的等待，各版本的测试条件才一致
    public static void waitClientLoaded(ClientGameTestContext context, TestSingleplayerContext sp) {
        context.waitFor(client -> client.player != null && client.player.hasClientLoaded()
                && !(client.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen), 1200);
        waitServer(context, () -> sp.getServer().computeOnServer(server -> !server.getPlayerList().getPlayers().isEmpty()
                && server.getPlayerList().getPlayers().getFirst().hasClientLoaded()), 200, "[GT] the server never saw the client as loaded");
    }

    *///?}

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
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    /**
     * 测试截图。加 -PframeShot 时改为保存游戏正常循环里画出的最后一帧，不用 gametest 额外渲染的那一帧
     * （26.1.2 上那次额外渲染会在 Sodium 里崩溃）。
     */
    public static java.nio.file.Path shot(ClientGameTestContext context, String name) {
        if (!Boolean.getBoolean("ale.frameshot")) return context.takeScreenshot(name);
        context.waitTicks(2);
        java.util.concurrent.CompletableFuture<com.mojang.blaze3d.platform.NativeImage> image = new java.util.concurrent.CompletableFuture<>();
        // 26.2 起主渲染目标归 GameRenderer 管
        //? if >=26.2 {
        /*context.runOnClient(c -> net.minecraft.client.Screenshot.takeScreenshot(c.gameRenderer.mainRenderTarget(), image::complete));
        *///?} else
        context.runOnClient(c -> net.minecraft.client.Screenshot.takeScreenshot(c.getMainRenderTarget(), image::complete));
        for (int i = 0; i < 40 && !image.isDone(); i++) context.waitTick();
        java.nio.file.Path path = context.computeOnClient(c -> c.gameDirectory.toPath().resolve("screenshots").resolve(name + ".png"));
        com.mojang.blaze3d.platform.NativeImage img = image.getNow(null);
        if (img == null) throw new AssertionError("frame screenshot " + name + " did not complete");
        try (img) {
            java.nio.file.Files.createDirectories(path.getParent());
            img.writeToFile(path);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return path;
    }

    /**
     * 按住或松开一个 MaLiLib 热键的键。26.3 起 MaLiLib 按扫描码自己记录按下的键（PRESSED_KEYS），gametest 模拟的按键记不进去，
     * 所以这里直接记进去并刷新这个热键的状态；更早的版本模拟按键本身就够了。
     */
    public static void setHotkeyHeld(ClientGameTestContext context, fi.dy.masa.malilib.hotkeys.IKeybind keybind, boolean held) {
        List<Integer> keys = context.computeOnClient(c -> keybind.getKeys());
        if (held) HELD_KEYS.addAll(keys);
        else HELD_KEYS.removeAll(keys);
        for (int k : keys) {
            if (held) context.getInput().holdKey(k);
            else context.getInput().releaseKey(k);
        }
        context.runOnClient(c -> {
            try {
                java.lang.reflect.Field f = fi.dy.masa.malilib.hotkeys.KeybindMulti.class.getDeclaredField("PRESSED_KEYS");
                f.setAccessible(true);
                @SuppressWarnings("unchecked") List<Integer> pressed = (List<Integer>) f.get(null);
                for (Integer k : keys) {
                    pressed.remove(k);
                    if (held) pressed.add(k);
                }
                ((fi.dy.masa.malilib.hotkeys.KeybindMulti) keybind).updateIsPressed();
            } catch (NoSuchFieldException e) {
                // MaLiLib before 26.3: the simulated key is enough
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        });
    }

    /** 游戏画过的帧数（FrameCounterMixin 计数），用来看界面是否流畅 */
    public static volatile long frames;

    public static long frameCounter() {
        return frames;
    }

    /** 当前 MaLiLib 列表界面（GuiListBase）的列表部件 */
    public static Object listWidget(net.minecraft.client.Minecraft client) {
        if (!(client.screen instanceof fi.dy.masa.malilib.gui.GuiListBase<?, ?, ?> gui)) throw new AssertionError("not a list screen: " + client.screen);
        try {
            java.lang.reflect.Method getList = fi.dy.masa.malilib.gui.GuiListBase.class.getDeclaredMethod("getListWidget");
            getList.setAccessible(true);
            return getList.invoke(gui);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 文件浏览器里名字（含扩展名）为 name 的条目的中心（界面坐标）；不在屏幕上时为 null */
    @org.jetbrains.annotations.Nullable
    public static double[] entryCenter(net.minecraft.client.Minecraft client, String name) {
        Object list = listWidget(client);
        try {
            java.lang.reflect.Field f = fi.dy.masa.malilib.gui.widgets.WidgetListBase.class.getDeclaredField("listWidgets");
            f.setAccessible(true);
            for (Object o : (List<?>) f.get(list)) {
                var w = (fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase<?>) o;
                if (w.getEntry() instanceof fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry e && e.getName().equals(name)) {
                    return new double[]{w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, w.getX(), w.getY(), w.getWidth(), w.getHeight()};
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return null;
    }

    /** 文件浏览器里名字（含扩展名）为 name 的条目部件；不在屏幕上时为 null */
    @org.jetbrains.annotations.Nullable
    public static Object entryWidget(net.minecraft.client.Minecraft client, String name) {
        Object list = listWidget(client);
        try {
            java.lang.reflect.Field f = fi.dy.masa.malilib.gui.widgets.WidgetListBase.class.getDeclaredField("listWidgets");
            f.setAccessible(true);
            for (Object o : (List<?>) f.get(list)) {
                var w = (fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase<?>) o;
                if (w.getEntry() instanceof fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry e && e.getName().equals(name)) return w;
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return null;
    }

    /** 当前 MaLiLib 列表界面的搜索栏是否打开 */
    public static boolean searchBarOpen(net.minecraft.client.Minecraft client) {
        if (!(client.screen instanceof fi.dy.masa.malilib.gui.GuiListBase<?, ?, ?> gui)) return false;
        try {
            java.lang.reflect.Method getList = fi.dy.masa.malilib.gui.GuiListBase.class.getDeclaredMethod("getListWidget");
            getList.setAccessible(true);
            Object list = getList.invoke(gui);
            java.lang.reflect.Field f = fi.dy.masa.malilib.gui.widgets.WidgetListBase.class.getDeclaredField("widgetSearchBar");
            f.setAccessible(true);
            Object bar = f.get(list);
            return bar instanceof fi.dy.masa.malilib.gui.widgets.WidgetSearchBar sb && sb.isSearchOpen();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 改进的透明度（26.3 起是顺序无关透明）。1.21.11 之前对应“极佳！”画质，关掉时用“高品质”；
     * 和视频设置界面一样，切换画质后重新载入区块。
     */
    public static void setImprovedTransparency(net.minecraft.client.Minecraft client, boolean improved) {
        //? if >=1.21.11 {
        client.options.improvedTransparency().set(improved);
        //?} else {
        /*var mode = improved ? net.minecraft.client.GraphicsStatus.FABULOUS : net.minecraft.client.GraphicsStatus.FANCY;
        if (client.options.graphicsMode().get() != mode) {
            client.options.graphicsMode().set(mode);
            client.levelRenderer.allChanged();
        }
        *///?}
    }

    /** 隐藏或显示界面（F1）。26.2 起由 Hud 管理，只能切换 */
    public static void setGuiHidden(net.minecraft.client.Minecraft client, boolean hidden) {
        //? if >=26.2 {
        /*if (client.gui.hud.isHidden() != hidden) client.gui.hud.toggle();
        *///?} else
        client.options.hideGui = hidden;
    }

    /** 告示牌某一面的某一行写上文字。26.3 起 SignText 不可变，要经过 Mutable，正反面用 SignTextSlot */
    public static void setSignLine(net.minecraft.world.level.block.entity.SignBlockEntity sign, boolean front, int line,
                                   net.minecraft.network.chat.Component text) {
        //? if >=26.3 {
        /*sign.updateText(t -> t.asMutable().setLine(line, text).asImmutable(),
                front ? net.minecraft.world.level.block.entity.SignTextSlot.FRONT : net.minecraft.world.level.block.entity.SignTextSlot.BACK);
        *///?} else
        sign.updateText(t -> t.setMessage(line, text), front);
    }
}
