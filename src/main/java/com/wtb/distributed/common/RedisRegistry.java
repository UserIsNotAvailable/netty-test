package com.wtb.distributed.common;

import com.twb.distributed.common.enmu.NodeType;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RedisRegistry {

    private static RedisClient client;
    private static StatefulRedisConnection<String, String> connection;
    private static RedisAsyncCommands<String, String> async;
    private static final EventExecutor scheduler = new DefaultEventExecutor();

    public static synchronized void init() {
        if (client != null) return;
        
        try {
            // 假设 Redis 在本地默认端口
            client = RedisClient.create(RedisURI.create("redis://192.168.1.3:6379"));
            connection = client.connect();
            async = connection.async();
            System.out.println("[Redis] 连接成功 (Lettuce)");
        } catch (Exception e) {
            System.err.println("[Redis] 连接失败: " + e.getMessage());
        }
    }

    /**
     * 注册服务
     * 会自动开启定时续期任务
     */
    public static void register(NodeType type, String id, String address, int port) {
        if (client == null) init();
        if (async == null) return;

        String key = "REGISTRY:" + type.name() + ":" + id;
        String value = address + ":" + port;

        // 1. 立即注册 (TTL 10s)
        async.setex(key, 10, value);
        System.out.println("[Redis] 注册服务: " + key + " -> " + value);

        // 2. 启动心跳续期 (每5秒一次)
        scheduler.scheduleAtFixedRate(() -> {
            // 如果连接还在，就续期
            if (connection.isOpen()) {
                async.expire(key, 10);
                // System.out.println("[Redis] 续期: " + key);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 异步获取 Match Server 地址
     * @param promise 用于通知结果的 Promise
     */
    public static void getMatchServerAddress(Promise<String> promise) {
        if (client == null) init();
        if (async == null) {
            promise.setFailure(new RuntimeException("Redis未连接"));
            return;
        }

        // 约定 Match Server ID 为 MASTER
        String key = "REGISTRY:MATCH:MASTER";
        
        async.get(key).thenAccept(val -> {
            if (val != null) {
                promise.setSuccess(val);
            } else {
                promise.setFailure(new RuntimeException("Redis 中找不到 Match Server"));
            }
        }).exceptionally(e -> {
            promise.setFailure(e);
            return null;
        });
    }

    /**
     * 尝试成为 Master
     * 利用 Redis SETNX (set if not exists) 或者简单的覆盖+续期逻辑
     * 为了简单，这里逻辑是：
     * 1. 尝试 Get Key
     * 2. 如果 Key 不存在，或者 Key 的值就是我自己，那么 Setex 并成功。
     * 3. 否则失败。
     */
    public static void tryBecomeMaster(String myId, String ip, int port, Consumer<Boolean> callback) {
        if (client == null) init();
        if (async == null) return;
        
        String key = "REGISTRY:MATCH:MASTER";
        String myVal = ip + ":" + port;

        async.get(key).thenAccept(currentVal -> {
            if (currentVal == null || currentVal.equals(myVal)) {
                // 没主，或者是老子自己，续期/上位
                async.setex(key, 5, myVal);
                callback.accept(true);
            } else {
                // 有别人是主
                callback.accept(false);
            }
        }).exceptionally(e -> {
            callback.accept(false);
            return null;
        });
    }
}
