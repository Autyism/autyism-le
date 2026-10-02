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
        if (t == Sheets.solidBlockSheet() || t == Sheets.cutoutBlockSheet()) return Sheets.translucentItemSheet();
        if (t == Sheets.chestSheet()) return RenderTypes.entityTranslucent(Sheets.CHEST_SHEET);
        if (t == Sheets.signSheet() || t == Sheets.hangingSignSheet()) return RenderTypes.entityTranslucent(Sheets.SIGN_SHEET);
        if (t == Sheets.bedSheet()) return RenderTypes.entityTranslucent(Sheets.BED_SHEET);
        if (t == Sheets.shulkerBoxSheet()) return RenderTypes.entityTranslucent(Sheets.SHULKER_SHEET);
        if (t == Sheets.shieldSheet()) return RenderTypes.entityTranslucent(Sheets.SHIELD_SHEET);
        if (t == Sheets.bannerSheet()) return RenderTypes.entityTranslucent(Sheets.BANNER_SHEET);
        return t;
    }
}
