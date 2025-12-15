package com.wtb.distributed.common;

import com.wtb.distributed.common.enmu.MsgType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.function.Consumer;

/**
 * 集群服务组件 (原 ClusterServer)
 * 使用组合模式，不再需要继承
 */
public class ClusterService {

    protected final ServerConfig config;
    private final EventLoopGroup boss = new NioEventLoopGroup(1);
    private final EventLoopGroup worker = new NioEventLoopGroup();
    
    // 业务回调
    private final MessageHandler messageHandler;
    // 启动完成回调
    private Consumer<Void> onStartedCallback;

    public ClusterService(String configPath, MessageHandler messageHandler) {
        this.config = ServerConfig.load(configPath);
        this.messageHandler = messageHandler;
    }

    public void setOnStarted(Consumer<Void> callback) {
        this.onStartedCallback = callback;
    }
    
    public ServerConfig getConfig() {
        return config;
    }

    public void start() {
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast(new IdleStateHandler(15, 0, 0))
                                    .addLast(new JsonCodec())
                                    .addLast(new ClusterServerHandler());
                        }
                    });

            ChannelFuture f = b.bind(config.port).sync();
            System.out.println(config.type + " Server [" + config.id + "] 启动，监听端口: " + config.port);

            if (onStartedCallback != null) {
                onStartedCallback.accept(null);
            }

            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    class ClusterServerHandler extends SimpleChannelInboundHandler<ClusterMsg> {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                if (((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
                    System.out.println("[超时] 15秒未收到数据，踢除: " + ctx.channel());
                    ctx.close();
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ClusterMsg msg) {
            if (MsgType.HEARTBEAT == msg.getType()) {
                ctx.writeAndFlush(ClusterMsg.heartbeatAck(config.type, config.id));
                return;
            }
            // 委托给回调
            if (messageHandler != null) {
                messageHandler.handle(ctx, msg);
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (messageHandler != null) {
                messageHandler.onInactive(ctx);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}

