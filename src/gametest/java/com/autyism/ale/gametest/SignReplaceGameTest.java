package com.autyism.ale.gametest;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetContainer;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求 10：材料列表里用 Schematic Preview 的“替换”把橡木告示牌换成云杉木：
 * 四种形态各自换成云杉木的同一形态，旋转/朝向/含水/ATTACHED 不变，正反两面文字保留。
 * 全程用鼠标/键盘操作真实界面（替换按钮 → 方块选择界面搜索并点选 → 完成）。
 */
@SuppressWarnings("UnstableApiUsage")
public final class SignReplaceGameTest implements FabricClientGameTest {
    private static final BlockPos MIN = new BlockPos(30, 64, 0);
    private static final BlockPos MAX = new BlockPos(37, 66, 0);

    private static Map<BlockPos, BlockState> layout() {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        m.put(new BlockPos(30, 64, 0), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 5));
        m.put(new BlockPos(32, 64, 0), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.EAST)
                .setValue(BlockStateProperties.WATERLOGGED, true));
        m.put(new BlockPos(34, 65, 0), Blocks.OAK_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, 3)
                .setValue(CeilingHangingSignBlock.ATTACHED, true));
        m.put(new BlockPos(36, 65, 0), Blocks.OAK_WALL_HANGING_SIGN.defaultBlockState().setValue(WallHangingSignBlock.FACING, Direction.NORTH));
        m.put(new BlockPos(37, 66, 0), Blocks.STONE.defaultBlockState());
        return m;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("signreplace")) return;
        // 装了 Schematic Preview：测它的“替换”加上 ALE 的告示牌修复；没装：测 ALE 自己的“替换”
        boolean original = FabricLoader.getInstance().isModLoaded("schematicpreview");
        GT.log("[signreplace] using " + (original ? "Schematic Preview's" : "ALE's own") + " Replace dialog");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 26, -4, 42, 4, 70);
            sp.getServer().runCommand("tp @a 33.5 64 4.5 180 10");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 33.5) < 0.01, 200);
            Map<BlockPos, BlockState> layout = layout();
            sp.getServer().runOnServer(s -> {
                for (var e : layout.entrySet()) {
                    s.overworld().setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
                    if (s.overworld().getBlockEntity(e.getKey()) instanceof SignBlockEntity sign) {
                        String id = String.valueOf(e.getKey().getX());
                        GT.setSignLine(sign, true, 0, Component.literal("front " + id));
                        GT.setSignLine(sign, false, 2, Component.literal("back " + id));
                    }
                }
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_sign_replace");

            // 1) 打开材料列表，点“橡木告示牌”那一行的“替换”按钮（Schematic Preview 添加的）
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(10);
            GT.shot(context, "ale-sign-material-list");
            clickReplaceFor(context, Items.OAK_SIGN);

            // 2) 搜索 spruce_wall_sign（故意选“挂墙”形态，必须仍按每块原来的形态替换），点第一个结果，再点“完成”
            pickInDialog(context, "spruce_wall_sign", "ale-sign-block-select");

            // 3) 检查投影容器
            List<String> problems = context.computeOnClient(c -> {
                List<String> out = new ArrayList<>();
                LitematicaSchematic schematic = placement.getSchematic();
                String region = schematic.getAreas().keySet().iterator().next();
                LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);
                Map<BlockPos, ?> bes = schematic.getBlockEntityMapForRegion(region);
                for (var e : layout.entrySet()) {
                    BlockPos rel = e.getKey().subtract(MIN);
                    BlockState got = container.get(rel.getX(), rel.getY(), rel.getZ());
                    BlockState want = expectedAfter(e.getValue());
                    if (got != want) out.add(e.getKey().toShortString() + " got " + got + " want " + want);
                    if (want.getBlock() instanceof SignBlock) {
                        Object nbt = bes.get(rel);
                        String s = String.valueOf(nbt);
                        String id = String.valueOf(e.getKey().getX());
                        if (!s.contains("front " + id) || !s.contains("back " + id)) out.add(e.getKey().toShortString() + " text lost: " + s);
                    }
                }
                return out;
            });
            if (!problems.isEmpty()) throw new AssertionError("[signreplace] " + problems.size() + " problems:\n  " + String.join("\n  ", problems));
            GT.log("[signreplace] standing/wall signs OK (hanging untouched as they are another material entry)");

            // 4) 再替换悬挂式告示牌条目 → 悬挂/挂墙悬挂都变成樱花木同形态
            context.runOnClient(c -> {
                MaterialListSchematic list = new MaterialListSchematic(placement.getSchematic(), true);
                DataManager.setMaterialList(list);
                c.setScreen(new GuiMaterialList(list));
            });
            context.waitTicks(10);
            clickReplaceFor(context, Items.OAK_HANGING_SIGN);
            pickInDialog(context, "cherry_hanging_sign", null);
            List<String> problems2 = context.computeOnClient(c -> {
                List<String> out = new ArrayList<>();
                LitematicaSchematic schematic = placement.getSchematic();
                String region = schematic.getAreas().keySet().iterator().next();
                LitematicaBlockStateContainer container = schematic.getSubRegionContainer(region);
                BlockState hanging = container.get(4, 1, 0);
                BlockState wallHanging = container.get(6, 1, 0);
                BlockState wantH = Blocks.CHERRY_HANGING_SIGN.defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, 3).setValue(CeilingHangingSignBlock.ATTACHED, true);
                BlockState wantW = Blocks.CHERRY_WALL_HANGING_SIGN.defaultBlockState().setValue(WallHangingSignBlock.FACING, Direction.NORTH);
                if (hanging != wantH) out.add("hanging " + hanging);
                if (wallHanging != wantW) out.add("wall hanging " + wallHanging);
                String nbt = String.valueOf(schematic.getBlockEntityMapForRegion(region).get(new BlockPos(6, 1, 0)));
                if (!nbt.contains("front 36") || !nbt.contains("back 36")) out.add("wall hanging text lost " + nbt);
                return out;
            });
            if (!problems2.isEmpty()) throw new AssertionError("[signreplace] hanging: " + problems2);
            GT.log("[signreplace] OK: all four sign forms replaced with the same form of the target wood, states and both text sides kept");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> c.setScreen(null));
        }
    }

    private static BlockState expectedAfter(BlockState before) {
        Block target = null;
        if (before.is(Blocks.OAK_SIGN)) target = Blocks.SPRUCE_SIGN;
        else if (before.is(Blocks.OAK_WALL_SIGN)) target = Blocks.SPRUCE_WALL_SIGN;
        if (target == null) return before;
        BlockState s = target.defaultBlockState();
        for (var p : before.getProperties()) s = copy(before, s, p);
        return s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState from, BlockState to, net.minecraft.world.level.block.state.properties.Property p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }

    /** 在“替换”对话框里搜索并点第一个结果，再确定（Schematic Preview 的或 ALE 自己的对话框） */
    private static void pickInDialog(ClientGameTestContext context, String search, @org.jetbrains.annotations.Nullable String shot) {
        if (FabricLoader.getInstance().isModLoaded("schematicpreview")) {
            context.waitFor(c -> c.screen != null && c.screen.getClass().getSimpleName().equals("GuiBlockSelect"), 40);
            context.runOnClient(c -> {
                try {
                    Field f = c.screen.getClass().getDeclaredField("searchField");
                    f.setAccessible(true);
                    ((net.minecraft.client.gui.components.EditBox) f.get(c.screen)).setFocused(true);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            });
            context.getInput().typeChars(search);
            context.waitTicks(3);
            double[] cell = context.computeOnClient(c -> {
                int x = c.screen.width - 200 >> 1, y = c.screen.height - 200 >> 1;
                double scale = c.getWindow().getGuiScale();
                return new double[]{(x + 10) * scale, (y + 38 + 10) * scale};
            });
            context.getInput().setCursorPos(cell[0], cell[1]);
            context.waitTick();
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            if (shot != null) GT.shot(context, shot);
            GT.clickButton(context, "Ok");
        } else {
            context.waitFor(c -> c.screen instanceof com.autyism.ale.replace.ReplaceBlockScreen, 40);
            context.getInput().typeChars(search);
            context.waitTicks(3);
            double[] cell = context.computeOnClient(c -> {
                int[] g = ((com.autyism.ale.replace.ReplaceBlockScreen) c.screen).gridGeometry();
                double scale = c.getWindow().getGuiScale();
                return new double[]{(g[0] + g[2] / 2.0) * scale, (g[1] + g[2] / 2.0) * scale};
            });
            context.getInput().setCursorPos(cell[0], cell[1]);
            context.waitTick();
            context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            context.waitTicks(2);
            if (shot != null) GT.shot(context, shot);
            GT.clickButton(context, "OK");
        }
        context.waitTicks(5);
    }

    /** 在材料列表里找到某物品那一行的“Replace”按钮并用鼠标点击 */
    private static void clickReplaceFor(ClientGameTestContext context, net.minecraft.world.item.Item item) {
        double[] pos = context.computeOnClient(c -> {
            try {
                var getList = GuiListBase.class.getDeclaredMethod("getListWidget");
                getList.setAccessible(true);
                WidgetListBase<?, ?> list = (WidgetListBase<?, ?>) getList.invoke(c.screen);
                Field lw = WidgetListBase.class.getDeclaredField("listWidgets");
                lw.setAccessible(true);
                Field sub = WidgetContainer.class.getDeclaredField("subWidgets");
                sub.setAccessible(true);
                Field ds = ButtonBase.class.getDeclaredField("displayString");
                ds.setAccessible(true);
                for (Object w : (List<?>) lw.get(list)) {
                    Object entry = ((WidgetListEntryBase<?>) w).getEntry();
                    if (entry instanceof MaterialListEntry me && me.getStack().is(item)) {
                        for (Object o : (List<?>) sub.get(w)) {
                            if (o instanceof ButtonBase b && String.valueOf(ds.get(b)).contains("Replace")) {
                                double scale = c.getWindow().getGuiScale();
                                return new double[]{(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
                            }
                        }
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            throw new AssertionError("Replace button for " + item + " not found");
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTick();
    }
}
