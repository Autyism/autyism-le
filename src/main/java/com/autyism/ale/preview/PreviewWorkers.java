package com.autyism.ale.preview;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 预览用的后台线程：读文件、生成网格都在这里做，不占游戏主线程。
 * 两个低优先级线程；任务后进先出（刚滚到的缩略图先做）。
 */
final class PreviewWorkers {
    private PreviewWorkers() {
    }

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new LinkedBlockingDeque<>() {
                @Override
                public boolean offer(Runnable r) {
                    // 新任务放到队首：最后请求的（屏幕上正看着的）先做
                    return super.offerFirst(r);
                }
            }, r -> {
        Thread t = new Thread(r, "ALE schematic preview " + COUNTER.incrementAndGet());
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    static {
        ((ThreadPoolExecutor) EXECUTOR).allowCoreThreadTimeOut(true);
    }

    static Future<?> submit(Runnable task) {
        return EXECUTOR.submit(task);
    }
}
