package com.example.game.session;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Map<Channel, Session> CHANNEL_SESSION = new ConcurrentHashMap<>();
    private static final Map<Long, Session> PLAYER_SESSION = new ConcurrentHashMap<>();

    public static Session bind(Channel ch) {
        Session s = new Session(ch);
        CHANNEL_SESSION.put(ch, s);
        return s;
    }

    public static void unbind(Channel ch) {
        Session s = CHANNEL_SESSION.remove(ch);
        if (s != null && s.getPlayerId() != 0) {
            PLAYER_SESSION.remove(s.getPlayerId());
        }
    }

    public static void bindPlayer(long playerId, Channel ch) {
        Session s = CHANNEL_SESSION.get(ch);
        if (s != null) {
            s.setPlayerId(playerId);
            PLAYER_SESSION.put(playerId, s);
        }
    }

    public static Session getByPlayerId(long playerId) {
        return PLAYER_SESSION.get(playerId);
    }

    public static void sendToPlayer(long playerId, Object msg) {
        Session s = PLAYER_SESSION.get(playerId);
        if (s != null) {
            s.send(msg);
        }
    }
}
