package com.wtb.model;

public class PlayerState {
    private final long playerId;
    private String name;
    private long roomId;
    private int hp = 100;

    public PlayerState(long playerId) {
        this.playerId = playerId;
    }

    public long getPlayerId() { return playerId; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public long getRoomId() { return roomId; }

    public void setRoomId(long roomId) { this.roomId = roomId; }

    public int getHp() { return hp; }

    public void damage(int amount) {
        hp = Math.max(0, hp - amount);
    }
}
