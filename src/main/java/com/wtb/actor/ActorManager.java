package com.wtb.actor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ActorManager {

    private static final Map<Long, PlayerActor> PLAYER_ACTORS = new ConcurrentHashMap<>();
    private static final Map<Long, RoomActor> ROOM_ACTORS = new ConcurrentHashMap<>();

    public static PlayerActor getOrCreatePlayerActor(long playerId) {
        return PLAYER_ACTORS.computeIfAbsent(playerId, PlayerActor::new);
    }

    public static RoomActor getOrCreateRoomActor(long roomId) {
        return ROOM_ACTORS.computeIfAbsent(roomId, RoomActor::new);
    }

    public static RoomActor getRoomActor(long roomId) {
        return ROOM_ACTORS.get(roomId);
    }

    public static void removePlayer(long playerId) {
        PLAYER_ACTORS.remove(playerId);
    }

    public static void removeRoom(long roomId) {
        ROOM_ACTORS.remove(roomId);
    }
}
