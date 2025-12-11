package org.example;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ClientGUI extends JFrame {
    private SocketChannel socket;
    private String username;
    private String currentRoom;
    private boolean running = false;

    private JTextPane chatArea;
    private StyledDocument doc;
    private JTextField messageField;
    private JButton sendButton;
    private JLabel statusLabel;

    public ClientGUI() {
        setTitle("Чат клиент");
        setSize(600, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createGUI();
    }

    private void createGUI() {
        JPanel topPanel = new JPanel();
        JButton createRoomBtn = new JButton("Создать комнату");
        JButton joinRoomBtn = new JButton("Войти в комнату");
        JButton listRoomsBtn = new JButton("Список комнат");

        createRoomBtn.addActionListener(e -> createRoom());
        joinRoomBtn.addActionListener(e -> joinRoom());
        listRoomsBtn.addActionListener(e -> listRooms());

        topPanel.add(createRoomBtn);
        topPanel.add(joinRoomBtn);
        topPanel.add(listRoomsBtn);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 13));
        doc = chatArea.getStyledDocument();
        JScrollPane scrollPane = new JScrollPane(chatArea);

        JPanel inputPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        sendButton = new JButton("Отправить");
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        statusLabel = new JLabel("Не подключено");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputPanel, BorderLayout.CENTER);
        bottomPanel.add(statusLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void connect(String host, int port, String username) {
        this.username = username;

        new Thread(() -> {
            try {
                socket = SocketChannel.open();
                socket.configureBlocking(false);
                socket.connect(new InetSocketAddress(host, port));

                while (!socket.finishConnect()) {
                    Thread.sleep(50);
                }

                running = true;
                statusLabel.setText("Подключено к " + host + ":" + port);
                addMessage("Система: Подключено к серверу\n");
                sendCommand("USERNAME:" + username);

                startReading();

            } catch (Exception e) {
                addMessage("Ошибка: " + e.getMessage() + "\n");
            }
        }).start();
    }

    private void startReading() {
        new Thread(() -> {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            StringBuilder sb = new StringBuilder();

            while (running) {
                try {
                    buffer.clear();
                    int read = socket.read(buffer);

                    if (read > 0) {
                        buffer.flip();
                        String data = StandardCharsets.UTF_8.decode(buffer).toString();
                        sb.append(data);

                        int newlinePos;
                        while ((newlinePos = sb.indexOf("\n")) != -1) {
                            String line = sb.substring(0, newlinePos).trim();
                            sb.delete(0, newlinePos + 1);

                            if (!line.isEmpty()) {
                                handleServerMessage(line);
                            }
                        }
                    } else if (read == -1) {
                        addMessage("Система: Соединение разорвано\n");
                        running = false;
                        break;
                    }

                    Thread.sleep(10);
                } catch (Exception e) {
                    if (running) {
                        addMessage("Ошибка чтения: " + e.getMessage() + "\n");
                    }
                    running = false;
                    break;
                }
            }
        }).start();
    }

    private void handleServerMessage(String line) {
        Message msg = Message.fromProtocol(line);
        String cmd = msg.getCommand();
        String data = msg.getData();

        SwingUtilities.invokeLater(() -> {
            switch (cmd) {
                case "OK":
                    addMessage(data + "\n");
                    break;

                case "ERROR":
                    addMessage(" Ошибка: " + data + "\n");
                    break;

                case "JOINED":
                    currentRoom = data;
                    statusLabel.setText("Комната: " + currentRoom);
                    addMessage("Вы вошли в комнату: " + currentRoom + "\n");
                    break;

                case "CHAT":
                    String[] parts = data.split(":", 3);
                    if (parts.length >= 3) {
                        String user = parts[0];
                        String colorHex = parts[1];
                        String text = parts[2];

                        Color color = parseColor(colorHex);
                        addColoredMessage(user + ": ", color);
                        addMessage(text + "\n");
                    }
                    break;

                case "SYSTEM":
                    addMessage("[Система] " + data + "\n");
                    break;

                case "ROOMS":
                    showRooms(data);
                    break;
            }
        });
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        if (currentRoom == null) {
            addMessage("Сначала войдите в комнату!\n");
            return;
        }

        sendCommand("MSG:" + text);
        messageField.setText("");
    }

    private void createRoom() {
        String roomName = JOptionPane.showInputDialog(this, "Название комнаты:");
        if (roomName != null && !roomName.trim().isEmpty()) {
            sendCommand("CREATE:" + roomName.trim());
        }
    }

    private void joinRoom() {
        String roomName = JOptionPane.showInputDialog(this, "Название комнаты:");
        if (roomName != null && !roomName.trim().isEmpty()) {
            sendCommand("JOIN:" + roomName.trim());
        }
    }

    private void listRooms() {
        sendCommand("LIST:");
    }

    private void showRooms(String roomsData) {
        String[] rooms = roomsData.split(";");
        StringBuilder sb = new StringBuilder("Доступные комнаты:\n\n");
        for (String room : rooms) {
            sb.append("• ").append(room).append("\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString());
    }

    private void sendCommand(String command) {
        if (socket == null || !socket.isConnected()) return;

        new Thread(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.wrap((command + "\n").getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) {
                    socket.write(buffer);
                }
            } catch (IOException e) {
                addMessage("Ошибка отправки: " + e.getMessage() + "\n");
            }
        }).start();
    }

    private void addMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                doc.insertString(doc.getLength(), text, null);
                chatArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void addColoredMessage(String text, Color color) {
        SwingUtilities.invokeLater(() -> {
            try {
                SimpleAttributeSet attrs = new SimpleAttributeSet();
                StyleConstants.setForeground(attrs, color);
                StyleConstants.setBold(attrs, true);
                doc.insertString(doc.getLength(), text, attrs);
                chatArea.setCaretPosition(doc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
            JTextField hostField = new JTextField("localhost");
            JTextField portField = new JTextField("8080");
            JTextField nameField = new JTextField("Пользователь");

            panel.add(new JLabel("Сервер:"));
            panel.add(hostField);
            panel.add(new JLabel("Порт:"));
            panel.add(portField);
            panel.add(new JLabel("Ваше имя:"));
            panel.add(nameField);

            int result = JOptionPane.showConfirmDialog(null, panel,
                "Подключение", JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                try {
                    String host = hostField.getText();
                    int port = Integer.parseInt(portField.getText());
                    String name = nameField.getText();

                    ClientGUI gui = new ClientGUI();
                    gui.setVisible(true);
                    gui.connect(host, port, name);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, "Ошибка: " + e.getMessage());
                }
            }
        });
    }

    private Color parseColor(String hexColor) {
        try {
            return Color.decode("#" + hexColor);
        } catch (Exception e) {
            return Color.BLUE;
        }
    }
}

