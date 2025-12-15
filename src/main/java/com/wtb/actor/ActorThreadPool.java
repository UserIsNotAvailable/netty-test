package com.wtb.actor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActorThreadPool {

    private static final int N_THREADS =
            Runtime.getRuntime().availableProcessors() * 2;

    private static final ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(N_THREADS, r -> {
                Thread t = new Thread(r);
                t.setName("actor-thread-" + t.getId());
                t.setDaemon(true);
                return t;
            });

    public static void execute(Runnable task) {
        EXECUTOR.submit(task);
    }
}
