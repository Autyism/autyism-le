package com.autyism.ale.preview;

import net.minecraft.client.renderer.RenderType;

/** 原版区段的层 → 预览网格的层。Minecraft 1.21.5 版：区段的层还是渲染类型 */
final class SectionLayers {
    private SectionLayers() {
    }

    static MeshLayer of(RenderType layer) {
        if (layer == RenderType.solid()) return MeshLayer.SOLID;
        if (layer == RenderType.cutoutMipped()) return MeshLayer.CUTOUT_MIPPED;
        if (layer == RenderType.cutout()) return MeshLayer.CUTOUT;
        if (layer == RenderType.translucent()) return MeshLayer.TRANSLUCENT;
        if (layer == RenderType.tripwire()) return MeshLayer.TRIPWIRE;
        return MeshLayer.SOLID;
    }
}
