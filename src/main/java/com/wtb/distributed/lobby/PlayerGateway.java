package com.wtb.distributed.lobby;


import com.wtb.distributed.common.ClusterMsg;
import com.wtb.distributed.common.enmu.NodeType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

/**
 * 玩家网关组件
 * 负责处理玩家连接和请求转发
 */
public class PlayerGateway {

    private final int port;
    private final ClusterAgent clusterAgent; // 依赖注入
    private final NioEventLoopGroup boss = new NioEventLoopGroup(1);
    private final NioEventLoopGroup worker = new NioEventLoopGroup();

    public PlayerGateway(int port, ClusterAgent clusterAgent) {
        this.port = port;
        this.clusterAgent = clusterAgent;
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
                                    .addLast(new StringDecoder(), new StringEncoder())
                                    .addLast(new PlayerHandler());
                        }
                    });

            b.bind(port).addListener(f -> {
                if (f.isSuccess()) {
                    System.out.println("Player Gateway 启动成功，端口: " + port);
                } else {
                    System.err.println("Player Gateway 启动失败: " + f.cause().getMessage());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void stop() {
        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }

    /**
     * 内部 Handler，直接使用外部类的 clusterAgent
     */
    class PlayerHandler extends SimpleChannelInboundHandler<String> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) {
            System.out.println("[玩家消息] " + msg);

            if (msg.startsWith("match:")) {
                String mode = msg.split(":")[1];
                
                String jsonContent = "{\"player\":\"" + ctx.channel().id() + "\", \"mode\":\"" + mode + "\"}";
                
                // 使用注入的 Agent 转发
                // TODO: 这里的 LOBBY_01 硬编码应该从 Config 里读，为了演示简化先这样
                ClusterMsg forwardMsg = ClusterMsg.data(NodeType.LOBBY, "LOBBY_01", jsonContent);

                clusterAgent.sendToMatch(forwardMsg);
                
                ctx.writeAndFlush("正在为您匹配: " + mode + "...\n");
            } else {
                ctx.writeAndFlush("未知指令，请输入 match:5v5\n");
            }
        }
    }
}

