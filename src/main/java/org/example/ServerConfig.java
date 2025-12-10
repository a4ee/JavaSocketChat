package org.example;

import java.util.Scanner;

public class ServerConfig {
    private final int port;
    private final int maxClient;
    private final int bufferSize = 1024;

    public ServerConfig(int port, int maxClient){
        this.port = port;
        this.maxClient = maxClient;

    }


    public int getPort() {
        return port;
    }

    public int getMaxClient() {
        return maxClient;
    }

    public int getBufferSize() {
        return bufferSize;
    }
}
