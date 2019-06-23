package io.github.sammers21.twitch;

public class ClipMakingDecisionEngine {

    private final String streamerName;
    private final LastMessagesStorage lms;
    private final Streams streams;

    public ClipMakingDecisionEngine(String streamerName, LastMessagesStorage lms, Streams streams) {
        this.streamerName = streamerName;
        this.lms = lms;
        this.streams = streams;
    }
}
