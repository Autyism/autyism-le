package com.autyism.ale.verifier;

import com.autyism.ale.mixin.malilib.GuiBaseAccessor;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.config.Hotkeys;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier;
import fi.dy.masa.litematica.gui.GuiSchematicVerifier.BlockMismatchEntry;
import fi.dy.masa.litematica.gui.widgets.WidgetListSchematicVerificationResults;
import fi.dy.masa.litematica.gui.widgets.WidgetSchematicVerificationResult;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.BlockMismatch;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier.MismatchType;
import fi.dy.masa.litematica.schematic.verifier.VerifierResultSorter;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.GuiUtils;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 投影验证器结果列表里的“容器内容不同”一类：标题、按容器种类分的条目、选中 / 忽略、筛选按钮、悬停说明和信息栏。
 * 和 Litematica 自己的几类一样：只有选中这一类或其中一条时，世界里才框出这些容器。
 */
public final class ContainerVerifierGui {
    /** 列表和信息栏里这一类的文字颜色（世界里的框见 ContainerVerifier.BOX_COLOR） */
    public static final String TEXT_COLOR = GuiBase.TXT_BLUE;

    /** 只看“容器内容不同”（结果列表上方的筛选按钮）；和 Litematica 的筛选一样，关掉界面后记住 */
    public static boolean contentsMode;

    private ContainerVerifierGui() {
    }

    public static String categoryName() {
        return StringUtils.translate("autyism-le.gui.label.schematic_verifier.wrong_contents");
    }

    // ---------------- 列表条目 ----------------

    /** 类别标题 */
    public static final class CategoryEntry extends BlockMismatchEntry {
        public CategoryEntry() {
            super((MismatchType) null, TEXT_COLOR + GuiBase.TXT_BOLD + categoryName() + GuiBase.TXT_RST);
        }
    }

    // 一种容器：数量和代表状态放在 BlockMismatch 里（排序、列宽用 Litematica 原来的逻辑）
    //? if >=26.2 {
    /*// 26.2 起 BlockMismatch 是 record，不能继承：照样用它，记下哪几个是这一类的（预期和实际一样的错误状态 Litematica 自己不会有）
    private static final java.util.Set<BlockMismatch> OURS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public static BlockMismatch containerMismatch(Block block, int count) {
        if (OURS.size() > 4096) OURS.clear();
        BlockMismatch m = new BlockMismatch(MismatchType.WRONG_STATE, block.defaultBlockState(), block.defaultBlockState(), count);
        OURS.add(m);
        return m;
    }

    public static boolean isContainerMismatch(BlockMismatch m) {
        return m != null && OURS.contains(m);
    }

    public static net.minecraft.world.level.block.state.BlockState expectedOf(BlockMismatch m) {
        return m.stateExpected();
    }

    public static int countOf(BlockMismatch m) {
        return m.count();
    }
    *///?} else {
    public static final class ContainerMismatch extends BlockMismatch {
        public ContainerMismatch(Block block, int count) {
            super(MismatchType.WRONG_STATE, block.defaultBlockState(), block.defaultBlockState(), count);
        }
    }

    public static BlockMismatch containerMismatch(Block block, int count) {
        return new ContainerMismatch(block, count);
    }

    public static boolean isContainerMismatch(BlockMismatch m) {
        return m instanceof ContainerMismatch;
    }

    public static net.minecraft.world.level.block.state.BlockState expectedOf(BlockMismatch m) {
        return m.stateExpected;
    }

    public static int countOf(BlockMismatch m) {
        return m.count;
    }
    //?}

    public static final class DataEntry extends BlockMismatchEntry {
        public final BlockMismatch mismatch;
        public final Block block;

        public DataEntry(BlockMismatch mismatch) {
            super(MismatchType.WRONG_STATE, mismatch);
            this.mismatch = mismatch;
            this.block = expectedOf(mismatch).getBlock();
        }
    }

    public static boolean isOurs(BlockMismatchEntry entry) {
        return entry instanceof CategoryEntry || entry instanceof DataEntry;
    }

