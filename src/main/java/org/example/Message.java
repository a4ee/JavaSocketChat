package org.example;

public class Message {
    private String command;  // USERNAME, CREATE, JOIN, LEAVE, LIST, MSG
    private String data;

    public Message(String command, String data) {
        this.command = command;
        this.data = data;
    }

    public String getCommand() {
        return command;
    }

    public String getData() {
        return data;
    }

    public static Message fromProtocol(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            return new Message("MSG", line);
        }
        String cmd = line.substring(0, colonIndex);
        String data = line.substring(colonIndex + 1);
        return new Message(cmd, data);
    }
}

