package com.wtb.session;

import io.netty.channel.Channel;

public class Session {

    private final Channel channel;
    private long playerId; // 登录后绑定

    public Session(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() { return channel; }

    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }

    public void send(Object msg) {
        channel.writeAndFlush(msg);
    }
}
