package com.autyism.ale.gametest;

import com.autyism.ale.verifier.ContainerVerifier;
import com.autyism.ale.verifier.ContainerVerifierGui;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier.BlockMismatchEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import fi.dy.masa.malilib.gui.widgets.WidgetListEntryBase;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 验证器结果列表里的“错误内容物”一类：
 * 不选时世界里不框；选中整类框出全部；选中一条只框这种容器；忽略、重置忽略；“错误内容物”筛选按钮；信息栏；
 * 和 Litematica 自己的分类互不影响。
 */
@SuppressWarnings("UnstableApiUsage")
public final class VerifierContentsGameTest implements FabricClientGameTest {
    private static final BlockPos MIN = new BlockPos(30, 64, 30);
    private static final BlockPos CHEST_A = new BlockPos(31, 64, 31);
    private static final BlockPos CHEST_B = new BlockPos(33, 64, 31);
    private static final BlockPos BARREL = new BlockPos(35, 64, 31);
    private static final BlockPos HOPPER = new BlockPos(37, 64, 31);
    private static final BlockPos STONE = new BlockPos(39, 64, 31);
    private static final BlockPos MAX = new BlockPos(40, 65, 32);

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!GTFilter.enabled("verifiercontents")) return;
        try (TestSingleplayerContext sp = GT.newWorld(context)) {
            GT.clearArena(sp, 24, 24, 46, 40, 72);
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("tp @a 35.5 66 37.5 180 30");
            context.waitFor(c -> c.player != null && Math.abs(c.player.getX() - 35.5) < 0.01, 200);
            context.runOnClient(c -> GT.setGuiHidden(c, true));
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlockAndUpdate(CHEST_A, Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(CHEST_B, Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(BARREL, Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(HOPPER, Blocks.HOPPER.defaultBlockState());
                level.setBlockAndUpdate(STONE, Blocks.STONE.defaultBlockState());
                fill(level, CHEST_A, new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.COAL, 5));
                fill(level, CHEST_B, new ItemStack(Items.DIAMOND, 3));
                fill(level, BARREL, new ItemStack(Items.APPLE, 10));
                fill(level, HOPPER, new ItemStack(Items.WHITE_SHULKER_BOX, 1));
            });
            context.waitTicks(5);
            SchematicPlacement placement = GT.captureAndPlace(context, sp, MIN, MAX, MIN, "ale_verifier_contents");
            GT.waitSchematicBlock(context, CHEST_A, Blocks.CHEST);
            // 世界里：两个箱子和木桶的东西不对，漏斗对；石头换成泥土（Litematica 自己的“错误方块”）
            sp.getServer().runOnServer(s -> {
                ServerLevel level = s.overworld();
                fill(level, CHEST_A, new ItemStack(Items.IRON_INGOT, 30));
                fill(level, CHEST_B);
                fill(level, BARREL, new ItemStack(Items.APPLE, 10), new ItemStack(Items.DIRT, 2));
                level.setBlockAndUpdate(STONE, Blocks.DIRT.defaultBlockState());
            });
            context.waitTicks(5);

            // 验证器要在客户端线程上取（第一次取时会新建，构造时用到 Minecraft 实例）
            SchematicVerifier verifier = context.computeOnClient(c -> placement.getSchematicVerifier());
            context.runOnClient(c -> verifier.startVerification(c.level, SchematicWorldHandler.getSchematicWorld(), placement, () -> {
            }));
            context.waitFor(c -> verifier.isFinished(), 400);
            context.waitTicks(10);
            int count = context.computeOnClient(c -> ContainerVerifier.countMismatches(verifier));
            if (count != 3) throw new AssertionError("[verifiercontents] expected 3 containers with wrong contents, got " + count);

            // 1) 不选：世界里不框
            int unselected = context.computeOnClient(c -> ContainerVerifier.renderPositions(verifier, 1000).size());
            Path shotNone = GT.shot(context, "ale-verifiercontents-unselected");
            int boxNone = countBoxPixels(shotNone);
            if (unselected != 0) throw new AssertionError("[verifiercontents] containers highlighted without being selected: " + unselected);

            // 2) 打开验证器：列表里有“错误内容物”一类，按容器种类分条（箱子 2、木桶 1）
            context.runOnClient(c -> c.setScreen(new GuiSchematicVerifier(placement)));
            context.waitTicks(5);
            List<String> rows = context.computeOnClient(c -> describeRows(c));
            GT.log("[verifiercontents] result list: " + rows);
            if (!rows.contains("category") || !rows.contains("Chest x2") || !rows.contains("Barrel x1") || rows.contains("Hopper x1")) {
                throw new AssertionError("[verifiercontents] category rows wrong: " + rows);
            }
            GT.shot(context, "ale-verifiercontents-list");

            // 3) 点类别标题 → 三个都框出来，信息栏列出它们
            clickRow(context, e -> e instanceof ContainerVerifierGui.CategoryEntry);
            int all = context.computeOnClient(c -> ContainerVerifier.renderPositions(verifier, 1000).size());
            boolean hud = context.computeOnClient(c -> verifier.getText(fi.dy.masa.litematica.render.infohud.RenderPhase.POST).stream()
                    .anyMatch(l -> l.contains(ContainerVerifierGui.categoryName())));
            if (all != 3 || !hud) throw new AssertionError("[verifiercontents] category selection: highlighted=" + all + " hudLines=" + hud);
            GT.shot(context, "ale-verifiercontents-category-selected-gui");
            context.runOnClient(c -> c.setScreen(null));
            context.waitTicks(3);
            Path shotAll = GT.shot(context, "ale-verifiercontents-category-selected");
            int boxAll = countBoxPixels(shotAll);
            GT.log("[verifiercontents] box pixels: unselected=" + boxNone + " category selected=" + boxAll);
            if (boxAll < 300 || boxAll < boxNone * 4 + 200) throw new AssertionError("[verifiercontents] boxes not drawn after selecting: " + boxAll + " vs " + boxNone);

            // 4) 只点“木桶”一条 → 整类取消，只框木桶
            context.runOnClient(c -> c.setScreen(new GuiSchematicVerifier(placement)));
            context.waitTicks(5);
            clickRow(context, e -> e instanceof ContainerVerifierGui.DataEntry d && d.block == Blocks.BARREL);
            List<BlockPos> barrelOnly = context.computeOnClient(c -> ContainerVerifier.renderPositions(verifier, 1000));
            boolean categoryStill = context.computeOnClient(c -> ContainerVerifier.isCategorySelected(verifier));
            if (!barrelOnly.equals(List.of(BARREL)) || categoryStill) {
                throw new AssertionError("[verifiercontents] entry selection: " + barrelOnly + " categoryStillSelected=" + categoryStill);
            }
            // 再点一次 → 取消
            clickRow(context, e -> e instanceof ContainerVerifierGui.DataEntry d && d.block == Blocks.BARREL);
            int none = context.computeOnClient(c -> ContainerVerifier.renderPositions(verifier, 1000).size());
            if (none != 0) throw new AssertionError("[verifiercontents] entry not deselected: " + none);

            // 5) Litematica 自己的“错误方块”一类照常工作，和这一类互不影响
            clickRow(context, e -> e.type == BlockMismatchEntry.Type.CATEGORY_TITLE && e.mismatchType == MismatchType.WRONG_BLOCK);
            List<BlockPos> wrongBlocks = context.computeOnClient(c -> new ArrayList<>(verifier.getSelectedMismatchBlockPositionsForRender()));
            int oursAfter = context.computeOnClient(c -> ContainerVerifier.renderPositions(verifier, 1000).size());
            if (!wrongBlocks.equals(List.of(STONE)) || oursAfter != 0) {
                throw new AssertionError("[verifiercontents] Litematica category: " + wrongBlocks + " ours=" + oursAfter);
            }
            clickRow(context, e -> e.type == BlockMismatchEntry.Type.CATEGORY_TITLE && e.mismatchType == MismatchType.WRONG_BLOCK);

            // 6) 忽略“箱子” → 只剩木桶；重置忽略 → 回来
            context.runOnClient(c -> {
                verifier.ignoreStateMismatch(ContainerVerifierGui.containerMismatch(Blocks.CHEST, 2));
                if (c.screen instanceof GuiSchematicVerifier g) g.initGui();
            });
            context.waitTicks(2);
            int afterIgnore = context.computeOnClient(c -> ContainerVerifier.countMismatches(verifier));
            List<String> rowsIgnored = context.computeOnClient(c -> describeRows(c));
            int litematicaIgnored = context.computeOnClient(c -> verifier.getIgnoredMismatches().size());
            if (afterIgnore != 1 || rowsIgnored.contains("Chest x2") || litematicaIgnored != 0) {
                throw new AssertionError("[verifiercontents] ignore: count=" + afterIgnore + " rows=" + rowsIgnored + " litematicaIgnored=" + litematicaIgnored);
            }
            GT.clickButton(context, "Reset ignored");
            int afterReset = context.computeOnClient(c -> ContainerVerifier.countMismatches(verifier));
            if (afterReset != 3) throw new AssertionError("[verifiercontents] reset ignored: " + afterReset);

            // 7) “Wrong Contents” 筛选按钮：只显示这一类；点 Litematica 的“全部”回去
            GT.clickButton(context, ContainerVerifierGui.categoryName());
            List<String> onlyOurs = context.computeOnClient(c -> describeRows(c));
            GT.shot(context, "ale-verifiercontents-filter");
            if (!onlyOurs.contains("category") || onlyOurs.stream().anyMatch(r -> r.startsWith("litematica:"))) {
                throw new AssertionError("[verifiercontents] filter shows other categories: " + onlyOurs);
            }
            GT.clickButton(context, "All (non-ignored)");
            List<String> back = context.computeOnClient(c -> describeRows(c));
            if (!back.contains("litematica:WRONG_BLOCK") || !back.contains("category")) throw new AssertionError("[verifiercontents] back to all: " + back);
            GT.log("[verifiercontents] OK: no boxes until selected, category / entry selection, ignore + reset, filter button, HUD lines, Litematica categories unaffected");
        } finally {
            ContainerVerifierGui.contentsMode = false;
            GT.removeAllPlacements(context);
            context.runOnClient(c -> {
                c.setScreen(null);
                GT.setGuiHidden(c, false);
            });
        }
    }

    private static void fill(ServerLevel level, BlockPos pos, ItemStack... stacks) {
        if (!(level.getBlockEntity(pos) instanceof Container c)) throw new AssertionError("no container at " + pos);
        c.clearContent();
        for (int i = 0; i < stacks.length; i++) c.setItem(i, stacks[i]);
        c.setChanged();
    }

    /** 当前结果列表的所有行（含翻页才看得到的）：Litematica 的类别标题 “litematica:类型”，ALE 的标题 “category”，ALE 的条目 “方块名 xN” */
    private static List<String> describeRows(net.minecraft.client.Minecraft client) {
        List<String> out = new ArrayList<>();
        for (Object o : listContents(client)) {
            if (!(o instanceof BlockMismatchEntry e)) continue;
            if (e instanceof ContainerVerifierGui.CategoryEntry) out.add("category");
            else if (e instanceof ContainerVerifierGui.DataEntry d) out.add(d.block.getName().getString() + " x" + ContainerVerifierGui.countOf(d.mismatch));
            else if (e.type == BlockMismatchEntry.Type.CATEGORY_TITLE) out.add("litematica:" + e.mismatchType);
        }
        return out;
    }

    private static List<?> listContents(net.minecraft.client.Minecraft client) {
        try {
            java.lang.reflect.Field f = WidgetListBase.class.getDeclaredField("listContents");
            f.setAccessible(true);
            return new ArrayList<>((List<?>) f.get(GT.listWidget(client)));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 把结果列表翻到这一行（列表只为看得到的行建部件） */
    private static void scrollTo(net.minecraft.client.Minecraft client, Predicate<BlockMismatchEntry> which) {
        Object list = GT.listWidget(client);
        List<?> contents = listContents(client);
        int index = -1;
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) instanceof BlockMismatchEntry e && which.test(e)) {
                index = i;
                break;
            }
        }
        if (index < 0) throw new AssertionError("row not in the list: " + describeRows(client));
        try {
            java.lang.reflect.Field bar = WidgetListBase.class.getDeclaredField("scrollBar");
            bar.setAccessible(true);
            ((fi.dy.masa.malilib.gui.GuiScrollBar) bar.get(list)).setValue(index);
            java.lang.reflect.Method m = WidgetListBase.class.getDeclaredMethod("reCreateListEntryWidgets");
            m.setAccessible(true);
            m.invoke(list);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static List<?> entryWidgets(net.minecraft.client.Minecraft client) {
        Object list = GT.listWidget(client);
        try {
            java.lang.reflect.Field f = WidgetListBase.class.getDeclaredField("listWidgets");
            f.setAccessible(true);
            return new ArrayList<>((List<?>) f.get(list));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 像真人一样点结果列表里的一行（点在行的左边，避开“忽略”按钮） */
    private static void clickRow(ClientGameTestContext context, Predicate<BlockMismatchEntry> which) {
        context.runOnClient(c -> scrollTo(c, which));
        context.waitTick();
        double[] pos = context.computeOnClient(c -> {
            for (Object o : entryWidgets(c)) {
                var w = (WidgetListEntryBase<?>) o;
                if (w.getEntry() instanceof BlockMismatchEntry e && which.test(e)) {
                    double scale = c.getWindow().getGuiScale();
                    return new double[]{(w.getX() + 30) * scale, (w.getY() + w.getHeight() / 2.0) * scale};
                }
            }
            throw new AssertionError("row not found in " + describeRows(c));
        });
        context.getInput().setCursorPos(pos[0], pos[1]);
        context.waitTick();
        context.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }

    /** 截图里“错误内容物”框的颜色（0x5A78FF 附近的蓝紫色）的像素数 */
    private static int countBoxPixels(Path png) {
        try {
            BufferedImage img = ImageIO.read(png.toFile());
            int n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    if (b > 200 && Math.abs(r - 0x5A) < 40 && Math.abs(g - 0x78) < 40) n++;
                }
            }
            return n;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
