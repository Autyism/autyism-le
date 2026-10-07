package com.autyism.ale.config;

import com.autyism.ale.AleMod;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
//? if >=1.21.11 {
import fi.dy.masa.malilib.util.data.json.JsonUtils;
//?} else {
/*import fi.dy.masa.malilib.util.JsonUtils;
*///?}

import java.nio.file.Files;
import java.nio.file.Path;

public class AleConfigs implements IConfigHandler {
    private static final AleConfigs INSTANCE = new AleConfigs();
    private static final String PREFIX = "autyism-le.config";
    private static final Path FILE = Path.of("config", AleMod.MOD_ID + ".json");

    public static class Generic {
        // 需求 3：所有方块列表使用方块选择界面
        public static final ConfigBoolean BLOCK_PICKER = new ConfigBoolean("blockPicker", true).apply(PREFIX);
        // 需求 4：投影放置编辑保存按钮
        public static final ConfigBoolean SAVE_EDIT_BUTTONS = new ConfigBoolean("saveEditButtons", true).apply(PREFIX);
        // 需求 5a：流体/实体的方块信息对比
        public static final ConfigBoolean INFO_FLUIDS_ENTITIES = new ConfigBoolean("infoOverlayFluidsEntities", true).apply(PREFIX);
        // 需求 5b：投影实体半透明
        public static final ConfigBoolean TRANSLUCENT_ENTITIES = new ConfigBoolean("translucentSchematicEntities", true).apply(PREFIX);
        // 需求 6：只差含水时画蓝色 W
        public static final ConfigBoolean WATERLOGGED_MARKER = new ConfigBoolean("waterloggedMarker", true).apply(PREFIX);
        public static final ConfigColor WATERLOGGED_MARKER_COLOR = new ConfigColor("waterloggedMarkerColor", "#FF1E64FF").apply(PREFIX);
        // 朝向错误：红色 D
        public static final ConfigBoolean ORIENTATION_MARKER = new ConfigBoolean("orientationMarker", true).apply(PREFIX);
        public static final ConfigColor ORIENTATION_MARKER_COLOR = new ConfigColor("orientationMarkerColor", "#FFFF2020").apply(PREFIX);
        // 需求 9：材料列表中的容器内容物、实体；验证器检查容器内容物
        public static final ConfigBoolean MATERIAL_LIST_ENTITIES = new ConfigBoolean("materialListEntities", true).apply(PREFIX);
        public static final ConfigBoolean VERIFIER_CONTAINERS = new ConfigBoolean("verifierContainerContents", true).apply(PREFIX);
        // 需求 10：告示牌替换保持形态/朝向/文字
        public static final ConfigBoolean SIGN_REPLACE_FIX = new ConfigBoolean("signReplaceFix", true).apply(PREFIX);
        // 需求 12：透过玻璃也能看到投影渲染
        public static final ConfigBoolean RENDER_THROUGH_GLASS = new ConfigBoolean("renderThroughGlass", true).apply(PREFIX);
        // 投影浏览器预览、图标和材料列表“替换”：先做了 1.21.11 和 26.x，1.21.10 及更早以后再移植
        //? if >=1.21.11 {
        // 投影浏览器的显示方式、3D 预览和文件夹图标（装了 Schematic Preview 时由它负责）
        public static final ConfigBoolean BROWSER_PREVIEWS = new ConfigBoolean("schematicBrowserPreviews", true).apply(PREFIX);
        // 材料列表每一行的“替换”（装了 Schematic Preview 时由它负责）
        public static final ConfigBoolean MATERIAL_REPLACE = new ConfigBoolean("materialListReplace", true).apply(PREFIX);
        // 打开 ALE 设置的热键（默认不绑定，避免和别的模组冲突）
        public static final ConfigHotkey OPEN_CONFIG = new ConfigHotkey("openConfigGui", "").apply(PREFIX);
        //?}

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BLOCK_PICKER,
                SAVE_EDIT_BUTTONS,
                INFO_FLUIDS_ENTITIES,
                TRANSLUCENT_ENTITIES,
                WATERLOGGED_MARKER,
                WATERLOGGED_MARKER_COLOR,
                ORIENTATION_MARKER,
                ORIENTATION_MARKER_COLOR,
                MATERIAL_LIST_ENTITIES,
                VERIFIER_CONTAINERS,
                SIGN_REPLACE_FIX,
                RENDER_THROUGH_GLASS
                //? if >=1.21.11 {
                , BROWSER_PREVIEWS,
                MATERIAL_REPLACE,
                OPEN_CONFIG
                //?}
        );
        //? if >=1.21.11 {

