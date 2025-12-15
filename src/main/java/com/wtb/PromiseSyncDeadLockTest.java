package com.wtb;

import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

public class PromiseSyncDeadLockTest {
    public static void main(String[] args) {
        // 使用一个单线程的 Executor (模拟 Netty 的 EventLoop)
        EventExecutor executor = new DefaultEventExecutor();

        Promise<String> promise = executor.newPromise();

        // 提交一个任务给 executor 执行
        executor.submit(() -> {
            System.out.println("Worker: 我开始运行了...");
            
            try {
                // 【死亡操作】
                // 我在 worker 线程里，但我试图 sync() 等待 promise 结束。
                // 可是 promise 要什么时候结束呢？
                // 假设 promise 需要我这个线程去执行某些回调才能结束，或者我这行代码下面的逻辑才能结束 promise。
                // 这就构成了死锁！
                // Netty 很聪明，它会检测到你在 EventLoop 里调用 sync()，直接抛异常阻止你死锁。
                System.out.println("Worker: 我准备 sync() 了...");
                promise.sync(); 
                
                System.out.println("Worker: 我 sync() 完了！(你永远看不到这句)");
            } catch (Exception e) {
                System.err.println("Worker: 发生异常了！");
                e.printStackTrace();
            }
        });

        // 另起一个线程尝试去完成 promise (模拟外部事件)
        // 即便外部线程把 promise 设为成功了，上面的 worker 线程也可能因为死锁检测而先抛异常。
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.out.println("External: 我把 Promise 设为成功");
                promise.setSuccess("成功");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}