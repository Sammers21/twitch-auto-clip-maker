package io.github.sammers21.twitch.db;

import io.github.sammers21.twitch.Main;
import io.reactiverse.reactivex.pgclient.PgIterator;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactiverse.reactivex.pgclient.Row;
import io.reactiverse.reactivex.pgclient.Tuple;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class DbController {

    private static final Logger log = LoggerFactory.getLogger(DbController.class);
    private final PgPool pgClient;

    public DbController(PgPool pgClient) {
        this.pgClient = pgClient;
    }

    public Completable insertToken(String token) {
        return pgClient.rxPreparedQuery(
                "INSERT into client_token(token) values ($1)",
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
                "insert into clip(clip_id, streamer_name, broadcaster_id, full_link, app_version) values ($1, $2, $3, $4, $5)",
                Tuple.of(clipId, streamerName, broadcasterId, fullLink, Main.VERSION)
        ).ignoreElement();
    }

    public Single<List<String>> selectClips(String streamerName) {
        return pgClient.rxPreparedQuery(
                "select clip_id from clip where streamer_name = $1",
                Tuple.of(streamerName)
        ).map(pgRowSet -> {
            List<String> res = new ArrayList<>();
            PgIterator iterator = pgRowSet.iterator();
            while (iterator.hasNext()) {
                res.add(iterator.next().getString("clip_id"));
            }
            return res;
        });
    }
}

