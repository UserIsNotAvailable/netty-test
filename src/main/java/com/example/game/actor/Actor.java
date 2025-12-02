package com.example.game.actor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 轻量 Actor 实现：
 * - 内部一个任务队列
 * - 同一时刻最多一个线程在执行 runLoop
 */
public abstract class Actor {

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = false;

    public void post(Runnable task) {
        queue.add(task);
        tryStart();
    }

    private void tryStart() {
        if (!running) {
            synchronized (this) {
                if (!running) {
                    running = true;
                    ActorThreadPool.execute(this::runLoop);
                }
            }
        }
    }

    private void runLoop() {
        try {
            Runnable task;
            while ((task = queue.poll()) != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    handleException(t);
                }
            }
        } finally {
            running = false;
            if (!queue.isEmpty()) {
                tryStart();
            }
        }
    }

    protected void handleException(Throwable t) {
        t.printStackTrace();
    }
}
