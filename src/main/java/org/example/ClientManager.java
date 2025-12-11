package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientManager {
    private final Set<SocketChannel> clients;
    private final Map<SocketChannel, String> usernames;
    private final Map<String, ChatRoom> rooms;
    private final Map<SocketChannel, String> clientRooms;
    private final Map<SocketChannel, String> userColors;
    private final Random random;

    public ClientManager(){
        this.clients = ConcurrentHashMap.newKeySet();
        this.usernames = new ConcurrentHashMap<>();
        this.rooms = new ConcurrentHashMap<>();
        this.clientRooms = new ConcurrentHashMap<>();
        this.userColors = new ConcurrentHashMap<>();
        this.random = new Random();

        rooms.put("Главная", new ChatRoom("Главная", "Сервер", 10));
    }

    public void addClient(SocketChannel channel){
        clients.add(channel);
        userColors.put(channel, generateRandomColor());
    }

    public void removeClient(SocketChannel channel){
        String room = clientRooms.get(channel);
        if (room != null && rooms.containsKey(room)) {
            rooms.get(room).removeMember(channel);
        }
        clients.remove(channel);
        usernames.remove(channel);
        clientRooms.remove(channel);
        userColors.remove(channel);
    }

    public void setUsername(SocketChannel channel, String username){
        usernames.put(channel, username);
    }

    public String getUsername(SocketChannel channel){
        return usernames.get(channel);
    }

    public int getClientCount(){
        return clients.size();
    }


    public boolean createRoom(String roomName) {
        if (rooms.containsKey(roomName)) {
            return false;
        }
        rooms.put(roomName, new ChatRoom(roomName, "Пользователь", 5));
        return true;
    }

    public boolean joinRoom(SocketChannel channel, String roomName) {
        if (!rooms.containsKey(roomName)) {
            return false;
        }

        String oldRoom = clientRooms.get(channel);
        if (oldRoom != null && rooms.containsKey(oldRoom)) {
            rooms.get(oldRoom).removeMember(channel);
        }


        ChatRoom room = rooms.get(roomName);
        if (room.addMember(channel)) {
            clientRooms.put(channel, roomName);
            return true;
        }
        return false;
    }

    public String getCurrentRoom(SocketChannel channel) {
        return clientRooms.get(channel);
    }

    public List<String> getRoomList() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, ChatRoom> entry : rooms.entrySet()) {
            result.add(entry.getKey() + " [" + entry.getValue().getMemberCount() + "]");
        }
        return result;
    }

    public void sendToRoom(String roomName, String message) throws IOException {
        if (!rooms.containsKey(roomName)) {
            return;
        }

        ChatRoom room = rooms.get(roomName);
        ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));

        for (SocketChannel client : room.getMembers()) {
            try {
                buffer.rewind();
                client.write(buffer);
            } catch (IOException e) {
            }
        }
    }

    public void sendToRoomWithColor(SocketChannel sender, String roomName, String username, String text) throws IOException {
        if (!rooms.containsKey(roomName)) {
            return;
        }

        String color = userColors.get(sender);
        String message = "CHAT:" + username + ":" + color + ":" + text;

        sendToRoom(roomName, message);
    }

    public void sendToClient(SocketChannel channel, String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
        channel.write(buffer);
    }

    public String getUserColor(SocketChannel channel) {
        return userColors.getOrDefault(channel, "0000FF");
    }

    private String generateRandomColor() {
        String[] colors = {"FF0000", "0000FF", "00AA00", "FF8800", "AA00AA", "008888", "CC0066", "6600CC"};
        return colors[random.nextInt(colors.length)];
    }
}
