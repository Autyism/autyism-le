package com.autyism.ale.browser;

import com.autyism.ale.AleMod;
import com.autyism.ale.config.AleConfigs;
import com.autyism.ale.gui.GuiBlockPicker;
import com.autyism.ale.preview.Previews;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/**
 * 右键条目图标打开的小对话框：输入物品 ID（或者用方块 / 物品选择界面挑一个，留空 = 默认图标），
 * 选择图标的位置。位置改了马上能在后面的列表里看到；“取消”会恢复原样，“重置”回到默认图标。
 */
public class FolderIconScreen extends GuiBase implements Previews.PreviewOwnedScreen {
    private static final int W = 260;
    private static final int H = 112;
    /** 文字框宽度（右边留出物品小图和“选择…”按钮） */
    private static final int FIELD_W = W - 20 - 64 - 20;

    private final Path path;
    private final String name;
    @Nullable
    private final FolderIcons.Choice original;
    private FolderIcons.Placement placement;
    private String text;
    /** 物品选择界面用的临时列表：选完后取最后一个 */
    private final ConfigStringList pickList = new ConfigStringList("iconItem", ImmutableList.of()).apply("autyism-le.config");
    private boolean pickerOpen;
    private GuiTextFieldGeneric field;
    private int left, top;

    public FolderIconScreen(Path path, String name, boolean directory, @Nullable Screen parent) {
        this.path = path;
        this.name = name;
        this.original = FolderIcons.get(path);
        this.placement = this.original != null ? this.original.placement() : FolderIcons.Placement.SMALL;
        this.text = this.original != null ? this.original.itemId() : "";
        this.title = StringUtils.translate(directory ? "autyism-le.gui.title.folder_icon" : "autyism-le.gui.title.schematic_icon", name);
        this.useTitleHierarchy = false;
        this.setParent(parent);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (this.pickerOpen) {
            // 从物品选择界面回来：用最后选的那个
            this.pickerOpen = false;
            List<String> picked = this.pickList.getStrings();
            if (!picked.isEmpty()) this.text = picked.get(picked.size() - 1).trim();
        }
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        int x = this.left + 10, y = this.top + 30;

        this.field = new GuiTextFieldGeneric(x, y, FIELD_W, 18, this.font);
        this.field.setValueWrapper(this.text);
        this.field.setFocused(true);
        this.addTextField(this.field, f -> {
            this.text = f.getValueWrapper();
            return true;
        });
        ButtonGeneric pick = new ButtonGeneric(x + W - 20 - 60, y - 1, 60, 20, StringUtils.translate("autyism-le.gui.button.pick_item"));
        pick.setHoverStrings(StringUtils.translate("autyism-le.gui.button.pick_item.hover"));
        this.addButton(pick, (b, mb) -> openPicker());

        y += 26;
        ButtonGeneric place = new ButtonGeneric(x, y, W - 20, 20, this.placement.displayName());
        place.setHoverStrings(StringUtils.translate("autyism-le.gui.button.icon_placement.hover").split("\n"));
        this.addButton(place, (b, mb) -> {
            this.placement = this.placement.next();
            preview();
            this.initGui();
        });

        y += 26;
        int bw = (W - 20 - 8) / 3;
        this.addButton(new ButtonGeneric(x, y, bw, 20, StringUtils.translate("autyism-le.gui.button.ok")), (b, mb) -> confirm());
        ButtonGeneric reset = new ButtonGeneric(x + bw + 4, y, bw, 20, StringUtils.translate("autyism-le.gui.button.reset_icon"));
        reset.setHoverStrings(StringUtils.translate("autyism-le.gui.button.reset_icon.hover"));
        this.addButton(reset, (b, mb) -> {
            FolderIcons.set(this.path, null);
            AleConfigs.saveNow();
            this.closeGui(true);
        });
        this.addButton(new ButtonGeneric(x + 2 * (bw + 4), y, bw, 20, StringUtils.translate("autyism-le.gui.button.cancel")), (b, mb) -> cancel());
    }

    /** 位置改动先显示出来（物品要点“确定”才用） */
    private void preview() {
        String item = this.original != null ? this.original.itemId() : "";
        FolderIcons.set(this.path, new FolderIcons.Choice(item, this.placement));
    }

    private void openPicker() {
        this.pickList.setStrings(this.text.isBlank() ? List.of() : List.of(this.text.trim()));
        this.pickerOpen = true;
        GuiBase.openGui(new GuiBlockPicker(this.pickList, AleMod.MOD_ID, GuiBlockPicker.Mode.ITEM, this));
    }

    private void confirm() {
        String id = this.text.trim();
        if (!FolderIcons.isValidItem(id)) {
            this.addMessage(fi.dy.masa.malilib.gui.Message.MessageType.ERROR, "autyism-le.message.icon.invalid_item", id);
            return;
        }
        FolderIcons.set(this.path, new FolderIcons.Choice(id, this.placement));
        AleConfigs.saveNow();
        this.closeGui(true);
    }

    private void cancel() {
        FolderIcons.set(this.path, this.original);
        this.closeGui(true);
    }

    @Override
    public boolean onKeyTyped(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        return super.onKeyTyped(event);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        // 后面的浏览器照常画（可以看到改动），对话框画在上面
        if (this.getParent() != null) this.getParent().render(g, -1, -1, partialTicks);
        super.render(g, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawScreenBackground(GuiContext ctx, int mouseX, int mouseY) {
        RenderUtils.drawRect(ctx, 0, 0, this.width, this.height, 0x60000000);
        RenderUtils.drawOutlinedBox(ctx, this.left, this.top, W, H, 0xF0101010, 0xFF999999);
    }

    @Override
    protected void drawTitle(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        String t = this.getTitleString();
        if (this.font.width(t) > W - 20) t = this.font.plainSubstrByWidth(t, W - 26) + "...";
        this.drawStringWithShadow(ctx, t, this.left + 10, this.top + 7, COLOR_WHITE);
        this.drawString(ctx, StringUtils.translate("autyism-le.gui.label.icon_hint"), this.left + 10, this.top + 18, 0xFFA0A0A0);
    }

    @Override
    protected void drawContents(GuiContext ctx, int mouseX, int mouseY, float partialTicks) {
        // 文字框右边显示输入的物品是什么
        ItemStack stack = FolderIcons.stackOf(this.text);
        if (stack != null) ctx.renderItem(stack, this.left + 10 + FIELD_W + 3, this.top + 31);
    }
}
