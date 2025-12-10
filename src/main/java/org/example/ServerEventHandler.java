package org.example;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
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
            Set<SelectionKey> selectionKeySet = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectionKeySet.iterator();


            while (iterator.hasNext()){
                SelectionKey key = iterator.next();
                iterator.remove();

                if (key.isAcceptable()) {
                    handleAccept(key);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel =serverChannel.accept();
        if (!clientManager.canAcceptNewClient(config.getMaxClient())){
            rejectClient(clientChannel);
            return;
        }
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);

        clientManager.addClient(clientChannel);

        clientChannel.write(ByteBuffer.wrap("\n".getBytes(StandardCharsets.UTF_8)));

        System.out.println("Новое подключение, всего клиентов: " + clientManager.getClientCount());


    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        buffer.clear();

        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                disconnectClient(clientChannel);
                return;
            }


            buffer.flip();
            String message = StandardCharsets.UTF_8.decode(buffer).toString().trim();

            if (message.isEmpty()) {
                return;
            }

            processMessage(clientChannel, message);
        } catch (IOException e) {
            disconnectClient(clientChannel);
        }

    }

    private void processMessage(SocketChannel clientChannel, String message) throws IOException {

        if (!clientManager.hasUsername(clientChannel)){
            handleUsernameRegistration(clientChannel, message);
            return;
        }

        String username = clientManager.getUsername(clientChannel);

        if (message.equalsIgnoreCase("stop")){
            disconnectClient(clientChannel);
            return;
        }

        if (message.length() > 100){
            sendErrorMessage(clientChannel, "Лимит по количеству символов 100");
            return;
        }

        broadcastChatMessage(username, message);
    }

    private void broadcastChatMessage(String username, String message) throws IOException {
        String formMessage = "[" + username + "]: " + message;
        clientManager.broadcastMessage(formMessage);
    }

    private void sendErrorMessage(SocketChannel clientChannel, String errorMessage) throws IOException {
        clientChannel.write(ByteBuffer.wrap((errorMessage + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    private void handleUsernameRegistration(SocketChannel clientChannel, String username) throws IOException {
        clientManager.setUsername(clientChannel, username);
        String welcomeMessage = "SERVER: Добро пожаловать в чат, " + clientManager.getUsername(clientChannel) + "!\n";
        clientChannel.write(ByteBuffer.wrap(welcomeMessage.getBytes(StandardCharsets.UTF_8)));

        clientManager.broadcastMessage("SERVER: " + clientManager.getUsername(clientChannel) + " присоединился к чату");
        System.out.println("Клиент зарегистрирован как: " + clientManager.getUsername(clientChannel));
    }

    private void disconnectClient(SocketChannel clientChannel) throws IOException {
        String username = clientManager.getUsername(clientChannel);

        clientManager.removeClient(clientChannel);

        if (clientChannel.isOpen()) {
            clientChannel.close();
        }

        clientManager.broadcastMessage("SERVER: " + username + " покинул чат");
        System.out.println("Клиент " + username + " отключен. Осталось клиентов: " + clientManager.getClientCount());
    }


    private void rejectClient(SocketChannel clientChannel) throws IOException {
        String message = "Достигнут лимит участников в " + config.getMaxClient() + " ,пожалуйста попробуйте позже";
        clientChannel.write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
        clientChannel.close();
        System.out.println("Отклонено подключение - достигнут лимит клиентов");
    }
}