    /**
     * 往结果列表里加这一类（在 Litematica 填好列表之后）。“全部”时接在最后；只看这一类时换掉 Litematica 的内容
     */
    public static void addEntries(List<BlockMismatchEntry> list, GuiSchematicVerifier gui) {
        SchematicVerifier verifier = gui.getPlacement().getSchematicVerifier();
        if (contentsMode) {
            list.clear();
        } else if (gui.getResultMode() != MismatchType.ALL) {
            return;
        }
        Map<Block, Integer> overview = ContainerVerifier.overview(verifier);
        if (overview.isEmpty() && !contentsMode) return;
        List<BlockMismatchEntry> ours = new ArrayList<>();
        ours.add(new CategoryEntry());
        List<BlockMismatch> rows = new ArrayList<>();
        for (var e : overview.entrySet()) rows.add(containerMismatch(e.getKey(), e.getValue()));
        rows.sort(new VerifierResultSorter(verifier));
        for (BlockMismatch m : rows) ours.add(new DataEntry(m));
        // “全部”里排在“错误状态”后面（“多余方块”、“缺失方块”往往很长，放在它们后面要翻很久）
        int at = list.size();
        for (int i = 0; i < list.size(); i++) {
            BlockMismatchEntry e = list.get(i);
            if (e.type == BlockMismatchEntry.Type.CATEGORY_TITLE && (e.mismatchType == MismatchType.EXTRA || e.mismatchType == MismatchType.MISSING)) {
                at = i;
                break;
            }
        }
        list.addAll(at, ours);
    }

