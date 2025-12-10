package org.example;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Client {
    private final String host;
    private final int port;
    private SocketChannel socketChannel;
    private final ByteBuffer readBuffer;
    private String username;
    private volatile boolean running;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
        this.readBuffer = ByteBuffer.allocate(1024);
        this.running = true;
    }

    public void start() throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        System.out.println("Подключение к серверу " + host + ":" + port + "...");

        if (!socketChannel.connect(new InetSocketAddress(host, port))) {
            while (!socketChannel.finishConnect()) {
                System.out.print(".");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        System.out.println("\nУспешно подключено к серверу!");

        readServerMessage();

        setupUsername();

        Thread readThread = new Thread(this::readMessages);
        Thread writeThread = new Thread(this::writeMessages);

        readThread.start();
        writeThread.start();

        try {
            readThread.join();
            writeThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void setupUsername() throws IOException {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Введите ваше имя: ");
        username = scanner.nextLine();

        sendMessage(username);

        readServerMessage();
    }

    private void readMessages() {
        try {
            while (running) {
                readBuffer.clear();
                int bytesRead = socketChannel.read(readBuffer);

                if (bytesRead > 0) {
                    readBuffer.flip();
                    String message = StandardCharsets.UTF_8.decode(readBuffer).toString();
                    System.out.print(message);
                } else if (bytesRead == -1) {
                    System.out.println("\nСоединение с сервером разорвано.");
                    running = false;
                    break;
                }

                Thread.sleep(10);
            }
        } catch (IOException | InterruptedException e) {
            if (running) {
                System.out.println("Ошибка чтения сообщений: " + e.getMessage());
            }
        }
    }

    private void writeMessages() {
        Scanner scanner = new Scanner(System.in);

        try {
            while (running) {
                String userInput = scanner.nextLine();

                if (!userInput.trim().isEmpty()) {
                    if (userInput.equalsIgnoreCase("stop")) {
                        sendMessage("stop");
                        running = false;
                        break;
                    }

                    sendMessage(userInput);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.out.println("Ошибка отправки сообщения: " + e.getMessage());
            }
        } finally {
            scanner.close();
            closeConnection();
        }
    }

    private void sendMessage(String message) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));

        while (buffer.hasRemaining()) {
            socketChannel.write(buffer);
        }
    }

    private void readServerMessage() throws IOException {
        readBuffer.clear();
        int bytesRead = socketChannel.read(readBuffer);

        if (bytesRead > 0) {
            readBuffer.flip();
            String message = StandardCharsets.UTF_8.decode(readBuffer).toString();
            System.out.print(message);
        }
    }

    private void closeConnection() {
        running = false;

        try {
            if (socketChannel != null && socketChannel.isOpen()) {
                socketChannel.close();
            }
        } catch (IOException e) {
            System.out.println("Ошибка при закрытии соединения: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            try {
                System.out.print("Введите адрес сервера (localhost): ");
                String address = scanner.nextLine().trim();
                if (address.isEmpty()) address = "localhost";

                System.out.print("Введите порт сервера: ");
                int port = Integer.parseInt(scanner.nextLine());

                Client client = new Client(address, port);
                client.start();

                break;

            } catch (NumberFormatException e) {
                System.out.println("Ошибка: порт должен быть числом. Попробуйте еще раз.");
            } catch (IOException e) {
                System.out.println("Ошибка подключения: " + e.getMessage() + ". Попробуйте еще раз.");
            }
        }
        scanner.close();
    }
}