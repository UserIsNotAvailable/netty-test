package com.wtb;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class NettyClient {

    private static volatile Channel channel;
    private static int retryDelay = 1; // 秒

    // 复用 Server 的协议对象，实际项目中通常会提取到公共 jar 包
    static class GamePacket {
        public int cmd;
        public String body;

        public GamePacket(int cmd, String body) {
            this.cmd = cmd;
            this.body = body;
        }

        @Override
        public String toString() {
            return "GamePacket{cmd=" + cmd + ", body='" + body + "'}";
        }
    }

    // 客户端也需要同样的 Encoder
    static class GamePacketEncoder extends MessageToByteEncoder<GamePacket> {
        @Override
        protected void encode(ChannelHandlerContext ctx, GamePacket msg, ByteBuf out) {
            byte[] bytes = msg.body.getBytes(StandardCharsets.UTF_8);
            int length = 4 + 2 + bytes.length; // 4(length) + 2(cmd) + body
            out.writeInt(length);
            out.writeShort(msg.cmd);
            out.writeBytes(bytes);
        }
    }

    // 客户端也需要同样的 Decoder
    static class GamePacketDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() < 4) return;
            in.markReaderIndex();
            int length = in.readInt();
            if (in.readableBytes() < length - 4) {
                in.resetReaderIndex();
                return;
            }
            short cmd = in.readShort();
            byte[] bytes = new byte[length - 6];
            in.readBytes(bytes);
            out.add(new GamePacket(cmd, new String(bytes, StandardCharsets.UTF_8)));
        }
    }

    /**
     * 客户端心跳 Handler
     * 配合 IdleStateHandler (WriteIdle) 使用
     */
    static class ClientHeartBeatHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                if (event.state() == IdleState.WRITER_IDLE) {
                    System.out.println("Client: 5秒没发数据，发送心跳保活...");
                    // Cmd 0 代表心跳
                    // 【修复】必须使用 ctx.channel().write() 从 Tail 开始传播，否则会跳过后面的 Encoder
                    ctx.writeAndFlush(new GamePacket(0, "Ping-Pong"));
                }
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast(new GamePacketEncoder())
                                // 【新增】空闲检测：5秒没写数据，触发 WRITER_IDLE
                                .addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS))
                                // 【新增】心跳包发送
                                .addLast(new ClientHeartBeatHandler())

                                .addLast(new GamePacketDecoder())
                                .addLast(new SimpleChannelInboundHandler<GamePacket>() {
                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, GamePacket msg) {
                                        System.out.println("收到服务器响应: " + msg);
                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        System.out.println("与服务器断开连接！");
                                        // 触发重连
                                        connect(b, group);
                                        super.channelInactive(ctx);
                                    }
                                });
                    }
                });

        // 首次连接
        connect(b, group);

        // 主线程负责读取控制台输入
        Scanner scanner = new Scanner(System.in);
        System.out.println("等待连接...");

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if ("exit".equals(line)) break;

            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new GamePacket(1, line));
            } else {
                System.out.println("当前未连接，无法发送。");
            }
        }

        group.shutdownGracefully();
    }

    private static void connect(Bootstrap b, EventLoopGroup group) {
        b.connect("127.0.0.1", 9000).addListener(future -> {
            if (future.isSuccess()) {
                System.out.println("连接服务器成功！");
                channel = ((ChannelFuture) future).channel();
                retryDelay = 1; // 成功后重置重连间隔
            } else {
                int delay = retryDelay;
                // 指数退避：每次 * 2，最大 30秒
                retryDelay = Math.min(retryDelay * 2, 30);

                System.out.println("连接失败，" + delay + "秒后重试...");

                // 定时任务调度重连
                group.schedule(() -> connect(b, group), delay, TimeUnit.SECONDS);
            }
        });
    }
}