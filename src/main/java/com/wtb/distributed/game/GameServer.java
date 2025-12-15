package com.wtb.distributed.game;


import com.wtb.distributed.common.ClusterClient;

public class GameServer {
    public static void main(String[] args) {
        // 支持通过 args[0] 传入配置后缀，例如传入 "1" 读取 "game_1.properties"
        // 默认为 "1"
        String suffix = args.length > 0 ? args[0] : "1";
        String configPath = "src/main/resources/game_" + suffix + ".properties";
        
        System.out.println("Game Server 正在启动，使用配置: " + configPath);

        // 启动 ClusterClient 连接 Match
        new ClusterClient(configPath) {}.start();
        
        // Game Server 通常不需要像 Lobby 那样开启对外的 Netty Server
        // 它的核心逻辑是在内部循环跑游戏逻辑，或者响应 Match 发过来的 "CREATE_ROOM" 指令
    }
}
