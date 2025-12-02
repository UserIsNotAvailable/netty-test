package com.example.game.netty;

import com.example.game.actor.ActorManager;
import com.example.game.actor.PlayerActor;
import com.example.game.model.GamePacket;
import com.example.game.session.SessionManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class GameServerHandler extends SimpleChannelInboundHandler<GamePacket> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        SessionManager.bind(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        SessionManager.unbind(ctx.channel());
        // TODO: 可以在这里根据 playerId 清理 PlayerActor
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GamePacket msg) {
        long playerId = msg.getPlayerId();
        if (playerId == 0) {
            // 简单 demo：把 payload 当作 playerId
            try {
                playerId = Long.parseLong(msg.getPayload());
                msg.setPlayerId(playerId);
            } catch (NumberFormatException e) {
                System.out.println("Invalid playerId in payload: " + msg);
            }
        }

        if (playerId != 0) {
            SessionManager.bindPlayer(playerId, ctx.channel());
        }

        PlayerActor actor = ActorManager.getOrCreatePlayerActor(playerId);
        actor.onPacket(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
