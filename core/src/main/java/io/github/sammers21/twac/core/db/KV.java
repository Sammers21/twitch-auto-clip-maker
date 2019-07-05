package io.github.sammers21.twac.core.db;

import io.reactiverse.reactivex.pgclient.PgPool;

public class KV {
    private final String id;
    private final PgPool pgClient;

    public KV(String id, PgPool pgClient) {
        this.id = id;
        this.pgClient = pgClient;
    }

    public void put(String key, byte[] value) {

    }

    public byte[] get(String key) {
        return new byte[0];
    }
}
