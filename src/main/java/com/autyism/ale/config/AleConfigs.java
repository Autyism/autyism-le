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
import fi.dy.masa.malilib.util.data.json.JsonUtils;

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
        );
    }

    public static void init() {
        INSTANCE.load();
        ConfigManager.getInstance().registerConfigHandler(AleMod.MOD_ID, INSTANCE);
        fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new fi.dy.masa.malilib.util.data.ModInfo(AleMod.MOD_ID, AleMod.MOD_NAME, AleConfigGui::new));
    }

    @Override
    public void load() {
        if (!Files.isRegularFile(FILE)) return;
        JsonElement element = JsonUtils.parseJsonFile(FILE);
        if (element != null && element.isJsonObject()) {
            ConfigUtils.readConfigBase(element.getAsJsonObject(), "Generic", Generic.OPTIONS);
        }
    }

    @Override
    public void save() {
        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
        try {
            Files.createDirectories(FILE.getParent());
        } catch (Exception ignored) {
        }
        JsonUtils.writeJsonToFile(root, FILE);
    }
}
