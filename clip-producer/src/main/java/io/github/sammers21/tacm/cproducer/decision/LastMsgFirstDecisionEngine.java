package io.github.sammers21.tacm.cproducer.decision;

import io.github.sammers21.tacm.cproducer.LastMessagesStorage;
import io.github.sammers21.twac.core.Channel;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.chat.TwitchChatClient;
import io.vertx.core.Vertx;

public class LastMsgFirstDecisionEngine extends DecisionEngine {
    public LastMsgFirstDecisionEngine(Vertx vertx, Channel channel, LastMessagesStorage lms, Streams streams, TwitchChatClient chatClient) {
        super(vertx, channel, lms, streams, chatClient);
    }

    @Override
    public boolean makeDecision() {
        return false;
    }
}
