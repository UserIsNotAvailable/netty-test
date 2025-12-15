package com.wtb.distributed.match;

import com.wtb.distributed.common.*;
import com.wtb.distributed.common.enmu.MsgType;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.DefaultEventExecutor;
import io.netty.util.concurrent.EventExecutor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MatchServer {

    private final ClusterService clusterService;
    // 路由表: ID -> Channel
    private static final Map<String, Channel> ROUTE_TABLE = new ConcurrentHashMap<>();
    
    private boolean isMaster = false;
    private final EventExecutor electionExecutor = new DefaultEventExecutor();

    public static void main(String[] args) {
        String suffix = args.length > 0 ? args[0] : "master";
        String configPath = "src/main/resources/match_" + suffix + ".properties";
        
        new MatchServer(configPath).start();
    }

    public MatchServer(String configPath) {
        // 组装 ClusterService，传入业务回调
        this.clusterService = new ClusterService(configPath, new MessageHandler() {
            @Override
            public void handle(ChannelHandlerContext ctx, ClusterMsg msg) {
                handleMessage(ctx, msg);
            }

            @Override
            public void onInactive(ChannelHandlerContext ctx) {
                handleInactive(ctx);
            }
        });
        
        // 设置启动后钩子
        this.clusterService.setOnStarted(v -> startElection());
    }

    public void start() {
        clusterService.start();
    }

    private void handleMessage(ChannelHandlerContext ctx, ClusterMsg msg) {
        if (MsgType.REGISTER == msg.getType()) {
            String id = msg.getSourceId();
            ROUTE_TABLE.put(id, ctx.channel());
            System.out.println("[路由] 节点注册: " + id + " -> " + ctx.channel().remoteAddress());
            
            ServerConfig config = clusterService.getConfig();
            ctx.writeAndFlush(ClusterMsg.registerAck(config.type, config.id));
        }
    }

    private void handleInactive(ChannelHandlerContext ctx) {
        ROUTE_TABLE.entrySet().removeIf(entry -> {
            if (entry.getValue() == ctx.channel()) {
                System.out.println("[路由] 节点断开: " + entry.getKey());
                return true;
            }
            return false;
        });
    }

    private void startElection() {
        System.out.println("启动选举任务...");
        electionExecutor.scheduleAtFixedRate(this::tryElect, 0, 5, TimeUnit.SECONDS);
    }
    
    private void tryElect() {
        ServerConfig config = clusterService.getConfig();
        RedisRegistry.tryBecomeMaster(config.id, config.ip, config.port, success -> {
            electionExecutor.execute(() -> {
                if (success) {
                    if (!isMaster) {
                        System.out.println(">>> 我已成为 MASTER! <<<");
                        isMaster = true;
                    }
                } else {
                    if (isMaster) {
                        System.out.println(">>> 我失去了 Master 身份! <<<");
                        isMaster = false;
                    }
                }
            });
        });
    }
}
