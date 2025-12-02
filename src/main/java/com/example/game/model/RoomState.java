package com.example.game.model;

import java.util.LinkedHashSet;
import java.util.Set;

public class RoomState {
    private final long roomId;
    private final Set<Long> players = new LinkedHashSet<>();

    public RoomState(long roomId) {
        this.roomId = roomId;
    }

    public long getRoomId() { return roomId; }

    public void addPlayer(long playerId) {
        players.add(playerId);
    }

    public void removePlayer(long playerId) {
        players.remove(playerId);
    }

    public Set<Long> getPlayers() {
        return players;
    }
}
