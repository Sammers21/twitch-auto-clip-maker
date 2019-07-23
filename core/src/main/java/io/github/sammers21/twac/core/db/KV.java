package io.github.sammers21.twac.core.db;

import io.reactiverse.reactivex.pgclient.PgIterator;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactiverse.reactivex.pgclient.Row;
import io.reactiverse.reactivex.pgclient.Tuple;
import io.vertx.core.buffer.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KV {

    private static final Logger log = LoggerFactory.getLogger(KV.class);
    private final String id;
    private final PgPool pgClient;

    public KV(String id, PgPool pgClient) {
        this.id = id;
        this.pgClient = pgClient;
    }

    public void put(String key, byte[] value) {
        String sql = "insert into kv(id, key, value) values ($1, $2, $3)";
        Tuple tuple = Tuple.of(id, key, Buffer.buffer(value));
        logSQL(sql, tuple);
        pgClient.rxPreparedQuery(sql, tuple).blockingGet();
    }

    public byte[] get(String key) {
        String sql = "select value as v from kv where id = $1 and key = $2";
        Tuple tuple = Tuple.of(id, key);
        logSQL(sql, tuple);
        PgRowSet pgRowSet = pgClient.rxPreparedQuery(sql, tuple).blockingGet();
        PgIterator iterator = pgRowSet.iterator();
        if (iterator.hasNext()) {
            Row next = iterator.next();
            io.vertx.reactivex.core.buffer.Buffer buffer = next.getBuffer("v");
            return buffer.getBytes();
        } else {
            return null;
        }
    }

    private void logSQL(String sql, Tuple tuple) {
        log.info("KV SQL:\n{}\nparams:{}", sql, tuple);
    }
}
