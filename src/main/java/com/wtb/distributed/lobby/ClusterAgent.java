package com.wtb.distributed.lobby;

import com.twb.distributed.common.ClusterClient;
import com.twb.distributed.common.ClusterMsg;

/**
 * 集群代理组件
 * 负责与 Match Server 的所有交互
 */
public class ClusterAgent {

    private final ClusterClient client;

    public ClusterAgent(String configPath) {
        // 使用匿名内部类实例化 ClusterClient
        this.client = new ClusterClient(configPath) {};
    }

    public void start() {
        client.start();
    }

    public void sendToMatch(ClusterMsg msg) {
        client.send(msg);
    }
}

