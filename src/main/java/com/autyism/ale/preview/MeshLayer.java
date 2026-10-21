package com.autyism.ale.preview;

/** 预览网格的渲染层（对应原版区段的实心 / 镂空 / 半透明 / 绊线层），按这个顺序绘制 */
enum MeshLayer {
    SOLID,
    // 1.21.10 及更早原版区段还分带 mipmap 的镂空层（树叶、草等）和不带的（玻璃板、铁栏杆等）
    //? if <1.21.11
    //CUTOUT_MIPPED,
    CUTOUT,
    TRIPWIRE,
    TRANSLUCENT
}
