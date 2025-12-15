package com.wtb.actor;

import com.wtb.model.GamePacket;
import com.wtb.model.PlayerState;

public class PlayerActor extends Actor {

    private final long playerId;
    private final PlayerState state;

    public PlayerActor(long playerId) {
        this.playerId = playerId;
        this.state = new PlayerState(playerId);
    }

    public long getPlayerId() { return playerId; }

    public void onPacket(GamePacket packet) {
        post(() -> handlePacket(packet));
    }

    private void handlePacket(GamePacket packet) {
        switch (packet.getCmd()) {
            case 1001: // 登录
                handleLogin(packet);
                break;
            case 2001: // 加入房间
                handleJoinRoom(packet);
                break;
            case 2002: // 离开房间
                handleLeaveRoom(packet);
                break;
            case 3001: // 房间内操作
                handleRoomAction(packet);
                break;
            default:
                System.out.println("Unknown cmd: " + packet);
                break;
        }
    }

    private void handleLogin(GamePacket packet) {
        System.out.println("Player " + playerId + " logged in. payload=" + packet.getPayload());
        // TODO: 登录校验 & DB 加载
    }

    private void handleJoinRoom(GamePacket packet) {
        long roomId = Long.parseLong(packet.getPayload());
        RoomActor room = ActorManager.getOrCreateRoomActor(roomId);
        room.postJoin(playerId);
        state.setRoomId(roomId);
    }

    private void handleLeaveRoom(GamePacket packet) {
        long roomId = state.getRoomId();
        if (roomId == 0) return;

        RoomActor room = ActorManager.getRoomActor(roomId);
        if (room != null) {
            room.postLeave(playerId);
        }
        state.setRoomId(0);
    }

    private void handleRoomAction(GamePacket packet) {
        long roomId = state.getRoomId();
        if (roomId == 0) {
            System.out.println("Player " + playerId + " action but not in room.");
            return;
        }
        RoomActor room = ActorManager.getRoomActor(roomId);
        if (room != null) {
            room.postPlayerAction(playerId, packet);
        }
    }
}
