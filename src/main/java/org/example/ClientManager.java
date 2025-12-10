package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private final Set<SocketChannel> connectionClients;
    private final Map<SocketChannel, String> clientUsername;


    public ClientManager(){
        this.connectionClients = ConcurrentHashMap.newKeySet();
        this.clientUsername = new ConcurrentHashMap<>();
    }

    public boolean canAcceptNewClient(int maxClients){
        return connectionClients.size() < maxClients;
    }

    public void addClient(SocketChannel channel){
        connectionClients.add(channel);
    }

    public void removeClient(SocketChannel channel){
        connectionClients.remove(channel);
        clientUsername.remove(channel);
    }
    public int getClientCount(){
        return connectionClients.size();
    }

    public String getUsername(SocketChannel channel){
        return clientUsername.get(channel);
    }

    public void setUsername(SocketChannel channel, String username){
        String name = (username != null && !username.trim().isEmpty() ? username.trim() : "Аноним");
        clientUsername.put(channel, name);
    }

    public boolean hasUsername(SocketChannel channel){
        return clientUsername.containsKey(channel);
    }

    public void broadcastMessage(String message) throws IOException {
        byte[] messageBytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
        ByteBuffer messageBuffer = ByteBuffer.wrap(messageBytes);

        for (SocketChannel client: connectionClients){
            if (client.isConnected()){
                messageBuffer.rewind();
                client.write(messageBuffer);
            }
        }
    }
}
