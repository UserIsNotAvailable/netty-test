package com.wtb;

import io.netty.util.concurrent.*;

public class PromiseAsyncTest {
    public static void main(String[] args) {
        // 1. 搞个线程池 (EventExecutor)
        // 这里的 EventExecutor 就是一个专门干活的线程，它也有自己的任务队列
        EventExecutor executor = new DefaultEventExecutor();

        try {
            // 2. 创建一个 Promise (承诺：未来会返回一个 String)
            Promise<String> promise = executor.newPromise();

            // 3. 设置回调 (消费者)
            // 这里的逻辑是非阻塞的，addListener 会立刻返回
            promise.addListener(new FutureListener<String>() {
                @Override
                public void operationComplete(Future<String> future) throws Exception {
                    // 这个回调方法通常会在 executor 线程里执行
                    if (future.isSuccess()) {
                        System.out.println("Listener (" + Thread.currentThread().getName() + "): 任务成功，结果是: " + future.getNow());
                    } else {
                        System.out.println("Listener (" + Thread.currentThread().getName() + "): 任务失败，异常是: " + future.cause());
                    }
                }
            });

            // 4. 开始干活 (生产者)
            executor.submit(() -> {
                try {
                    System.out.println("Worker (" + Thread.currentThread().getName() + "): 开始做饭...");
                    Thread.sleep(2000); // 模拟耗时

                    // 5. 决定命运的时刻！
                    if (Math.random() > 0.5) {
                        promise.setSuccess("红烧肉"); // 成功，触发 Listener
                    } else {
                        promise.setFailure(new RuntimeException("烧糊了")); // 失败，触发 Listener
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            System.out.println("Main (" + Thread.currentThread().getName() + "): 我去打游戏了，饭好了叫我。");
            
            // 为了看到输出，主线程稍微等一下再退出（实际项目中不需要，因为 worker 线程没结束前 JVM 不会退）
            Thread.sleep(3000);
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // 优雅关闭
            executor.shutdownGracefully();
        }
    }
}
