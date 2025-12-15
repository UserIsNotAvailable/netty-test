package com.wtb;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import io.netty.util.Recycler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NettyServer {
    static class GamePacket {
        private static final Recycler<GamePacket> RECYCLER = new Recycler<GamePacket>() {
            @Override
            protected GamePacket newObject(Handle<GamePacket> handle) {
                return new GamePacket(handle);
            }
        };

        private final Recycler.Handle<GamePacket> handle;
        public int cmd;
        public String body;

        // 私有构造
        private GamePacket(Recycler.Handle<GamePacket> handle) {
            this.handle = handle;
        }

        // 静态工厂方法
        public static GamePacket newInstance(int cmd, String body) {
            GamePacket packet = RECYCLER.get();
            packet.cmd = cmd;
            packet.body = body;
            return packet;
        }

        // 回收方法
        public void recycle() {
            this.cmd = 0;
            this.body = null;
            handle.recycle(this);
        }

        @Override
        public String toString() {
            return "cmd=" + cmd + ", body=" + body;
        }
    }

    static class GamePacketDecoder extends ByteToMessageDecoder {
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {

            // 标记当前读取位置，如果数据不够可以回滚
            if (in.readableBytes() < 4) {
                return;
            }
            in.markReaderIndex();

            int length = in.readInt();

            // 如果剩余可读字节小于包体长度（length - 4），说明半包，重置读取位置，等待下次
            if (in.readableBytes() < length - 4) {
                in.resetReaderIndex();
                return;
            }

            short cmd = in.readShort();
            byte[] bytes = new byte[length - 6]; // length(4) + cmd(2) + body
            in.readBytes(bytes);
            String body = new String(bytes, StandardCharsets.UTF_8);

            // 【修改】使用对象池创建
            out.add(GamePacket.newInstance(cmd, body));
        }
    }

    // 继承 MessageToMessageEncoder，因为我们要输出任意类型的 ByteBuf，而不是被限制在传入的那个 buf 里
    static class ZeroCopyEncoder extends MessageToMessageEncoder<GamePacket> {
        @Override
        protected void encode(ChannelHandlerContext ctx, GamePacket msg, List<Object> out) {
            byte[] bodyBytes = msg.body.getBytes(StandardCharsets.UTF_8);
            int length = 4 + 2 + bodyBytes.length;

            // 1. 头部 Buf (分配在堆外)
            ByteBuf headerBuf = ctx.alloc().directBuffer(6);
            headerBuf.writeInt(length);
            headerBuf.writeShort(msg.cmd);

            // 2. 身体 Buf (直接 wrap 现有的数组，零拷贝)
            // 注意：实际场景中如果是 FileRegion 效果更好，这里演示 byte[] 的 wrap
            ByteBuf bodyBuf = Unpooled.wrappedBuffer(bodyBytes);

            // 3. 缝合
            CompositeByteBuf compositeBuf = ctx.alloc().compositeBuffer();
            // addComponents(true, ...) true 表示自动增加 writerIndex
            compositeBuf.addComponents(true, headerBuf, bodyBuf);

            System.out.println("--- 使用了缝合怪 (CompositeByteBuf) ---");
            System.out.println("Header ref: " + headerBuf.refCnt());
            System.out.println("Body ref: " + bodyBuf.refCnt());
            System.out.println("Composite ref: " + compositeBuf.refCnt());

            out.add(compositeBuf);
//            throw new RuntimeException("编码炸了");
        }
    }

    @ChannelHandler.Sharable
    static class GamePacketEncoder extends MessageToByteEncoder<GamePacket> {
        @Override
        protected void encode(ChannelHandlerContext ctx, GamePacket msg, ByteBuf out) {

            byte[] bytes = msg.body.getBytes(StandardCharsets.UTF_8);
            int length = 4 + 2 + bytes.length;

            out.writeInt(length);
            out.writeShort(msg.cmd);
            out.writeBytes(bytes);

            // 【关键】编码完成后，回收对象！实现闭环。
            msg.recycle();
        }
    }

    static class ByteBufTestHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
//            ByteBuf buf = (ByteBuf) msg;
//
//            System.out.println("--- ByteBuf 实验室 ---");
//            System.out.println("类型: " + buf.getClass().getSimpleName());
//            System.out.println("容量: " + buf.capacity());
//            System.out.println("读指针(readerIndex): " + buf.readerIndex());
//            System.out.println("写指针(writerIndex): " + buf.writerIndex());
//            System.out.println("可读字节: " + buf.readableBytes());
//            System.out.println("引用计数: " + buf.refCnt());
//
//            // 必须透传，否则后面的 Handler 收不到数据
//            ctx.fireChannelRead(msg);

            ByteBuf buf = (ByteBuf) msg;

            // 1. 假设前4字节是长度，第5-6字节是Cmd。
            // 我们想“偷窥”一下 Cmd，但不想影响原来的 buf。

            // slice() 方法创建了一个新的 ByteBuf 对象，但它指向的内存地址和原 buf 是一模一样的！
            // offset=4, length=2
            ByteBuf cmdBuf = buf.slice(4, 2);

            // 必须先标记，读完再重置，否则虽然是 slice，但如果 slice 也有 readerIndex...
            // 其实 slice 出来的 buf 有自己独立的 readerIndex，互不影响！这是最爽的。
            short cmd = cmdBuf.readShort();

            System.out.println("--- 偷窥 Cmd: " + cmd + " ---");

            // 思考题：如果我在这里修改 cmdBuf 的内容，原 buf 会变吗？
            // 答案：会！因为内存是共享的。
            // cmdBuf.setShort(0, 999); // 这行代码会篡改原始数据！

            ctx.fireChannelRead(msg);
        }
    }

    static final GamePacketEncoder SHARED = new GamePacketEncoder();

    /**
     * 心跳检测处理器
     * 配合 IdleStateHandler 使用，处理空闲事件
     */
    static class HeartBeatHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent) {
                IdleStateEvent event = (IdleStateEvent) evt;
                // 读空闲：说明客户端很久没发数据了
                if (event.state() == IdleState.READER_IDLE) {
                    System.out.println("Server: 30秒没收到客户端数据，关闭假死连接: " + ctx.channel());
                    ctx.close();
                }
            } else {
                // 不是 Idle 事件，继续往下传
                super.userEventTriggered(ctx, evt);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        EventLoopGroup boss;
        EventLoopGroup worker;
        // 独立的业务线程池，用来处理耗时业务（如数据库查询），避免卡死 IO 线程
        // 16 个线程，专门干脏活累活
        EventExecutorGroup businessGroup = new DefaultEventExecutorGroup(16);

        if (KQueue.isAvailable()) {
            boss = new KQueueEventLoopGroup(1);
            worker = new KQueueEventLoopGroup();
        } else if (Epoll.isAvailable()) {
            boss = new EpollEventLoopGroup(1);
            worker = new EpollEventLoopGroup();
            System.out.println("Using Epoll Model");
        } else {
            boss = new NioEventLoopGroup(1);
            worker = new NioEventLoopGroup();
            System.out.println("Using NIO Model");
        }

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, worker)
                    // 自动适配 Channel 类型
                    .channel(KQueue.isAvailable() ? KQueueServerSocketChannel.class
                            : Epoll.isAvailable() ? EpollServerSocketChannel.class
                            : NioServerSocketChannel.class)

                    // 【Boss 线程配置】
                    // SO_BACKLOG: 全连接队列大小。TCP 三次握手后，Accept 之前的队列。
                    // 设小了（比如10），高并发时客户端会报 Connection Refused。
                    .option(ChannelOption.SO_BACKLOG, 1024)

                    // 【Boss 线程 Handler】
                    // 作用：监控新连接接入。通常只放 LoggingHandler。
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) throws Exception {
                            System.out.println("Boss Channel 启动成功，绑定端口: " + ctx.channel().localAddress());
                            super.channelActive(ctx);
                        }

                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            // 这里的 msg 是 NioSocketChannel (新连接)
                            // 我们可以做 IP 黑名单过滤！如果不想要这个连接，直接 close 掉，不传给 worker。
                            System.out.println("Boss 收到新连接请求: " + msg);
                            ctx.fireChannelRead(msg); // 必须透传！否则 Worker 收不到。
                        }
                    })

                    .attr(AttributeKey.valueOf("ServerVersion"), "1")

                    // 【Worker 线程配置】
                    // TCP_NODELAY: 禁用 Nagle 算法。也就是“有数据立刻发”，哪怕只有一个字节。
                    // 默认是 false (开启 Nagle)，会凑满一个包再发，导致几十毫秒延迟。游戏/RPC 必开 true。
                    .childOption(ChannelOption.TCP_NODELAY, true)

                    // SO_KEEPALIVE: TCP 层面的心跳保活。
                    .childOption(ChannelOption.SO_KEEPALIVE, true)

                    .childAttr(AttributeKey.valueOf("TYPE"), "WORKER")

                    // 【Worker 线程 Handler】
                    // 作用：处理读写业务逻辑。
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    // 【新增】空闲检测 Handler
                                    // 30秒没有读事件 (ReaderIdle)，触发 userEventTriggered
                                    .addLast(new IdleStateHandler(30, 0, 0, TimeUnit.SECONDS))
                                    // 【新增】处理心跳事件的 Handler
                                    .addLast(new HeartBeatHandler())

                                    .addLast(new ByteBufTestHandler())
                                    .addLast(new GamePacketDecoder())
                                    .addLast(new ZeroCopyEncoder())
