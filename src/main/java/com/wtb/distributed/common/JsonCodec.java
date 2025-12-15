package com.wtb.distributed.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

/**
 * 简单的 JSON 编解码器
 * 协议格式: [4字节长度][JSON字节]
 */
public class JsonCodec extends MessageToMessageCodec<ByteBuf, ClusterMsg> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void encode(ChannelHandlerContext ctx, ClusterMsg msg, List<Object> out) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(msg);
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        out.add(buf);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < 4) return;
        in.markReaderIndex();
        int len = in.readInt();
        if (in.readableBytes() < len) {
            in.resetReaderIndex();
            return;
        }
        byte[] bytes = new byte[len];
        in.readBytes(bytes);
        ClusterMsg msg = mapper.readValue(bytes, ClusterMsg.class);
        out.add(msg);
    }
}