        public static final ImmutableList<ConfigHotkey> HOTKEYS = ImmutableList.of(OPEN_CONFIG);
        //?}
    }
    //? if >=1.21.11 {

    /** 投影浏览器：显示方式、间距、行高、缩略图体积上限 */
    public static class Browser {
        public static final ConfigOptionList LAYOUT = new ConfigOptionList("browserLayout", com.autyism.ale.browser.BrowserLayout.LIST).apply(PREFIX);
        public static final ConfigInteger GAP_X = new ConfigInteger("browserGapX", 2, 0, 32).apply(PREFIX);
        public static final ConfigInteger GAP_Y = new ConfigInteger("browserGapY", 2, 0, 32).apply(PREFIX);
        public static final ConfigInteger LIST_ROW_HEIGHT = new ConfigInteger("browserListRowHeight", 15, 10, 64).apply(PREFIX);
        public static final ConfigInteger PREVIEW_ROW_HEIGHT = new ConfigInteger("browserPreviewRowHeight", 35, 16, 128).apply(PREFIX);
        public static final ConfigDouble TILE_HEIGHT_RATIO = new ConfigDouble("browserTileHeightRatio", 1.0, 0.25, 4.0).apply(PREFIX);
        public static final ConfigInteger THUMBNAIL_MAX_VOLUME = new ConfigInteger("thumbnailMaxVolume", 125000, 0, Integer.MAX_VALUE).apply(PREFIX);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                LAYOUT,
                GAP_X,
                GAP_Y,
                LIST_ROW_HEIGHT,
                PREVIEW_ROW_HEIGHT,
                TILE_HEIGHT_RATIO,
                THUMBNAIL_MAX_VOLUME
        );

        public static com.autyism.ale.browser.BrowserLayout layout() {
            return LAYOUT.getOptionListValue() instanceof com.autyism.ale.browser.BrowserLayout l ? l : com.autyism.ale.browser.BrowserLayout.LIST;
        }
    }

    /** 3D 预览：方块实体、视野、默认角度 */
    public static class Preview {
        public static final ConfigBoolean BLOCK_ENTITIES = new ConfigBoolean("previewBlockEntities", true).apply(PREFIX);
        public static final ConfigDouble FOV = new ConfigDouble("previewFov", 50.0, 10.0, 150.0).apply(PREFIX);
        public static final ConfigDouble YAW = new ConfigDouble("previewYaw", -45.0, -180.0, 180.0).apply(PREFIX);
        public static final ConfigDouble PITCH = new ConfigDouble("previewPitch", 30.0, -90.0, 90.0).apply(PREFIX);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BLOCK_ENTITIES,
                FOV,
                YAW,
                PITCH
        );
    }
    //?}

    public static void init() {
        INSTANCE.load();
        ConfigManager.getInstance().registerConfigHandler(AleMod.MOD_ID, INSTANCE);
        fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new fi.dy.masa.malilib.util.data.ModInfo(AleMod.MOD_ID, AleMod.MOD_NAME, AleConfigGui::new));
        //? if >=1.21.11
        AleHotkeys.register();
    }
    //? if >=1.21.11 {

    /** 立刻保存（例如改了文件夹图标） */
    public static void saveNow() {
        INSTANCE.save();
    }
    //?}

    @Override
    public void load() {
        if (!Files.isRegularFile(FILE)) return;
        // 1.21.10 及更早 MaLiLib 按 Path 读写的方法名带 AsPath
        //? if >=1.21.11 {
        JsonElement element = JsonUtils.parseJsonFile(FILE);
        //?} else
        //JsonElement element = JsonUtils.parseJsonFileAsPath(FILE);
        if (element != null && element.isJsonObject()) {
            //? if >=1.21.11 {
            JsonObject root = element.getAsJsonObject();
            ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.readConfigBase(root, "Browser", Browser.OPTIONS);
            ConfigUtils.readConfigBase(root, "Preview", Preview.OPTIONS);
            com.autyism.ale.browser.FolderIcons.read(root.get("FolderIcons"));
            //?} else
            //ConfigUtils.readConfigBase(element.getAsJsonObject(), "Generic", Generic.OPTIONS);
        }
    }

    @Override
    public void save() {
        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
        //? if >=1.21.11 {
        ConfigUtils.writeConfigBase(root, "Browser", Browser.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Preview", Preview.OPTIONS);
        root.add("FolderIcons", com.autyism.ale.browser.FolderIcons.write());
        //?}
        try {
            Files.createDirectories(FILE.getParent());
        } catch (Exception ignored) {
        }
        //? if >=1.21.11 {
        JsonUtils.writeJsonToFile(root, FILE);
        //?} else
        //JsonUtils.writeJsonToFileAsPath(root, FILE);
    }
}
