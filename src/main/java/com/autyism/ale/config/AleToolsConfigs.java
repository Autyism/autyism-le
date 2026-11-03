package com.autyism.ale.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Settings of the "Tools" tab: the wrong block guide (find the next wrong / missing block) and the schematic diff
 * (compare a placement with another version of the schematic). Saved in their own "Tools" section of the config file.
 */
public final class AleToolsConfigs {
    private static final String PREFIX = "autyism-le.config";

    private AleToolsConfigs() {
    }

    /** Which blocks the guide looks for */
    public enum LocateMode implements IConfigOptionListEntry {
        BOTH("both"),
        MISSING_ONLY("missing"),
        WRONG_ONLY("wrong");

        private final String id;

        LocateMode(String id) {
            this.id = id;
        }

        public boolean wantsMissing() {
            return this != WRONG_ONLY;
        }

        public boolean wantsWrong() {
            return this != MISSING_ONLY;
        }

        @Override
        public String getStringValue() {
            return this.id;
        }

        @Override
        public String getDisplayName() {
            return StringUtils.translate("autyism-le.locate_mode." + this.id);
        }

        @Override
        public LocateMode cycle(boolean forward) {
            LocateMode[] values = values();
            return values[(this.ordinal() + (forward ? 1 : values.length - 1)) % values.length];
        }

        @Override
        public LocateMode fromString(String value) {
            for (LocateMode m : values()) {
                if (m.id.equalsIgnoreCase(value)) return m;
            }
            return BOTH;
        }
    }

    // ---------------- wrong block guide ----------------
    public static final ConfigBoolean LOCATE = new ConfigBoolean("locateWrongBlocks", true).apply(PREFIX);
    public static final ConfigOptionList LOCATE_MODE = new ConfigOptionList("locateMode", LocateMode.BOTH).apply(PREFIX);
    public static final ConfigColor LOCATE_BOX_COLOR = new ConfigColor("locateBoxColor", "#FFFF8A00").apply(PREFIX);
    public static final ConfigColor LOCATE_ARROW_COLOR = new ConfigColor("locateArrowColor", "#FFFFC21A").apply(PREFIX);
    // Not bound by default: the owner's keyboard is full of other mods' keys
    public static final ConfigHotkey LOCATE_NEXT = new ConfigHotkey("locateNext", "").apply(PREFIX);
    public static final ConfigHotkey LOCATE_PREVIOUS = new ConfigHotkey("locatePrevious", "").apply(PREFIX);
    public static final ConfigHotkey LOCATE_CLEAR = new ConfigHotkey("locateClear", "").apply(PREFIX);

    // ---------------- schematic diff ----------------
    public static final ConfigBoolean DIFF = new ConfigBoolean("schematicDiff", true).apply(PREFIX);
    public static final ConfigBoolean DIFF_SHOW = new ConfigBoolean("schematicDiffShow", true).apply(PREFIX);
    public static final ConfigColor DIFF_ADDED_COLOR = new ConfigColor("schematicDiffAddedColor", "#FF35E04A").apply(PREFIX);
    public static final ConfigColor DIFF_REMOVED_COLOR = new ConfigColor("schematicDiffRemovedColor", "#FFFF3B3B").apply(PREFIX);
    public static final ConfigColor DIFF_CHANGED_COLOR = new ConfigColor("schematicDiffChangedColor", "#FFFFD21F").apply(PREFIX);
    public static final ConfigInteger DIFF_MAX_BOXES = new ConfigInteger("schematicDiffMaxBoxes", 2000, 0, 50000).apply(PREFIX);
    public static final ConfigHotkey DIFF_TOGGLE = new ConfigHotkey("schematicDiffToggle", "").apply(PREFIX);
    public static final ConfigHotkey DIFF_OPEN_LIST = new ConfigHotkey("schematicDiffOpenList", "").apply(PREFIX);

    public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
            LOCATE,
            LOCATE_MODE,
            LOCATE_BOX_COLOR,
            LOCATE_ARROW_COLOR,
            LOCATE_NEXT,
            LOCATE_PREVIOUS,
            LOCATE_CLEAR,
            DIFF,
            DIFF_SHOW,
            DIFF_ADDED_COLOR,
            DIFF_REMOVED_COLOR,
            DIFF_CHANGED_COLOR,
            DIFF_MAX_BOXES,
            DIFF_TOGGLE,
            DIFF_OPEN_LIST
    );

    public static final ImmutableList<ConfigHotkey> HOTKEYS = ImmutableList.of(
            LOCATE_NEXT, LOCATE_PREVIOUS, LOCATE_CLEAR, DIFF_TOGGLE, DIFF_OPEN_LIST);

    public static LocateMode locateMode() {
        return LOCATE_MODE.getOptionListValue() instanceof LocateMode m ? m : LocateMode.BOTH;
    }

    /** Hotkey actions (called from AleHotkeys.register) */
    static void registerCallbacks() {
        LOCATE_NEXT.getKeybind().setCallback((action, key) -> {
            com.autyism.ale.locate.WrongBlockGuide.next();
            return true;
        });
        LOCATE_PREVIOUS.getKeybind().setCallback((action, key) -> {
            com.autyism.ale.locate.WrongBlockGuide.previous();
            return true;
        });
        LOCATE_CLEAR.getKeybind().setCallback((action, key) -> {
            com.autyism.ale.locate.WrongBlockGuide.clear(true);
            return true;
        });
        DIFF_TOGGLE.getKeybind().setCallback((action, key) -> {
            DIFF_SHOW.toggleBooleanValue();
            return true;
        });
        DIFF_OPEN_LIST.getKeybind().setCallback((action, key) -> {
            com.autyism.ale.diff.DiffSession.openList(null);
            return true;
        });
        LOCATE.setValueChangeCallback(c -> {
            if (!c.getBooleanValue()) com.autyism.ale.locate.WrongBlockGuide.clear(false);
        });
    }
}
