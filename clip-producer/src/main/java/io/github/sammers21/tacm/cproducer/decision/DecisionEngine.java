package io.github.sammers21.tacm.cproducer.decision;

import io.github.sammers21.tacm.cproducer.LastMessagesStorage;
import io.github.sammers21.twac.core.Channel;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.chat.TwitchChatClient;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public abstract class DecisionEngine {

    protected final Vertx vertx;
    protected final Logger log;
    protected final Channel channel;
    protected final LastMessagesStorage lms;
    protected final Streams streams;
    private TwitchChatClient chatClient;
    protected final AtomicLong lastClipOnMillis = new AtomicLong(0);

    public DecisionEngine(Vertx vertx, Channel channel, LastMessagesStorage lms, Streams streams, TwitchChatClient chatClient) {
        this.vertx = vertx;
        log = LoggerFactory.getLogger(String.format("%s:[%s]", ShortIntervalDecisionEngine.class.getName(), channel.getName()));
        this.channel = channel;
        this.lms = lms;
        this.streams = streams;
        this.chatClient = chatClient;
    }

    abstract public boolean makeDecision();

    public void startDecisionEngine() {
        log.info("Will make decisions in 2 mins");
        vertx.setTimer(TimeUnit.MINUTES.toMillis(2), timer -> {
            log.info("Start making decisions");
            vertx.setPeriodic(3_000, event -> {
                try {
                    if (streams.isOnline(channel.getName()) && makeDecision()) {
                        streams.createClipOnChannel(channel.getName())
                            .doAfterSuccess(s -> {
                                chatClient.sendMsgToChan(channel.getName(),
                                    String.format("Highlight has been created: https://clips.twitch.tv/%s .Bot creator: sammers21", s));
                            })
                            .subscribe(
                                ok -> {
                                    log.info("Clip created");
                                    lastClipOnMillis.set(System.currentTimeMillis());

                                },
                                throwable -> log.error("Unable to create a clip:", throwable)
                            );
                    }
                } catch (NoSuchElementException e) {
                    log.warn("NLP");
                } catch (Throwable t) {
                    log.error("Unexpected exception", t);
                }
            });
        });
    }
}
