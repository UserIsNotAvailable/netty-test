package com.example.game.netty;

import com.example.game.model.GamePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import java.nio.charset.StandardCharsets;

public class GamePacketEncoder extends MessageToByteEncoder<GamePacket> {

    @Override
    protected void encode(ChannelHandlerContext ctx, GamePacket msg, ByteBuf out) {
        // 把 GamePacket 转成二进制：
        // [Length(4)] [Cmd(2)] [PlayerId(4)] [Body...]

        byte[] body = msg.getPayload() != null
                ? msg.getPayload().getBytes(StandardCharsets.UTF_8)
                : new byte[0];

        int length = 4 + 2 + 4 + body.length; // length字段自己约定：包括整个包，还是不包含自身？

        // 小端/大端问题，Netty 默认是 big-endian，如果你们 old server 是小端，要用 writeIntLE / readIntLE
        out.writeInt(length);
        out.writeShort((short) msg.getCmd());
        out.writeInt((int) msg.getPlayerId());
        out.writeBytes(body);
    }
}
