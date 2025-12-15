package com.wtb;

import io.netty.util.concurrent.*;

public class PromiseCombineTest {
    public static void main(String[] args) {
        EventExecutor executor = new DefaultEventExecutor();

        try {
            System.out.println("Main: 开始准备晚饭...");

            // 1. 定义总任务 (晚饭)
            Promise<Void> dinnerPromise = executor.newPromise();

            // 2. 准备聚合器
            PromiseCombiner combiner = new PromiseCombiner(executor);

            // 3. 定义并提交子任务 1 (煮饭)
            Promise<Void> ricePromise = executor.newPromise();
            executor.submit(() -> {
                try {
                    System.out.println("厨师A: 开始煮饭...");
                    Thread.sleep(1000);
                    System.out.println("厨师A: 饭煮好了！");
                    ricePromise.setSuccess(null);
                } catch (InterruptedException e) {
                    ricePromise.setFailure(e);
                }
            });

            // 4. 定义并提交子任务 2 (炒菜)
            Promise<Void> dishPromise = executor.newPromise();
            executor.submit(() -> {
                try {
                    System.out.println("厨师B: 开始炒菜...");
                    Thread.sleep(2000); // 这个更慢
                    System.out.println("厨师B: 菜炒好了！");
                    dishPromise.setSuccess(null);
                } catch (InterruptedException e) {
                    dishPromise.setFailure(e);
                }
            });

            // 5. 将子任务加入聚合器
            // 虽然 add(Promise) 被标记为 Deprecated，但 add(Future) 是推荐用法
            // Promise 是 Future 的子类，所以这里直接传也没问题，但为了消除警告，我们可以向上转型
            combiner.add((Future<Void>) ricePromise);
            combiner.add((Future<Void>) dishPromise);

            // 6. 绑定总任务 (当所有子任务都完成时，dinnerPromise 会被 setSuccess)
            combiner.finish(dinnerPromise);

            // 7. 监听总任务
            dinnerPromise.addListener((FutureListener<Void>) future -> {
                if (future.isSuccess()) {
                    System.out.println("Main: 晚饭做好了，开吃！(总耗时取决于最慢的那个)");
                } else {
                    System.out.println("Main: 晚饭搞砸了: " + future.cause());
                }
                executor.shutdownGracefully();
            });

            // 等待观察结果
            Thread.sleep(3000);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
