package com.autyism.ale.gametest;

/** 用 -Pgt=name1,name2 只运行部分测试（空 = 全部）。 */
public final class GTFilter {
    private GTFilter() {
    }

    /** 渲染期间让 Litematica 使用客户端世界，避免 gametest 中跨线程读取服务端世界死锁 */
    public static volatile boolean clientBestWorld = false;

    /** 截图对比期间：动画贴图停住、不产生粒子（StillFramesMixin / NoParticlesMixin） */
    public static volatile boolean stillFrames = false;

    /** -PmeasureOnly：只记录测量结果，不做判定（对比修改前后的数值时用） */
    public static boolean measureOnly() {
        return Boolean.getBoolean("ale.measureonly");
    }

    /**
     * 只在 -Pgt 里点名时才运行（观察原作用的工具，比如并排对照；完整回归里不跑，免得原作自己的问题让回归失败）
     */
    public static boolean explicit(String name) {
        String filter = System.getProperty("ale.gt", "");
        for (String s : filter.split(",")) {
            if (s.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static boolean enabled(String name) {
        String filter = System.getProperty("ale.gt", "");
        if (filter.isBlank()) return true;
        for (String s : filter.split(",")) {
            if (s.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
