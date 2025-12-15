package com.wtb.distributed.common;


import com.wtb.distributed.common.enmu.NodeType;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ServerConfig {
    public String id;
    public String ip;
    public int port;
    public NodeType type;
    

    public static ServerConfig load(String path) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(path)) {
            props.load(in);
        } catch (Exception e) {
            throw new RuntimeException("无法加载配置文件: " + path);
        }

        ServerConfig config = new ServerConfig();
        config.id = props.getProperty("server.id");
        config.ip = props.getProperty("server.ip", "127.0.0.1");
        config.port = Integer.parseInt(props.getProperty("server.port", "0"));
        config.type = NodeType.valueOf(props.getProperty("server.type"));
        return config;
    }
}

