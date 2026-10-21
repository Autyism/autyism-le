package com.autyism.ale.gametest;

import com.autyism.ale.preview.Previews;
import com.autyism.ale.replace.ReplaceBlockScreen;
import com.autyism.ale.schematic.SchematicEditSaver;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.gui.GuiListBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.widgets.WidgetContainer;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import com.mojang.blaze3d.platform.InputConstants;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 材料列表的“替换”（ALE 自己的版本）：每行“忽略”左边的按钮 → 对话框（标题、搜索、方块网格、确定 / 取消）→
 * 投影两个区域里这种材料的方块全部换掉：相同的状态属性保留，告示牌 / 火把换成新方块对应的立式 / 挂墙形态，
 * 告示牌文字保留；提示条数；材料列表、投影渲染马上更新；标记为已修改，保存后读回来完全一致。
 */
@SuppressWarnings("UnstableApiUsage")
public final class MaterialReplaceGameTest implements FabricClientGameTest {
    private static final BlockPos A_MIN = new BlockPos(300, 64, 0);
    private static final BlockPos A_MAX = new BlockPos(309, 66, 2);
    private static final BlockPos B_MIN = new BlockPos(300, 64, 5);
    private static final BlockPos B_MAX = new BlockPos(303, 65, 6);

    private final List<String> problems = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("replace")) return;
        if (Previews.ORIGINAL_INSTALLED) {
            GT.log("[replace] Schematic Preview is installed: ALE's own Replace steps aside, skipped");
            return;
        }
        int oldScale = context.computeOnClient(c -> c.options.guiScale().get());
        UiDriver ui = new UiDriver(context, "ale-replace");
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 296, -4, 314, 10, 72);
            sp.getServer().runCommand("gamemode creative @a");
            sp.getServer().runCommand("tp @a 304.5 64 9.5 180 20");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getZ() - 9.5) < 0.01, 200);
            Map<BlockPos, BlockState> layout = layout();
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                for (var e : layout.entrySet()) {
                    level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    if (level.getBlockEntity(e.getKey()) instanceof SignBlockEntity sign) {
                        GT.setSignLine(sign, true, 0, Component.literal("sign " + e.getKey().getX()));
                    }
                }
            });
            context.waitTicks(5);
            SchematicPlacement placement = captureTwoRegions(context, sp, "ale_replace");
            LitematicaSchematic schematic = placement.getSchematic();
            GT.waitSchematicBlock(context, A_MIN, Blocks.OAK_PLANKS);
            ui.bigWindow();

            MaterialListSchematic list = context.computeOnClient(c -> {
                MaterialListSchematic l = new MaterialListSchematic(schematic, true);
                DataManager.setMaterialList(l);
                c.setScreen(new GuiMaterialList(l));
                return l;
            });
            context.waitTicks(10);
            ui.shot("material-list");
            int planks = count(context, list, Items.OAK_PLANKS);

            // 什么都不选直接确定：选中的是它自己，什么都不变
            boolean editedBefore = context.computeOnClient(c -> schematic.getMetadata().wasModifiedSinceSaved());
            clickReplace(context, Items.OAK_PLANKS);
            context.waitFor(c -> c.screen instanceof ReplaceBlockScreen, 40);
            GT.clickButton(context, "OK");
            context.waitTicks(5);
            ui.shot("same-block-ok");
            check(context, list, Items.OAK_PLANKS, planks);
            if (context.computeOnClient(c -> schematic.getMetadata().wasModifiedSinceSaved()) != editedBefore)
                problems.add("replacing a block with itself marked the schematic as edited");

            // 木板 → 云杉木板：用鼠标点“替换”，搜索，点格子，确定
            clickReplace(context, Items.OAK_PLANKS);
            context.waitFor(c -> c.screen instanceof ReplaceBlockScreen, 40);
            ui.shot("dialog");
            context.getInput().typeChars("spruce_planks");
            context.waitTicks(3);
            ui.shot("dialog-search");
            clickFirstCell(context, ui);
            ui.shot("dialog-picked");
            GT.clickButton(context, "OK");
            context.waitTicks(5);
            ui.shot("after-planks");
            check(context, list, Items.OAK_PLANKS, 0);
            check(context, list, Items.SPRUCE_PLANKS, planks);
            GT.waitSchematicBlock(context, A_MIN, Blocks.SPRUCE_PLANKS);
            if (!context.computeOnClient(c -> schematic.getMetadata().wasModifiedSinceSaved())) problems.add("schematic not marked as edited");

            // 其余材料直接在对话框里选（同一个界面，用 select() 选中避免依赖网格位置）
            replaceVia(context, ui, list, Items.OAK_STAIRS, Blocks.STONE_BRICK_STAIRS);
            replaceVia(context, ui, list, Items.OAK_SLAB, Blocks.STONE_SLAB);
            replaceVia(context, ui, list, Items.OAK_SIGN, Blocks.SPRUCE_SIGN);
            replaceVia(context, ui, list, Items.TORCH, Blocks.SOUL_TORCH);
            ui.shot("after-all");

            // 检查投影里每个方块（两个区域）
            Map<BlockPos, BlockState> expected = expectedAfter(layout);
            List<String> wrong = context.computeOnClient(c -> compare(schematic, expected));
            problems.addAll(wrong);
            // 告示牌文字
            String signNbt = context.computeOnClient(c -> String.valueOf(schematic.getBlockEntityMapForRegion("a").get(new BlockPos(5, 1, 1))));
            if (!signNbt.contains("sign 305")) problems.add("standing sign text lost: " + signNbt);
            String wallNbt = context.computeOnClient(c -> String.valueOf(schematic.getBlockEntityMapForRegion("a").get(new BlockPos(6, 1, 1))));
            if (!wallNbt.contains("sign 306")) problems.add("wall sign text lost: " + wallNbt);

            // 保存后从文件读回：与内存中一致
            boolean saved = context.computeOnClient(c -> SchematicEditSaver.saveOverwrite(placement));
            if (!saved) problems.add("could not save the edited schematic");
            Path dir = context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());
            List<String> reloaded = context.computeOnClient(c -> {
                LitematicaSchematic again = LitematicaSchematic.createFromFile(dir, "ale_replace.litematic");
                if (again == null) return List.of("could not read the saved file");
                List<String> out = compare(again, expected);
                String nbt = String.valueOf(again.getBlockEntityMapForRegion("a").get(new BlockPos(5, 1, 1)));
                if (!nbt.contains("sign 305")) out.add("sign text missing after reload: " + nbt);
                return out;
            });
            for (String r : reloaded) problems.add("after reload: " + r);

            for (String p : problems) GT.log("[replace] PROBLEM " + p);
            if (!problems.isEmpty()) throw new AssertionError("[replace] " + problems);
            GT.log("[replace] OK: Replace buttons, dialog, states kept, sign/torch forms, sign text, list and render updated, saved and reloaded identical");
        } finally {
            GT.removeAllPlacements(context);
            context.runOnClient(c -> c.setScreen(null));
            ui.normalWindow(oldScale);
        }
    }

    private static Map<BlockPos, BlockState> layout() {
        Map<BlockPos, BlockState> m = new LinkedHashMap<>();
        for (int x = 0; x <= 9; x++) for (int z = 0; z <= 2; z++) m.put(A_MIN.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
        m.put(A_MIN.offset(0, 1, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
        m.put(A_MIN.offset(1, 1, 0), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.SHAPE, StairsShape.OUTER_LEFT).setValue(BlockStateProperties.WATERLOGGED, true));
        m.put(A_MIN.offset(2, 1, 0), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        m.put(A_MIN.offset(3, 1, 0), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE));
        m.put(A_MIN.offset(4, 1, 0), Blocks.STONE.defaultBlockState());
        m.put(A_MIN.offset(4, 1, 1), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
        m.put(A_MIN.offset(5, 1, 1), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 6));
        m.put(A_MIN.offset(6, 1, 0), Blocks.STONE.defaultBlockState());
        m.put(A_MIN.offset(6, 1, 1), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH)
                .setValue(BlockStateProperties.WATERLOGGED, true));
        m.put(A_MIN.offset(7, 1, 1), Blocks.TORCH.defaultBlockState());
        // 第二个区域：也有木板和楼梯
        for (int x = 0; x <= 3; x++) m.put(B_MIN.offset(x, 0, 0), Blocks.OAK_PLANKS.defaultBlockState());
        m.put(B_MIN.offset(0, 1, 1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        return m;
    }

    /** 替换后的样子：同一形态的新方块，状态属性照抄 */
    private static Map<BlockPos, BlockState> expectedAfter(Map<BlockPos, BlockState> before) {
        Map<Block, Block> map = Map.of(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.OAK_STAIRS, Blocks.STONE_BRICK_STAIRS,
                Blocks.OAK_SLAB, Blocks.STONE_SLAB, Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.OAK_WALL_SIGN, Blocks.SPRUCE_WALL_SIGN,
                Blocks.TORCH, Blocks.SOUL_TORCH, Blocks.WALL_TORCH, Blocks.SOUL_WALL_TORCH);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (var e : before.entrySet()) {
            BlockState s = e.getValue();
            Block to = map.get(s.getBlock());
            if (to != null) {
                BlockState n = to.defaultBlockState();
                for (var p : s.getProperties()) n = copy(s, n, p);
                s = n;
            }
            out.put(e.getKey(), s);
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState from, BlockState to, net.minecraft.world.level.block.state.properties.Property p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }

    private static List<String> compare(LitematicaSchematic schematic, Map<BlockPos, BlockState> expected) {
        List<String> out = new ArrayList<>();
        for (var e : expected.entrySet()) {
            BlockPos pos = e.getKey();
            boolean inA = pos.getZ() <= A_MAX.getZ();
            String region = inA ? "a" : "b";
            BlockPos rel = pos.subtract(inA ? A_MIN : B_MIN);
            LitematicaBlockStateContainer c = schematic.getSubRegionContainer(region);
            BlockState got = c == null ? null : c.get(rel.getX(), rel.getY(), rel.getZ());
            if (got != e.getValue()) out.add(region + " " + rel.toShortString() + " is " + got + ", expected " + e.getValue());
        }
        return out;
    }

    /** 两个区域的投影：a（主要的一排）和 b（另一块木板和楼梯） */
    private static SchematicPlacement captureTwoRegions(ClientGameTestContext context, TestSingleplayerContext sp, String name) {
        Path dir = context.computeOnClient(c -> DataManager.getSchematicsBaseDirectory());
        boolean written = sp.getServer().computeOnServer(server -> {
            var area = new fi.dy.masa.litematica.selection.AreaSelection();
            area.setName(name);
            area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(A_MIN, A_MAX, "a"), false);
            area.addSubRegionBox(new fi.dy.masa.litematica.selection.Box(B_MIN, B_MAX, "b"), false);
            area.setExplicitOrigin(A_MIN);
            var schematic = LitematicaSchematic.createFromWorld(server.overworld(), area,
                    new LitematicaSchematic.SchematicSaveInfo(false, false), "ALE", s -> GT.log("capture: " + s));
            return schematic != null && schematic.writeToFile(dir, name, true);
        });
        if (!written) throw new AssertionError("could not write " + name);
        return context.computeOnClient(c -> {
            LitematicaSchematic loaded = LitematicaSchematic.createFromFile(dir, name + ".litematic");
            if (loaded == null) throw new AssertionError("could not read " + name);
            SchematicPlacement placement = SchematicPlacement.createFor(loaded, A_MIN, name, true, true);
            DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
            DataManager.getSchematicPlacementManager().setSelectedSchematicPlacement(placement);
            return placement;
        });
    }

    private void replaceVia(ClientGameTestContext context, UiDriver ui, MaterialListBase list, Item material, Block target) {
        int before = count(context, list, material);
        clickReplace(context, material);
        context.waitFor(c -> c.screen instanceof ReplaceBlockScreen, 40);
        context.runOnClient(c -> ((ReplaceBlockScreen) c.screen).select(target));
        GT.clickButton(context, "OK");
        context.waitTicks(5);
        String screen = context.computeOnClient(c -> c.screen == null ? "none" : c.screen.getClass().getSimpleName());
        if (!"GuiMaterialList".equals(screen)) {
            ui.shot("unexpected-screen-" + screen);
            throw new AssertionError("[replace] after replacing " + material + " the screen is " + screen);
        }
        check(context, list, material, 0);
        Item targetItem = target.asItem();
        int now = count(context, list, targetItem);
        GT.log("[replace] " + material + " (" + before + ") -> " + target + ": list now has " + now + " x " + targetItem);
        if (now < before) problems.add("after replacing " + material + " the list has only " + now + " x " + targetItem);
    }

    private static int count(ClientGameTestContext context, MaterialListBase list, Item item) {
        return context.computeOnClient(c -> {
            int n = 0;
            for (MaterialListEntry e : list.getMaterialsAll()) if (e.getStack().is(item)) n += e.getCountTotal();
            return n;
        });
    }

    private void check(ClientGameTestContext context, MaterialListBase list, Item item, int want) {
        int got = count(context, list, item);
        if (got != want) problems.add("material list has " + got + " x " + item + ", expected " + want);
    }

    private static void clickFirstCell(ClientGameTestContext context, UiDriver ui) {
        int[] g = context.computeOnClient(c -> ((ReplaceBlockScreen) c.screen).gridGeometry());
        ui.click(g[0] + g[2] / 2.0, g[1] + g[2] / 2.0, 0);
    }

    /** 在材料列表里找到某物品那一行的“替换”按钮并用鼠标点击（也检查每行只有一个“替换”） */
    static void clickReplace(ClientGameTestContext context, Item item) {
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
                        double[] found = null;
                        int replaceButtons = 0;
                        for (Object o : (List<?>) sub.get(w)) {
                            if (o instanceof ButtonBase b && String.valueOf(ds.get(b)).contains("Replace")) {
                                replaceButtons++;
                                double scale = c.getWindow().getGuiScale();
                                found = new double[]{(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
                            }
                        }
                        if (replaceButtons != 1) throw new AssertionError(replaceButtons + " Replace buttons in the row of " + item);
                        if (found != null) return found;
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            throw new AssertionError("Replace button for " + item + " not found");
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTick();
    }
}
