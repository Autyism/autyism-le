package com.autyism.ale.gametest;

/** 用 -Pgt=name1,name2 只运行部分测试（空 = 全部）。 */
public final class GTFilter {
    private GTFilter() {
    }

    /** 渲染期间让 Litematica 使用客户端世界，避免 gametest 中跨线程读取服务端世界死锁 */
    public static volatile boolean clientBestWorld = false;

    public static boolean enabled(String name) {
        String filter = System.getProperty("ale.gt", "");
        if (filter.isBlank()) return true;
        for (String s : filter.split(",")) {
            if (s.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }
}
