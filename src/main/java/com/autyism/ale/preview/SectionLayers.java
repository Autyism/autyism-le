package com.autyism.ale.preview;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

/** 原版区段的层 → 预览网格的层（1.21.6 起区段的层是 ChunkSectionLayer；1.21.5 见 src/v1215） */
final class SectionLayers {
    private SectionLayers() {
    }

    static MeshLayer of(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> MeshLayer.SOLID;
            //? if <1.21.11
            //case CUTOUT_MIPPED -> MeshLayer.CUTOUT_MIPPED;
            case CUTOUT -> MeshLayer.CUTOUT;
            case TRANSLUCENT -> MeshLayer.TRANSLUCENT;
            //? if <26.1
            case TRIPWIRE -> MeshLayer.TRIPWIRE;
        };
    }
}