    /** 列表里点了一条。是这一类的条目时处理并返回 true（Litematica 不再处理） */
    public static boolean onSelection(GuiSchematicVerifier gui, BlockMismatchEntry entry) {
        if (!isOurs(entry)) return false;
        SchematicVerifier verifier = gui.getPlacement().getSchematicVerifier();
        if (entry instanceof DataEntry data) ContainerVerifier.toggleEntry(verifier, data.block);
        else ContainerVerifier.toggleCategory(verifier);
        ((com.autyism.ale.mixin.litematica.SchematicVerifierInvoker) verifier).ale$updateMismatchOverlays();
        if (!Configs.InfoOverlays.VERIFIER_OVERLAY_ENABLED.getBooleanValue()) {
            String name = Configs.InfoOverlays.VERIFIER_OVERLAY_ENABLED.getName();
            String hotkeyName = Hotkeys.TOGGLE_VERIFIER_OVERLAY_RENDERING.getName();
            String hotkeyVal = Hotkeys.TOGGLE_VERIFIER_OVERLAY_RENDERING.getKeybind().getKeysDisplayString();
            InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING, "litematica.message.warn.schematic_verifier.overlay_disabled", name, hotkeyName, hotkeyVal);
        }
        return true;
    }

    /** 忽略一条（结果列表里的“忽略”按钮） */
    public static boolean onIgnore(SchematicVerifier verifier, BlockMismatch mismatch) {
        if (!isContainerMismatch(mismatch)) return false;
        ContainerVerifier.ignore(verifier, expectedOf(mismatch).getBlock());
        ((com.autyism.ale.mixin.litematica.SchematicVerifierInvoker) verifier).ale$updateMismatchOverlays();
        return true;
    }

    // ---------------- 筛选按钮 ----------------

    /** 在 Litematica 的筛选按钮（全部、错误方块……）那一行最后加“容器内容不同” */
    public static void addModeButton(GuiSchematicVerifier gui) {
        List<ButtonBase> buttons = ((GuiBaseAccessor) gui).ale$getButtons();
        int rowY = 42;
        int x = 12;
        String allLabel = Configs.Generic.ENABLE_DIFFERENT_BLOCKS.getBooleanValue() ? MismatchType.ALL.getDisplayname()
                : StringUtils.translate("litematica.gui.label.schematic_verifier_display_type.all_not_ignored");
        String currentLabel = gui.getResultMode() == MismatchType.ALL ? allLabel : gui.getResultMode().getDisplayname();
        for (ButtonBase b : buttons) {
            if (b.getY() != rowY) continue;
            x = Math.max(x, b.getX() + b.getWidth() + 4);
            // 只看这一类时，Litematica 当前的筛选按钮不算选中，可以点回去
            if (contentsMode && currentLabel.equals(((com.autyism.ale.mixin.malilib.ButtonBaseAccessor) b).ale$getDisplayString())) b.setEnabled(true);
        }
        String label = categoryName();
        ButtonGeneric button = new ButtonGeneric(x, rowY, Minecraft.getInstance().font.width(label) + 10, 20, label);
        button.setEnabled(!contentsMode);
        button.setActionListener((btn, mouseButton) -> {
            contentsMode = true;
            gui.initGui();
        });
        buttons.add(button);
        // “高亮告示牌”开关（见 SignHighlight）
        String signs = StringUtils.translate(SignHighlight.enabled ? "autyism-le.gui.button.schematic_verifier.signs_on"
                : "autyism-le.gui.button.schematic_verifier.signs_off");
        ButtonGeneric signButton = new ButtonGeneric(x + button.getWidth() + 4, rowY, Minecraft.getInstance().font.width(signs) + 10, 20, signs);
        signButton.setHoverStrings(StringUtils.translate("autyism-le.gui.hover.schematic_verifier.signs"));
        signButton.setActionListener((btn, mouseButton) -> {
            SignHighlight.toggle();
            gui.initGui();
        });
        buttons.add(signButton);
        // “重置忽略”：忽略了容器种类时也能点
        if (ContainerVerifier.hasIgnored(gui.getPlacement().getSchematicVerifier())) {
            String reset = StringUtils.translate("litematica.gui.button.schematic_verifier.reset_ignored");
            for (ButtonBase b : buttons) {
                if (reset.equals(((com.autyism.ale.mixin.malilib.ButtonBaseAccessor) b).ale$getDisplayString())) b.setEnabled(true);
            }
        }
    }

    /** 结果列表两列文字的最小宽度：{容器名字, “内容不同”} */
    public static int[] columnWidths() {
        int expected = 0;
        for (Block block : ContainerVerifier.knownBlocks()) {
            String name = WidgetSchematicVerificationResult.BlockMismatchInfo.getDisplayName(block.defaultBlockState(), new ItemStack(block));
            expected = Math.max(expected, StringUtils.getStringWidth(name));
        }
        int found = StringUtils.getStringWidth(StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_differ"));
        return new int[]{expected, found};
    }

    // ---------------- 信息栏（HUD）里的位置列表 ----------------

    /** 选中了这一类 / 条目时，在验证器的信息栏位置列表后面加上最近的几个容器 */
    public static void appendHudLines(SchematicVerifier verifier, List<String> lines) {
        if (!ContainerVerifier.hasSelection(verifier)) return;
        int max = Configs.InfoOverlays.INFO_HUD_MAX_LINES.getIntegerValue();
        List<BlockPos> positions = ContainerVerifier.renderPositions(verifier, Configs.InfoOverlays.VERIFIER_ERROR_HILIGHT_MAX_POSITIONS.getIntegerValue());
        if (positions.isEmpty()) return;
        // Litematica 的位置行数（第一行是标题）
        int used = Math.max(0, lines.size() - 1);
        int room = Math.max(0, max - used);
        if (room == 0) return;
        lines.add(TEXT_COLOR + GuiBase.TXT_BOLD + categoryName() + GuiBase.TXT_RST);
        for (int i = 0; i < Math.min(room, positions.size()); i++) {
            BlockPos pos = positions.get(i);
            lines.add(String.format("%sx: %5d, y: %3d, z: %5d%s", TEXT_COLOR, pos.getX(), pos.getY(), pos.getZ(), GuiBase.TXT_RST));
        }
    }

    // ---------------- 列表里的一行 ----------------

    /** 这一类的标题和条目。标题照 Litematica 的样子画；条目的“实际”一栏写“内容不同”，悬停时列出最近几个容器差了什么 */
    public static final class ResultWidget extends WidgetSchematicVerificationResult {
        private static final int TOOLTIP_POSITIONS = 8;
        private static final int TOOLTIP_ITEMS = 4;
        private final SchematicVerifier verifier;
        private final BlockMismatchEntry myEntry;
        private final boolean odd;

        public ResultWidget(int x, int y, int width, int height, boolean isOdd, WidgetListSchematicVerificationResults listWidget,
                            GuiSchematicVerifier gui, BlockMismatchEntry entry, int listIndex) {
            super(x, y, width, height, isOdd, listWidget, gui, entry, listIndex);
            this.verifier = gui.getPlacement().getSchematicVerifier();
            this.myEntry = entry;
            this.odd = isOdd;
        }

        @Override
        protected boolean shouldRenderAsSelected() {
            if (this.myEntry instanceof DataEntry data) return ContainerVerifier.isEntrySelected(this.verifier, data.block);
            return ContainerVerifier.isCategorySelected(this.verifier);
        }

        @Override
        //? if >=1.21.6 {
        public void render(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        //?} else
        //public void render(int mouseX, int mouseY, boolean selected, GuiContext ctx) {
            if (!(this.myEntry instanceof DataEntry data)) {
                //? if >=1.21.6 {
                super.render(ctx, mouseX, mouseY, selected);
                //?} else
                //super.render(mouseX, mouseY, selected, ctx);
                return;
            }
            selected = this.shouldRenderAsSelected();
            int color = selected ? 0xA0707070 : this.isMouseOver(mouseX, mouseY) ? 0xA0505050 : this.odd ? 0xA0101010 : 0xA0303030;
            RenderUtils.drawRect(ctx, this.x, this.y, this.width, this.height, color);
            if (selected) RenderUtils.drawOutline(ctx, this.x, this.y, this.width, this.height, 0xFFE0E0E0);
            int x1 = this.getColumnPosX(0), x2 = this.getColumnPosX(1), x3 = this.getColumnPosX(2);
            ItemStack icon = new ItemStack(data.block);
            String name = WidgetSchematicVerificationResult.BlockMismatchInfo.getDisplayName(expectedOf(data.mismatch), icon);
            //? if >=1.21.6 {
            this.drawString(ctx, x1 + 20, this.y + 7, 0xFFFFFFFF, name);
            //?} else
            //this.drawString(x1 + 20, this.y + 7, 0xFFFFFFFF, name, ctx);
            //? if >=1.21.6 {
            this.drawString(ctx, x2 + 20, this.y + 7, 0xFFFFFFFF, TEXT_COLOR + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_differ") + GuiBase.TXT_RST);
            //?} else
            //this.drawString(x2 + 20, this.y + 7, 0xFFFFFFFF, TEXT_COLOR + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_differ") + GuiBase.TXT_RST, ctx);
            //? if >=1.21.6 {
            this.drawString(ctx, x3, this.y + 7, 0xFFFFFFFF, String.valueOf(countOf(data.mismatch)));
            //?} else
            //this.drawString(x3, this.y + 7, 0xFFFFFFFF, String.valueOf(countOf(data.mismatch)), ctx);
            RenderUtils.drawRect(ctx, x1, this.y + 3, 16, 16, 0x20FFFFFF);
            RenderUtils.drawRect(ctx, x2, this.y + 3, 16, 16, 0x20FFFFFF);
            ctx.renderItem(icon, x1, this.y + 3);
            ctx.renderItem(icon, x2, this.y + 3);
            //? if >=1.21.6 {
            this.drawSubWidgets(ctx, mouseX, mouseY);
            //?} else
            //this.drawSubWidgets(mouseX, mouseY, ctx);
        }

        @Override
        //? if >=1.21.6 {
        public void postRenderHovered(GuiContext ctx, int mouseX, int mouseY, boolean selected) {
        //?} else
        //public void postRenderHovered(int mouseX, int mouseY, boolean selected, GuiContext ctx) {
            if (!(this.myEntry instanceof DataEntry data)) {
                //? if >=1.21.6 {
                super.postRenderHovered(ctx, mouseX, mouseY, selected);
                //?} else
                //super.postRenderHovered(mouseX, mouseY, selected, ctx);
                return;
            }
            boolean overButton = false;
            for (WidgetBase w : this.subWidgets) overButton |= w.isMouseOver(mouseX, mouseY);
            if (!overButton) renderTooltip(ctx, mouseX, mouseY, data.block);
            //? if >=1.21.6 {
            this.drawHoveredSubWidget(ctx, mouseX, mouseY);
            //?} else
            //this.drawHoveredSubWidget(mouseX, mouseY, ctx);
        }

        /** 最近的几个这种容器，每个写出差了什么（少了 / 多了哪些物品） */
        private void renderTooltip(GuiContext ctx, int mouseX, int mouseY, Block block) {
            var font = Minecraft.getInstance().font;
            List<String> lines = new ArrayList<>();
            lines.add(GuiBase.TXT_BOLD + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_tooltip_title") + GuiBase.TXT_RST);
            List<BlockPos> all = new ArrayList<>();
            for (BlockPos pos : ContainerVerifier.getMismatches(this.verifier)) {
                ContainerVerifier.Detail d = ContainerVerifier.getDetail(this.verifier, pos);
                if (d != null && d.state().getBlock() == block) all.add(pos);
            }
            if (Minecraft.getInstance().player != null) {
                BlockPos center = Minecraft.getInstance().player.blockPosition();
                all.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(center)));
            }
            for (int i = 0; i < Math.min(TOOLTIP_POSITIONS, all.size()); i++) {
                BlockPos pos = all.get(i);
                ContainerVerifier.Detail d = ContainerVerifier.getDetail(this.verifier, pos);
                if (d == null) continue;
                lines.add(String.format("%s%d, %d, %d%s", GuiBase.TXT_GRAY, pos.getX(), pos.getY(), pos.getZ(), GuiBase.TXT_RST));
                List<Map.Entry<ItemStack, Integer>> diff = ContainerVerifier.difference(d);
                for (int j = 0; j < Math.min(TOOLTIP_ITEMS, diff.size()); j++) {
                    var e = diff.get(j);
                    String item = e.getKey().getHoverName().getString();
                    if (e.getValue() == 0) {
                        lines.add("  " + GuiBase.TXT_LIGHT_PURPLE + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_wrong_slot", item) + GuiBase.TXT_RST);
                        continue;
                    }
                    lines.add(e.getValue() > 0
                            ? "  " + GuiBase.TXT_RED + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_missing", e.getValue(), item) + GuiBase.TXT_RST
                            : "  " + GuiBase.TXT_GOLD + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_extra", -e.getValue(), item) + GuiBase.TXT_RST);
                }
                if (diff.size() > TOOLTIP_ITEMS) lines.add("  " + StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_more", diff.size() - TOOLTIP_ITEMS));
            }
            if (all.size() > TOOLTIP_POSITIONS) lines.add(StringUtils.translate("autyism-le.gui.label.schematic_verifier.contents_more_containers", all.size() - TOOLTIP_POSITIONS));
            int width = 0;
            for (String l : lines) width = Math.max(width, font.width(l));
            width += 12;
            int height = lines.size() * (font.lineHeight + 2) + 8;
            int x = mouseX + 10, y = mouseY;
            if (x + width > GuiUtils.getCurrentScreenWidth()) x = mouseX - width - 10;
            if (y + height > GuiUtils.getCurrentScreenHeight()) y = Math.max(0, mouseY - height - 2);
            //? if >=1.21.6 {
            fi.dy.masa.litematica.render.RenderUtils.renderBackgroundMask(ctx, x + 1, y + 1, width - 1, height - 1);
            //?} else
            //fi.dy.masa.litematica.render.RenderUtils.renderBackgroundMask(x + 1, y + 1, width - 1, height - 1, ctx);
            RenderUtils.drawOutlinedBox(ctx, x, y, width, height, 0xFF000000, GuiBase.COLOR_HORIZONTAL_BAR);
            int ty = y + 5;
            for (String l : lines) {
                ctx.drawString(font, l, x + 6, ty, 0xFFFFFFFF, false);
                ty += font.lineHeight + 2;
            }
        }
    }
}
