package com.wtb.netty;

import com.wtb.model.GamePacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class GamePacketDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        // 此时 in 已经是一帧完整的数据（length 已经被拆掉了）
        // 按你的协议顺序读字段

        if (in.readableBytes() < 2 + 4) { // 至少要有 cmd(2) + playerId(4)
            return; // 不够就等等
        }

        in.markReaderIndex();

        short cmd = in.readShort();     // 2字节命令
        int playerId = in.readInt();    // 4字节玩家ID

        int bodyLen = in.readableBytes();
        byte[] body = new byte[bodyLen];
        in.readBytes(body);

        // 这里我假设 body 是 UTF-8 文本，你可以改成 Protobuf.parseFrom(body)
        String payload = new String(body, StandardCharsets.UTF_8);

        GamePacket packet = new GamePacket(cmd, playerId, payload);
        out.add(packet);
    }
}
