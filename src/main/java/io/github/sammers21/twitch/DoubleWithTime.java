package io.github.sammers21.twitch;

import java.time.Instant;

public class DoubleWithTime implements TypeWithTime {
    private final Instant time;
    private final Double aDouble;

    public DoubleWithTime(Instant time, Double aDouble) {
        this.time = time;
        this.aDouble = aDouble;
    }

    @Override
    public Instant time() {
        return time;
    }

    public Double number() {
        return aDouble;
    }
}
