package io.github.sammers21.tacm.cproducer.decision;

import io.github.sammers21.tacm.cproducer.LastMessagesStorage;
import io.github.sammers21.twac.core.Channel;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.chat.ChatMessage;
import io.github.sammers21.twac.core.chat.TwitchChatClient;
import io.vertx.core.Vertx;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class ShortIntervalDecisionEngine extends DecisionEngine {


    public ShortIntervalDecisionEngine(Vertx vertx, Channel chan, LastMessagesStorage lms, Streams streams, TwitchChatClient chatClient) {
        super(vertx, chan, lms, streams, chatClient);
    }

    @Override
    public boolean makeDecision() {
        long intervalMillis = TimeUnit.SECONDS.toMillis(70);
        List<ChatMessage> channelMessageEvents = lms.lastMessages(Math.toIntExact(intervalMillis))
                .stream()
                .filter(channelMessageEvent -> !channelMessageEvent.getText().startsWith("!"))
                .filter(channelMessageEvent -> !channelMessageEvent.getText().contains("#drop"))
                .filter(channelMessageEvent -> !channelMessageEvent.getText().contains("#дроп"))
                .collect(Collectors.toList());
        // msg per 10 seconds
        Map<Long, List<ChatMessage>> grouped = channelMessageEvents.stream().collect(Collectors.groupingBy(channelMessageEvent -> channelMessageEvent.getReceivedAt().getEpochSecond() / 10));

        long now = System.currentTimeMillis() / 10_000;
        long minDefault = now - (intervalMillis / 10_000);

        //removing first and last elems
        Long max = grouped.keySet().stream().max(Long::compareTo).orElse(now);
        Long min = grouped.keySet().stream().min(Long::compareTo).orElse(minDefault);

        //filling empty time windows
        LongStream.range(min, max + 1).forEach(l -> {
            grouped.computeIfAbsent(l, aLong -> new LinkedList<>());
        });

        // determine who is max and min one more time
        Long max2 = grouped.keySet().stream().max(Long::compareTo).get();
        Long min2 = grouped.keySet().stream().min(Long::compareTo).get();

        grouped.remove(max2);
        grouped.remove(min2);

        Long maxAfterRemove = grouped.keySet().stream().max(Long::compareTo).get();
        Long minAfterRemove = grouped.keySet().stream().min(Long::compareTo).get();

        // sorted list of pairs <timestamp/10 sec --> messages in this 10sec window >
        List<Map.Entry<Long, List<ChatMessage>>> sortedList = grouped.entrySet().stream().sorted(Comparator.comparingLong(Map.Entry::getKey)).collect(Collectors.toList());

        double minRm = (double) grouped.get(minAfterRemove).size() / 10d;
        boolean minRateLimit = minRm > 0.35d;
        double rateChangeRatio = (double) grouped.get(maxAfterRemove).size() / (double) grouped.get(minAfterRemove).size();
        boolean rateIncrease = rateChangeRatio > 6d;

        int increases = 0;
        int decreases = 0;

        Map.Entry<Long, List<ChatMessage>> lastEntry = null;
        for (Map.Entry<Long, List<ChatMessage>> entry : sortedList) {
            if (lastEntry != null) {
                if (entry.getValue().size() > lastEntry.getValue().size()) {
                    increases++;
                } else {
                    decreases++;
                }
            }
            lastEntry = entry;
        }

        boolean increaseQuorum = increases > decreases;
        long timeDif = System.currentTimeMillis() - lastClipOnMillis.get();
        boolean clipMakingLimit = timeDif > TimeUnit.MINUTES.toMillis(1);
        boolean resultedDecision = increaseQuorum && minRateLimit && (rateIncrease ) && clipMakingLimit;
        log.info("Decision[{}] explained: increaseQuorum={}[inc={},dec={}], minRateLimit={}[{}], rateIncrease={}[{}], clipMakingLimit={}[sec={},mins={}], groupSize={}",
                resultedDecision,
                increaseQuorum,
                increases,
                decreases,
                minRateLimit,
                String.format("%.02f", minRm),
                rateIncrease,
                String.format("%.02f", rateChangeRatio),
                clipMakingLimit,
                TimeUnit.MILLISECONDS.toSeconds(timeDif) % 60,
                TimeUnit.MILLISECONDS.toMinutes(timeDif),
                sortedList.size()
        );
        return resultedDecision;
    }
}
