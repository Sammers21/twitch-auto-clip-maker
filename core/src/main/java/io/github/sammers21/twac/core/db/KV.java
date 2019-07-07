package io.github.sammers21.twac.core.db;

import io.reactiverse.reactivex.pgclient.PgIterator;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactiverse.reactivex.pgclient.Row;
import io.reactiverse.reactivex.pgclient.Tuple;
import io.vertx.core.buffer.Buffer;

public class KV {
    private final String id;
    private final PgPool pgClient;

    public KV(String id, PgPool pgClient) {
        this.id = id;
        this.pgClient = pgClient;
    }

    public void put(String key, byte[] value) {
        pgClient.rxPreparedQuery(
                "insert into kv(id, key, value) values ($1, $2, $3)",
                Tuple.of(id, key, Buffer.buffer(value))
        ).blockingGet();
    }

    public byte[] get(String key) {
        PgRowSet pgRowSet = pgClient.rxPreparedQuery(
                "select value as v from kv where id = $1 and key = $2",
                Tuple.of(id, key)
        ).blockingGet();
        PgIterator iterator = pgRowSet.iterator();
        if (iterator.hasNext()) {
            Row next = iterator.next();
            io.vertx.reactivex.core.buffer.Buffer buffer = next.getBuffer("v");
            return buffer.getBytes();
        } else {
            return null;
        }
    }
}
