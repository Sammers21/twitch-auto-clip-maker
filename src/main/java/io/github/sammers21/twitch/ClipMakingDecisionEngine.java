package io.github.sammers21.twitch;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class ClipMakingDecisionEngine {

    private final Logger log;
    private final String streamerName;
    private final LastMessagesStorage lms;
    private final Streams streams;
    private final AtomicLong lastClipOnMillis = new AtomicLong(0);

    public ClipMakingDecisionEngine(Vertx vertx, String streamerName, LastMessagesStorage lms, Streams streams) {
        log = LoggerFactory.getLogger(String.format("%s:[%s]", ClipMakingDecisionEngine.class.getName(), streamerName));
        this.streamerName = streamerName;
        this.lms = lms;
        this.streams = streams;
        log.info("Will make decisions in 2 mins");
        vertx.setTimer(TimeUnit.MINUTES.toMillis(2), timer -> {
            log.info("Start making decisions");
            vertx.setPeriodic(10_000, event -> {
                try {
                    if (streams.isOnline(streamerName) && makeDecision()) {
                        streams.createClipOnChannel(streamerName)
                                .subscribe(
                                        ok -> {
                                            log.info("Clip created");
                                            lastClipOnMillis.set(System.currentTimeMillis());
                                        },
                                        throwable -> log.error("Unable to create a clip:", throwable)
                                );
                    }
                } catch (NoSuchElementException e) {
                    log.error("NLP");
                } catch (Throwable t) {
                    log.error("Unexpected exception", t);
                }
            });
        });
    }

    private boolean makeDecision() {
        int intervalMinutes = 2;
        long intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes);
        List<ChannelMessageEvent> channelMessageEvents = lms.lastMessages(Math.toIntExact(intervalMillis));
        // msg per 10 seconds
        Map<Long, List<ChannelMessageEvent>> grouped = channelMessageEvents.stream().collect(Collectors.groupingBy(channelMessageEvent -> channelMessageEvent.getFiredAt().toInstant().getEpochSecond() / 10));

        long now = System.currentTimeMillis() / 10_000;
        long minDefault = now - (intervalMillis / 10_000);

        //removing first and last elems
        Long max = grouped.keySet().stream().max(Long::compareTo).orElse(now);
        Long min = grouped.keySet().stream().min(Long::compareTo).orElse(minDefault);

        //filling empty time windows
        LongStream.range(min, max + 1).forEach(l -> {
            grouped.computeIfAbsent(l, aLong -> new LinkedList<>());
        });

        grouped.remove(max);
        grouped.remove(min);

        Long maxAfterRemove = grouped.keySet().stream().max(Long::compareTo).get();
        Long minAfterRemove = grouped.keySet().stream().min(Long::compareTo).get();

        List<Map.Entry<Long, List<ChannelMessageEvent>>> sortedList = grouped.entrySet().stream().sorted(Comparator.comparingLong(Map.Entry::getKey)).collect(Collectors.toList());

        double minRm = (double) grouped.get(minAfterRemove).size() / 10d;
        boolean minRateLimit = minRm > 0.35d;
        double rateChangeRation = (double) grouped.get(maxAfterRemove).size() / (double) grouped.get(minAfterRemove).size();
        boolean rateIncrease = rateChangeRation > 3d;

        int increases = 0;
        int decreases = 0;

        Map.Entry<Long, List<ChannelMessageEvent>> lastEntry = null;
        for (Map.Entry<Long, List<ChannelMessageEvent>> entry : sortedList) {
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
        boolean clipMakingLimit = (System.currentTimeMillis() - lastClipOnMillis.get()) > TimeUnit.MINUTES.toMillis(15);
        boolean resultedDecision = increaseQuorum && minRateLimit && rateIncrease && clipMakingLimit;
        log.info("Decision[{}] explained: increaseQuorum={}[inc={},dec={}], minRateLimit={}[{}], rateIncrease={}[{}], clipMakingLimit={}, groupSize={}",
                resultedDecision,
                increaseQuorum,
                increases,
                decreases,
                minRateLimit,
                String.format("%.02f", minRm),
                rateIncrease,
                rateChangeRation,
                clipMakingLimit,
                sortedList.size()
        );
        return resultedDecision;
    }
}
