package io.github.sammers21.twitch;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class LastMessagesStorage {

    private final int storeMessagesForTheLastMillis;
    private final LinkedList<ChannelMessageEvent> messages = new LinkedList<>();

    public LastMessagesStorage(int storeMessagesForTheLastMillis) {
        this.storeMessagesForTheLastMillis = storeMessagesForTheLastMillis;
    }

    public synchronized void push(ChannelMessageEvent messageEvent) {
        removeExpired();
        messages.push(messageEvent);
    }

    public synchronized List<String> lastMessages() {
        removeExpired();
        return messages.stream().map(ChannelMessageEvent::getMessage).collect(Collectors.toList());
    }

    private synchronized void removeExpired() {
        while (messages.size() != 0 &&
                messages.peek().getFiredAt().toInstant().toEpochMilli() < Instant.now().toEpochMilli() - storeMessagesForTheLastMillis) {
            messages.pop();
        }
    }

    public synchronized Double lenIndex() {
        List<String> strings = lastMessages();
        int messageCount = strings.size();
        if (messageCount != 0) {
            Integer symbCount = strings.stream().map(String::length).reduce(Integer::sum).get();
            return Double.valueOf(symbCount) / (double) messageCount;
        } else {
            return 1d;
        }
    }
}
