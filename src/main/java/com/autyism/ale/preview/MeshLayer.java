package com.autyism.ale.preview;

/** 预览网格的渲染层（对应原版区段的实心 / 镂空 / 半透明 / 绊线层），按这个顺序绘制 */
enum MeshLayer {
    SOLID,
    CUTOUT,
    TRIPWIRE,
    TRANSLUCENT
}
