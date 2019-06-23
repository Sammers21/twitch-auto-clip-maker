package io.github.sammers21.twitch.db;

import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactiverse.reactivex.pgclient.Row;
import io.reactiverse.reactivex.pgclient.Tuple;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbController {

    private static final Logger log = LoggerFactory.getLogger(DbController.class);
    private final PgPool pgClient;

    public DbController(PgPool pgClient) {
        this.pgClient = pgClient;
    }

    public Completable insertToken(String token) {
        return pgClient.rxPreparedQuery(
                "INSERT into client_token(token) values (?)",
                Tuple.of(token)
        ).ignoreElement();
    }

    public Single<String> token() {
        return pgClient
                .rxQuery("SELECT token\n" +
                        "from client_token\n" +
                        "order by client_token.time DESC\n" +
                        "limit 1")
                .map(pgRowSet -> {
                    PgRowSet rs = pgRowSet.value();
                    Row row = rs.iterator().next();
                    return row.getString("token");
                });
    }

    public Completable insertClip(String clipId, String streamerName, String broadcasterId) {
        String fullLink = String.format("https://clips.twitch.tv/%s", clipId);
        log.info("Insert new clip with clip_id={}, streamer_name={}, broadcaster_id={}", clipId, streamerName, broadcasterId);
        return pgClient.rxPreparedQuery(
                "insert into clip(clip_id, streamer_name, broadcaster_id, full_link) values ($1, $2, $3, $4)",
                Tuple.of(clipId, streamerName, broadcasterId,fullLink)
        ).ignoreElement();
    }
}