//                                    .addLast(SHARED)
                                    // 【关键】把业务 Handler 扔给 businessGroup 跑！
                                    .addLast(businessGroup, new SimpleChannelInboundHandler<GamePacket>() {
                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) {
                                            System.out.println("New client connected: " + ctx.channel());
                                        }

                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, GamePacket msg) {
                                            // 这行代码现在是在 businessGroup 的线程里跑的！
                                            System.out.println("Received in " + Thread.currentThread().getName() + ": " + msg);

                                            // 必须回传 GamePacket，否则 Encoder 不会处理
                                            // 添加 FIRE_EXCEPTION_ON_FAILURE 监听器，确保写操作的异常能被 exceptionCaught 捕获
                                            ctx.writeAndFlush(GamePacket.newInstance(msg.cmd, "Server Echo: " + msg.body))
                                                    .addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);

                                            // 【关键】业务处理完了，msg (GamePacket) 已经没用了，必须手动回收！
                                            // 否则池子里的对象会只出不进，导致内存泄漏
                                            msg.recycle();
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            System.err.println("🔥 业务逻辑发生异常: " + cause.getMessage());
                                            cause.printStackTrace();
                                            // 发生异常时，通常应该关闭连接，避免数据错乱
                                            ctx.close();
                                        }

                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) {
                                            System.out.println("Client disconnected: " + ctx.channel());
                                        }
                                    })

                            ;
                        }
                    });

            ChannelFuture f = b
                    .localAddress(new InetSocketAddress("127.0.0.1", 9000))
                    .validate()
                    .bind()
                    .sync();
            System.out.println("Netty server started on port 9000");
            f.channel().closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
            businessGroup.shutdownGracefully();
        }
    }
}
