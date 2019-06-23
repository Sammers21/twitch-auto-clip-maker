package io.github.sammers21.twitch;

import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
                    if (makeDecision()) {
                        streams.createClipOnChannel(streamerName)
                                .subscribe(
                                        ok -> {
                                            log.info("Clip created");
                                            lastClipOnMillis.set(System.currentTimeMillis());
                                        },
                                        throwable -> log.error("Unable to create a clip:", throwable)
                                );
                    }
                } catch (Throwable t) {
                    log.error("Unexpected exception", t);
                }
            });
        });
    }

    public boolean makeDecision() {
        List<ChannelMessageEvent> channelMessageEvents = lms.lastMessages(Math.toIntExact(TimeUnit.MINUTES.toMillis(2)));
        // msg per 10 seconds
        Map<Long, List<ChannelMessageEvent>> grouped = channelMessageEvents.stream().collect(Collectors.groupingBy(channelMessageEvent -> channelMessageEvent.getFiredAt().toInstant().getEpochSecond() / 10));

        //removing first and last elems
        Long max = grouped.keySet().stream().max(Long::compareTo).get();
        Long min = grouped.keySet().stream().min(Long::compareTo).get();
        grouped.remove(max);
        grouped.remove(min);

        Long maxAfterRemove = grouped.keySet().stream().max(Long::compareTo).get();
        Long minAfterRemove = grouped.keySet().stream().min(Long::compareTo).get();

        boolean minRateLimit = ((double) grouped.get(minAfterRemove).size() / 10d) > 0.5d;
        boolean rateIncrease = ((double) grouped.get(maxAfterRemove).size() / (double) grouped.get(minAfterRemove).size()) > 3d;

        int increases = 0;
        int decreases = 0;

        Map.Entry<Long, List<ChannelMessageEvent>> lastEntry = null;
        for (Map.Entry<Long, List<ChannelMessageEvent>> entry : grouped.entrySet()) {
            if (lastEntry != null) {
                if (entry.getValue().size() >= lastEntry.getValue().size()) {
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
        log.info("Decision[{}] explained: increaseQuorum={}[inc={},dec={}], minRateLimit={}, rateIncrease={}, clipMakingLimit={}", resultedDecision, increaseQuorum, increases, decreases, minRateLimit, rateIncrease, clipMakingLimit);
        return resultedDecision;
    }
}
