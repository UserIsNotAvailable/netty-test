package com.wtb.distributed.common;

import com.twb.distributed.common.enmu.MsgType;
import com.twb.distributed.common.enmu.NodeType;

import java.io.Serializable;

public class ClusterMsg implements Serializable {
    private MsgType type;    //
    private String sourceId; // 发送者ID
    private NodeType sourceType;
    private String content;  // JSON 内容

    public ClusterMsg() {
    }

    ClusterMsg(MsgType type, NodeType sourceType, String sourceId, String content) {
        this.type = type;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.content = content;
    }

    public static ClusterMsg register(NodeType type, String id) {
        return new ClusterMsg(MsgType.REGISTER, type, id, "");
    }

    public static ClusterMsg registerAck(NodeType type, String id) {
        return new ClusterMsg(MsgType.REGISTER_ACK, type, id, "OK");
    }

    public static ClusterMsg heartbeat(NodeType type, String id) {
        return new ClusterMsg(MsgType.HEARTBEAT, type, id, "");
    }

    public static ClusterMsg heartbeatAck(NodeType type, String id) {
        return new ClusterMsg(MsgType.HEARTBEAT_ACK, type, id, "");
    }

    public static ClusterMsg data(NodeType type, String id, String jsonContent) {
        return new ClusterMsg(MsgType.DATA, type, id, jsonContent);
    }

    // Getters / Setters
    public MsgType getType() {
        return type;
    }

    public void setType(MsgType type) {
        this.type = type;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public NodeType getSourceType() {
        return sourceType;
    }

    public void setSourceType(NodeType sourceType) {
        this.sourceType = sourceType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return "Msg{" + type + " from " + sourceType + ":" + sourceId + "}";
    }
}
