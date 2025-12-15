package com.wtb.model;

import java.io.Serializable;

public class GamePacket implements Serializable {

    private static final long serialVersionUID = 1L;

    private int cmd;          // 命令号，例如 1001=登录 2001=进房间
    private long playerId;    // 谁发的
    private String payload;   // 简单起见先用 JSON/String

    public GamePacket() {}

    public GamePacket(int cmd, long playerId, String payload) {
        this.cmd = cmd;
        this.playerId = playerId;
        this.payload = payload;
    }

    public int getCmd() { return cmd; }
    public void setCmd(int cmd) { this.cmd = cmd; }

    public long getPlayerId() { return playerId; }
    public void setPlayerId(long playerId) { this.playerId = playerId; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "GamePacket{" +
                "cmd=" + cmd +
                ", playerId=" + playerId +
                ", payload='" + payload + '\'' +
                '}';
    }
}
