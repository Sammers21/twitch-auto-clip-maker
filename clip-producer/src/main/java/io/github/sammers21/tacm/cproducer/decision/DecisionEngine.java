package io.github.sammers21.tacm.cproducer.decision;

import io.github.sammers21.tacm.cproducer.LastMessagesStorage;
import io.github.sammers21.twac.core.Streams;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public abstract class DecisionEngine {

    protected final Vertx vertx;
    protected final Logger log;
    protected final String streamerName;
    protected final LastMessagesStorage lms;
    protected final Streams streams;
    protected final AtomicLong lastClipOnMillis = new AtomicLong(0);

    public DecisionEngine(Vertx vertx, String streamerName, LastMessagesStorage lms, Streams streams) {
        this.vertx = vertx;
        log = LoggerFactory.getLogger(String.format("%s:[%s]", ShortIntervalDecisionEngine.class.getName(), streamerName));
        this.streamerName = streamerName;
        this.lms = lms;
        this.streams = streams;
    }

    abstract public boolean makeDecision();

    abstract public void startDecisionEngine();
}
