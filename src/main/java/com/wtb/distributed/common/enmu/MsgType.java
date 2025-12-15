package com.wtb.distributed.common.enmu;

public enum MsgType {
    // 系统级指令
    REGISTER,
    REGISTER_ACK,
    HEARTBEAT,
    HEARTBEAT_ACK,
    
    // 业务数据
    DATA;
}
