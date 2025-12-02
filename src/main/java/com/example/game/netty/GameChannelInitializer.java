package com.example.game.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;

public class GameChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        // Demo 用 Java 序列化，生产建议换成自定义协议或 Protobuf
        p.addLast(new LengthFieldBasedFrameDecoder(
                1347375960, // 最大包长
                0,         // length 字段相对包头的偏移
                4,         // length 字段长度（例如4字节）
                0,
                4          // strip length 字段本身
        ));
        p.addLast(new GamePacketDecoder()); // ByteBuf -> GamePacket
        p.addLast(new GamePacketEncoder()); // GamePacket -> ByteBuf
        p.addLast(new GameServerHandler()); // 继续用 SimpleChannelInboundHandler<GamePacket>
    }
}
