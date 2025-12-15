package com.wtb.actor;

import com.wtb.model.GamePacket;
import com.wtb.model.RoomState;
import com.wtb.session.SessionManager;

public class RoomActor extends Actor {

    private final long roomId;
    private final RoomState state;

    public RoomActor(long roomId) {
        this.roomId = roomId;
        this.state = new RoomState(roomId);
    }

    public long getRoomId() { return roomId; }

    public void postJoin(long playerId) {
        post(() -> handleJoin(playerId));
    }

    public void postLeave(long playerId) {
        post(() -> handleLeave(playerId));
    }

    public void postPlayerAction(long playerId, GamePacket packet) {
        post(() -> handlePlayerAction(playerId, packet));
    }

    private void handleJoin(long playerId) {
        state.addPlayer(playerId);
        broadcast("player " + playerId + " joined room " + roomId);
    }

    private void handleLeave(long playerId) {
        state.removePlayer(playerId);
        broadcast("player " + playerId + " left room " + roomId);

        if (state.getPlayers().isEmpty()) {
            ActorManager.removeRoom(roomId);
        }
    }

    private void handlePlayerAction(long playerId, GamePacket packet) {
        broadcast("room " + roomId + " action from " + playerId
                + ": " + packet.getPayload());
    }

    private void broadcast(String msg) {
        for (Long pid : state.getPlayers()) {
            SessionManager.sendToPlayer(pid, new GamePacket(
                    9000, pid, msg
            ));
        }
    }
}
