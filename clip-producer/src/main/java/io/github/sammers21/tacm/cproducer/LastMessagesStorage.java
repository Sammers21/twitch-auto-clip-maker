package io.github.sammers21.tacm.cproducer;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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

    public synchronized List<ChannelMessageEvent> lastMessages(Integer periodOfTime) {
        removeExpired();
        final long now = Instant.now().toEpochMilli();
        return messages.stream()
                .filter(channelMessageEvent ->
                        (now - channelMessageEvent.getFiredAt().toInstant().toEpochMilli()) < periodOfTime
                )
                .collect(Collectors.toList());
    }

    private synchronized void removeExpired() {
        long now = Instant.now().toEpochMilli();
        while (messages.size() != 0 &&
                messages.peekLast().getFiredAt().toInstant().toEpochMilli() < now - storeMessagesForTheLastMillis) {
            messages.removeLast();
        }
    }

    public synchronized Double lenIndex(Integer periodOfTime) {
        return customIndex(periodOfTime, String::length);
    }

    private synchronized Double customIndex(Integer periodOfTime, Function<String, Integer> countProcedure) {
        List<ChannelMessageEvent> strings = lastMessages(periodOfTime);
        int messageCount = strings.size();
        if (messageCount != 0) {
            Integer reducedMessages = strings.stream()
                    .map(ChannelMessageEvent::getMessage)
                    .map(countProcedure).reduce(Integer::sum).get();
            return Double.valueOf(reducedMessages) / (double) messageCount;
        } else {
            return 0d;
        }
    }

    public synchronized Double uniqWordsIndex(Integer periodOfTime) {
        return customIndex(periodOfTime, LastMessagesStorage::uniqWords);
    }

    public synchronized Double spamUniqIndex(Integer periodOfTime) {
        List<ChannelMessageEvent> strings = lastMessages(periodOfTime);
        int messageCount = strings.size();
        if (messageCount != 0) {
            final Set<String> collected = strings.stream()
                    .map(ChannelMessageEvent::getMessage)
                    .map(msg -> Arrays.stream(msg.split("\\s+")))
                    .flatMap(stringStream -> stringStream)
                    .collect(Collectors.toSet());
            return (double) collected.size() / (double) messageCount;
        } else {
            return 0d;
        }
    }

    private static int uniqWords(String input) {
        if (input == null || input.isEmpty()) {
            return 0;
        }

        String[] words = input.split("\\s+");
        Set<String> wordSet = Arrays.stream(words).collect(Collectors.toSet());
        return wordSet.size();
    }
}