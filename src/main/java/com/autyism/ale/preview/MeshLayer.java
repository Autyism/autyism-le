//? if >=1.21.11 {
// 投影浏览器预览、图标和“替换”：1.21.10 及更早以后再移植
package com.autyism.ale.preview;

/** 预览网格的渲染层（对应原版区段的实心 / 镂空 / 半透明 / 绊线层），按这个顺序绘制 */
enum MeshLayer {
    SOLID,
    CUTOUT,
    TRIPWIRE,
    TRANSLUCENT
}
//?}
