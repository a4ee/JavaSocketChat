package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ServerEventHandler {
    private final Selector selector;
    private final ClientManager clientManager;
    private final ServerConfig config;
    private final ByteBuffer buffer;

    public ServerEventHandler(Selector selector, ClientManager clientManager, ServerConfig config) {
        this.selector = selector;
        this.clientManager = clientManager;
        this.config = config;
        this.buffer = ByteBuffer.allocate(config.getBufferSize());
    }

    public void handleEvents() throws IOException {
        if (selector.select(100) > 0){
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();

            while (iterator.hasNext()){
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    acceptClient(key);
                } else if (key.isReadable()) {
                    readFromClient(key);
                }
            }
        }
    }

    private void acceptClient(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        clientManager.addClient(clientChannel);
        clientManager.sendToClient(clientChannel, "OK:Подключено к серверу");

        System.out.println("Новый клиент подключился. Всего: " + clientManager.getClientCount());
    }

    private void readFromClient(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        buffer.clear();

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                disconnectClient(clientChannel);
                return;
            }

            buffer.flip();
            String line = StandardCharsets.UTF_8.decode(buffer).toString().trim();

            if (line.isEmpty()) {
                return;
            }

            handleMessage(clientChannel, line);

        } catch (IOException e) {
            disconnectClient(clientChannel);
        }
    }

    private void handleMessage(SocketChannel client, String line) throws IOException {
        Message msg = Message.fromProtocol(line);
        String cmd = msg.getCommand();
        String data = msg.getData();

        switch (cmd) {
            case "USERNAME":
                handleUsername(client, data);
                break;

            case "CREATE":
                handleCreateRoom(client, data);
                break;

            case "JOIN":
                handleJoinRoom(client, data);
                break;

            case "LIST":
                handleListRooms(client);
                break;

            case "MSG":
                handleChatMessage(client, data);
                break;

            default:
                clientManager.sendToClient(client, "ERROR:Неизвестная команда");
        }
    }

    private void handleUsername(SocketChannel client, String username) throws IOException {
        clientManager.setUsername(client, username);
        clientManager.sendToClient(client, "OK:Имя установлено - " + username);
        System.out.println("Клиент установил имя: " + username);
    }

    private void handleCreateRoom(SocketChannel client, String roomName) throws IOException {
        if (clientManager.createRoom(roomName)) {
            clientManager.sendToClient(client, "OK:Комната создана - " + roomName);
            System.out.println("Создана комната: " + roomName);
        } else {
            clientManager.sendToClient(client, "ERROR:Комната уже существует");
        }
    }

    private void handleJoinRoom(SocketChannel client, String roomName) throws IOException {
        String username = clientManager.getUsername(client);

        if (clientManager.joinRoom(client, roomName)) {
            clientManager.sendToClient(client, "JOINED:" + roomName);

            String notification = "SYSTEM:" + username + " вошёл в комнату";
            clientManager.sendToRoom(roomName, notification);

            System.out.println(username + " вошёл в комнату: " + roomName);
        } else {
            clientManager.sendToClient(client, "ERROR:Не удалось войти в комнату");
        }
    }

    private void handleListRooms(SocketChannel client) throws IOException {
        List<String> rooms = clientManager.getRoomList();
        String roomList = String.join(";", rooms);
        clientManager.sendToClient(client, "ROOMS:" + roomList);
    }

    private void handleChatMessage(SocketChannel client, String message) throws IOException {
        String username = clientManager.getUsername(client);
        String room = clientManager.getCurrentRoom(client);

        if (room == null) {
            clientManager.sendToClient(client, "ERROR:Вы не в комнате");
            return;
        }

        clientManager.sendToRoomWithColor(client, room, username, message);
    }

    private void disconnectClient(SocketChannel client) throws IOException {
        String username = clientManager.getUsername(client);
        String room = clientManager.getCurrentRoom(client);

        if (room != null && username != null) {
            String notification = "SYSTEM:" + username + " покинул комнату";
            clientManager.sendToRoom(room, notification);
        }

        clientManager.removeClient(client);
        client.close();

        System.out.println("Клиент отключился: " + username + ". Осталось: " + clientManager.getClientCount());
    }
}
