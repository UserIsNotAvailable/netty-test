package com.wtb;

import io.netty.util.concurrent.*;

public class ProgressivePromiseTest {
    public static void main(String[] args) {
        EventExecutor executor = new DefaultEventExecutor();

        try {
            // 1. 创建一个“带进度”的 Promise
            ProgressivePromise<String> promise = executor.newProgressivePromise();

            // 2. 添加监听器 (注意泛型是 ProgressiveFuture)
            promise.addListener(new GenericProgressiveFutureListener<ProgressiveFuture<String>>() {
                
                // 【新增】进度回调：每次调用 setProgress 都会触发这里
                @Override
                public void operationProgressed(ProgressiveFuture<String> future, long progress, long total) {
                    System.out.println("Listener: 下载进度 " + progress + " / " + total + 
                                     " (" + (progress * 100 / total) + "%)");
                }

                // 完成回调：最后 setSuccess/setFailure 时触发
                @Override
                public void operationComplete(ProgressiveFuture<String> future) {
                    if (future.isSuccess()) {
                        System.out.println("Listener: 下载完成！结果: " + future.getNow());
                    } else {
                        System.out.println("Listener: 下载失败: " + future.cause());
                    }
                    executor.shutdownGracefully();
                }
            });

            // 3. 模拟下载任务
            executor.submit(() -> {
                System.out.println("Worker: 开始下载文件...");
                long total = 100;
                
                for (long i = 0; i <= total; i += 10) {
                    try {
                        Thread.sleep(100); // 模拟网络延迟
                        
                        // 【关键】更新进度
                        // 如果 i == total，虽然进度满了，但 Promise 还没结束，必须显式 setSuccess
                        promise.setProgress(i, total); 
                        
                    } catch (InterruptedException e) {
                        promise.setFailure(e);
                        return;
                    }
                }
                
                // 4. 全部搞定
                promise.setSuccess("jdk-21-linux.tar.gz");
            });

            // 阻塞主线程以免 JVM 退出
            Thread.sleep(2000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
