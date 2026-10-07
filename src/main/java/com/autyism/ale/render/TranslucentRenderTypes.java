package com.autyism.ale.render;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 不透明渲染类型 → 半透明版本。
 * 实体纹理的类型在创建时就会被 RenderTypesMixin 换掉；这里处理提前缓存好的“图集”类型（箱子、告示牌、床、潜影盒等方块实体用的 Sheets）。
 */
public final class TranslucentRenderTypes {
    private TranslucentRenderTypes() {
    }

    private static final Map<RenderType, RenderType> CACHE = new ConcurrentHashMap<>();

    public static RenderType translucent(RenderType type) {
        if (type == null) return null;
        return CACHE.computeIfAbsent(type, TranslucentRenderTypes::map);
    }

    private static RenderType map(RenderType t) {
        // 26.1 起箱子、告示牌、床、潜影盒等不再有提前缓存的图集类型：提交时由 RenderTypesMixin 当场换成半透明。只剩方块和物品图集
        //? if >=26.2 {
        /*if (t == Sheets.cutoutBlockItemSheet()) return Sheets.translucentBlockItemSheet();
        if (t == Sheets.cutoutItemSheet()) return Sheets.translucentItemSheet();
        *///?} elif >=26.1 {
        /*if (t == Sheets.cutoutBlockSheet() || t == Sheets.cutoutBlockItemSheet()) return Sheets.translucentBlockItemSheet();
        if (t == Sheets.cutoutItemSheet()) return Sheets.translucentItemSheet();
        *///?} else {
        if (t == Sheets.solidBlockSheet() || t == Sheets.cutoutBlockSheet()) return Sheets.translucentItemSheet();
        if (t == Sheets.chestSheet()) return RenderTypes.entityTranslucent(Sheets.CHEST_SHEET);
        if (t == Sheets.signSheet() || t == Sheets.hangingSignSheet()) return RenderTypes.entityTranslucent(Sheets.SIGN_SHEET);
        if (t == Sheets.bedSheet()) return RenderTypes.entityTranslucent(Sheets.BED_SHEET);
        if (t == Sheets.shulkerBoxSheet()) return RenderTypes.entityTranslucent(Sheets.SHULKER_SHEET);
        if (t == Sheets.shieldSheet()) return RenderTypes.entityTranslucent(Sheets.SHIELD_SHEET);
        if (t == Sheets.bannerSheet()) return RenderTypes.entityTranslucent(Sheets.BANNER_SHEET);
        //?}
        // 兜底：别的模组可能提前缓存了实体的渲染类型（绕过了 RenderTypesMixin），按名字识别不透明的实体类型，取出贴图换成半透明
        String name = nameOf(t);
        //? if >=26.1 {
        /*net.minecraft.resources.Identifier texture = name != null ? RenderTypeTextures.first(t) : null;
        if (texture != null) {
        *///?} elif >=1.21.11 {
        if (name != null && !t.state.textures.isEmpty()) {
            net.minecraft.resources.Identifier texture = t.state.textures.values().iterator().next().location();
        //?} else {
        /*// 1.21.10 及更早：贴图在渲染类型私有的状态里，经访问器读出（见 RenderTypeTextures）
        net.minecraft.resources.Identifier texture = name != null ? RenderTypeTextures.first(t) : null;
        if (texture != null) {
        *///?}
            // 26.3 没有半透明盔甲类型了，用半透明实体类型
            //? if >=26.3 {
            /*if (name.startsWith("armor_cutout")) return RenderTypes.entityTranslucent(texture);
            *///?} else
            if (name.startsWith("armor_cutout")) return RenderTypes.armorTranslucent(texture);
            if (name.equals("entity_solid") || name.startsWith("entity_cutout") || name.equals("entity_smooth_cutout")) {
                return RenderTypes.entityTranslucent(texture);
            }
        }
        return t;
    }

    /** RenderType 的名字（toString 形如 "RenderType[entity_cutout_no_cull:...]"，名字是运行时字符串，不受混淆影响） */
    @org.jetbrains.annotations.Nullable
    private static String nameOf(RenderType t) {
        String s = t.toString();
        int a = s.indexOf('['), b = s.indexOf(':');
        return a >= 0 && b > a ? s.substring(a + 1, b) : null;
    }
}
