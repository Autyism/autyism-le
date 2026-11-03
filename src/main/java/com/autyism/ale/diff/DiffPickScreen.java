package com.autyism.ale.diff;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiSchematicBrowserBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.interfaces.ISelectionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetFileBrowserBase.DirectoryEntry;
import fi.dy.masa.malilib.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * "Compare with...": Litematica's schematic browser (with ALE's layouts and previews) to pick the other version of a
 * placement's schematic. Compare starts the diff and opens its list.
 */
public class DiffPickScreen extends GuiSchematicBrowserBase implements ISelectionListener<DirectoryEntry> {
    private final SchematicPlacement placement;

    public DiffPickScreen(SchematicPlacement placement) {
        super(12, 24);
        this.placement = placement;
        this.title = StringUtils.translate("autyism-le.gui.title.diff_pick", placement.getName());
    }

    @Override
    public String getBrowserContext() {
        return "autyism_le_schematic_diff";
    }

    @Override
    public Path getDefaultDirectory() {
        return DataManager.getSchematicsBaseDirectory();
    }

    @Override
    public void initGui() {
        super.initGui();
        createButtons();
    }

    private void createButtons() {
        int x = 12, y = this.getScreenHeight() - 26;
        if (this.getListWidget() == null) return;
        DirectoryEntry selected = this.getListWidget().getLastSelectedEntry();
        ButtonGeneric compare = new ButtonGeneric(x, y, -1, 20, StringUtils.translate("autyism-le.gui.button.diff.compare"));
        compare.setEnabled(isSchematic(selected));
        compare.setHoverStrings(StringUtils.translate("autyism-le.gui.button.diff.compare.hover"));
        this.addButton(compare, (b, mb) -> {
            DirectoryEntry entry = this.getListWidget().getLastSelectedEntry();
            if (!isSchematic(entry)) return;
            compareWith(entry.getFullPath());
        });
        x += compare.getWidth() + 4;
        ButtonGeneric cancel = new ButtonGeneric(x, y, -1, 20, StringUtils.translate("autyism-le.gui.button.cancel"));
        this.addButton(cancel, (b, mb) -> this.closeGui(true));
    }

    /** Start the diff with this file and open the list (also used by the tests) */
    public void compareWith(Path file) {
        DiffSession.start(this.placement, file);
        DiffSession.openList(this.getParent());
    }

    private static boolean isSchematic(@Nullable DirectoryEntry entry) {
        return entry != null && entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")
                && java.nio.file.Files.isRegularFile(entry.getFullPath());
    }

    @Override
    public void onSelectionChange(@Nullable DirectoryEntry entry) {
        this.clearButtons();
        this.createButtons();
    }

    @Override
    protected ISelectionListener<DirectoryEntry> getSelectionListener() {
        return this;
    }

    /** Open the picker for a placement, coming back to parent */
    public static void open(SchematicPlacement placement, @Nullable net.minecraft.client.gui.screens.Screen parent) {
        DiffPickScreen screen = new DiffPickScreen(placement);
        screen.setParent(parent);
        GuiBase.openGui(screen);
    }
}
