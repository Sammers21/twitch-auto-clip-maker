package io.github.sammers21.twitch;

import java.time.Instant;
import java.util.LinkedList;

public class TimeStorage<Type extends TypeWithTime> {

    private final int storeMessagesForTheLastMillis;
    private final LinkedList<Type> messages = new LinkedList<>();

    public TimeStorage(int storeMessagesForTheLastMillis) {
        this.storeMessagesForTheLastMillis = storeMessagesForTheLastMillis;
    }

    public synchronized void push(Type event) {
        removeExpired();
        messages.push(event);
    }

    private synchronized void removeExpired() {
        while (messages.size() != 0 &&
                messages.peek().time().toEpochMilli() < Instant.now().toEpochMilli() - storeMessagesForTheLastMillis) {
            messages.pop();
        }
    }

}
