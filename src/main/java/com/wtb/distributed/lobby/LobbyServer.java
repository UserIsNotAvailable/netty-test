package com.wtb.distributed.lobby;

public class LobbyServer {

    public static void main(String[] args) {
        // 1. 准备配置
        String suffix = args.length > 0 ? args[0] : "1";
        String configPath = "src/main/resources/lobby_" + suffix + ".properties";
        
        System.out.println("Lobby Server [" + suffix + "] 正在启动...");

        // 2. 初始化并启动集群代理 (Cluster Agent)
        ClusterAgent clusterAgent = new ClusterAgent(configPath);
        clusterAgent.start();

        // 3. 初始化并启动玩家网关 (Player Gateway)
        // 将 Agent 注入给 Gateway
//        PlayerGateway gateway = new PlayerGateway(9000, clusterAgent);
//        gateway.start();
        
        // 4. 注册 Shutdown Hook (优雅关闭)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("正在停止 Lobby Server...");
//            gateway.stop();
        }));
    }
}
