package org.example;

import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private final String name;
    private final String owner;
    private final Set<SocketChannel> members;
    private final int maxMembers;

    public ChatRoom(String name, String owner, int maxMembers) {
        this.name = name;
        this.owner = owner;
        this.members = ConcurrentHashMap.newKeySet();
        this.maxMembers = maxMembers;
    }

    public boolean addMember(SocketChannel channel) {
        if (members.size() >= maxMembers) {
            return false;
        }
        return members.add(channel);
    }

    public boolean removeMember(SocketChannel channel) {
        return members.remove(channel);
    }

    public Set<SocketChannel> getMembers() {
        return new HashSet<>(members);
    }

    public int getMemberCount() {
        return members.size();
    }

}

