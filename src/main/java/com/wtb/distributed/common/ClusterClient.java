package com.wtb.distributed.common;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;

public abstract class ClusterClient {

    protected final ServerConfig config;
    protected Channel channel;
    private final NioEventLoopGroup group = new NioEventLoopGroup();
    private final Bootstrap b = new Bootstrap();

    public ClusterClient(String configPath) {
        this.config = ServerConfig.load(configPath);
        init();
    }

    private void init() {
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new IdleStateHandler(0, 5, 0)) // 5秒没写数据，触发 WRITER_IDLE
                                .addLast(new JsonCodec()) // 改用自定义协议
                                .addLast(new SimpleChannelInboundHandler<ClusterMsg>() {
                                    
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (evt instanceof IdleStateEvent) {
                                            if (((IdleStateEvent) evt).state() == IdleState.WRITER_IDLE) {
                                                // 发送应用层心跳 (Cmd=HEARTBEAT)
                                                ctx.writeAndFlush(ClusterMsg.heartbeat(config.type, config.id));
                                            }
                                        } else {
                                            super.userEventTriggered(ctx, evt);
                                        }
                                    }

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) {
                                        System.out.println("已连接到 Match Server!");
                                        // 发送注册包
                                        ctx.writeAndFlush(ClusterMsg.register(config.type, config.id));
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) {
                                        System.out.println("与 Match Server 断开！准备重连...");
                                        channel = null; // 置空
                                        scheduleConnect(3); 
                                    }

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, ClusterMsg msg) {
                                        System.out.println("收到 Match 指令: " + msg);
                                    }
                                });
                    }
                });
    }

    public void start() {
        connect();
        
        // 同时也注册自己到 Redis (供外部发现，不是给 Match 用的)
        RedisRegistry.register(config.type, config.id, config.ip, config.port);
    }

    // 【新增】暴露发送接口
    public void send(ClusterMsg msg) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(msg);
        } else {
            System.err.println("发送失败: 未连接到 Match Server");
        }
    }

    private void connect() {
        // 发现 Master
        io.netty.util.concurrent.Promise<String> promise = group.next().newPromise();
        RedisRegistry.getMatchServerAddress(promise);
        
        promise.addListener((io.netty.util.concurrent.Future<String> f) -> {
            if (f.isSuccess()) {
                String address = f.getNow();
                // ... 解析 IP:Port 连接 ...
                // 这里的解析逻辑同之前，省略重复代码
                String[] parts = address.split(":");
                b.connect(parts[0], Integer.parseInt(parts[1])).addListener(cf -> {
                    if (cf.isSuccess()) {
                         channel = ((ChannelFuture) cf).channel();
                    } else {
                         scheduleConnect(3);
                    }
                });
            } else {
                scheduleConnect(3);
            }
        });
    }
    
    private void scheduleConnect(int seconds) {
        group.schedule(this::connect, seconds, TimeUnit.SECONDS);
    }
}
