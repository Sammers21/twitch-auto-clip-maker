package io.github.sammers21.twac.core;

import java.util.Objects;

public class Channel {

    private String name;

    private String engine;

    public Channel() {
    }

    public Channel(String name, String engine) {
        this.name = name;
        this.engine = engine;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return Objects.equals(getName(), channel.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName());
    }
}
