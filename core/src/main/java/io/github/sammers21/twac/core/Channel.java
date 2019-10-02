package io.github.sammers21.twac.core;

import java.util.Objects;

public class Channel {

    private String name;

    public Channel() {
    }

    public Channel(String name) {
        this.name = name;
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
