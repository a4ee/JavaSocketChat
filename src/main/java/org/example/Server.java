package org.example;

import java.io.*;
import java.net.*;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.LinkedList;
import java.util.Scanner;

public class Server {
    private final ServerConfig config;
    private final ClientManager clientManager;
    private ServerSocketChannel serverChannel;
    private boolean running;
    private Selector selector;


    public Server(int port, int maxClient){
        this.config = new ServerConfig(port,maxClient);
        this.clientManager = new ClientManager();
        this.running = true;
    }

    public static void main(String[] args) {
        while (true){
            int port = setPort();
            try{
                Server chatServer = new Server(port, 10);
                chatServer.startServer();
                break;

            } catch (BindException e){
                System.out.println("Порт " + port + " занят, попробуйте ввести другой." );
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static int setPort() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Введите порт для запуска сервера: ");
        return Integer.parseInt(scanner.nextLine());

    }

    private void startServer() throws IOException {
        initializeServer();
        System.out.println("Сервер запущен на порту " + config.getPort());
        ServerEventHandler eventHandler = new ServerEventHandler(selector, clientManager, config);

        while (running) {
            eventHandler.handleEvents();
        }
    }



    private void initializeServer() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(config.getPort()));

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }
}