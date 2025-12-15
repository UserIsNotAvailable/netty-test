package com.wtb.distributed.common;

import io.netty.channel.ChannelHandlerContext;

// 定义回调接口
public interface MessageHandler {
    void handle(ChannelHandlerContext ctx, ClusterMsg msg);
    void onInactive(ChannelHandlerContext ctx);
}

