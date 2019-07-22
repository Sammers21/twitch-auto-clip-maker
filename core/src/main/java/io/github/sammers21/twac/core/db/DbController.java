package io.github.sammers21.twac.core.db;

import io.reactiverse.pgclient.PgPoolOptions;
import io.reactiverse.reactivex.pgclient.PgClient;
import io.reactiverse.reactivex.pgclient.PgConnection;
import io.reactiverse.reactivex.pgclient.PgIterator;
import io.reactiverse.reactivex.pgclient.PgPool;
import io.reactiverse.reactivex.pgclient.PgRowSet;
import io.reactiverse.reactivex.pgclient.Row;
import io.reactiverse.reactivex.pgclient.Tuple;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import org.javatuples.Quintet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DbController {

    private static final Logger log = LoggerFactory.getLogger(DbController.class);
    private final PgPool pgClient;
    private final String version;

    public DbController(JsonObject dbCfg, String version) {
        this.version = version;
        PgPoolOptions pgOptions = new PgPoolOptions()
                .setPort(dbCfg.getInteger("port"))
                .setHost(dbCfg.getString("host"))
                .setDatabase(dbCfg.getString("db"))
                .setUser(dbCfg.getString("user"))
                .setPassword(dbCfg.getString("password"))
                .setMaxSize(5);
        this.pgClient = PgClient.pool(pgOptions);
        PgConnection pgConnection = pgClient.rxGetConnection().blockingGet();
        log.info("DB connection: OK");
        pgConnection.close();
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

    public Completable insertClip(String clipId, String streamerName, String broadcasterId, String title) {
        String fullLink = String.format("https://clips.twitch.tv/%s", clipId);
        log.info("Insert new clip with clip_id={}, streamer_name={}, broadcaster_id={}, title={}", clipId, streamerName, broadcasterId, title);
        return pgClient.rxPreparedQuery(
                "insert into clip(clip_id, streamer_name, broadcaster_id, full_link, app_version, title) values ($1, $2, $3, $4, $5, $6)",
                Tuple.of(clipId, streamerName, broadcasterId, fullLink, version, title)
        ).ignoreElement();
    }

    public Single<List<String>> selectNonIncludedClips(String streamerName) {
        return pgClient.rxPreparedQuery(
                "SELECT clip.clip_id\n" +
                        "from clip\n" +
                        "         left join clip_released cr on clip.clip_id = cr.clip_id\n" +
                        "where streamer_name = $1\n" +
                        "  and included_in_release is null\n" +
                        "ORDER BY time DESC",
                Tuple.of(streamerName)
        ).map(pgRowSet -> {
            List<String> res = new ArrayList<>();
            PgIterator iterator = pgRowSet.iterator();
            while (iterator.hasNext()) {
                res.add(iterator.next().getString("clip_id"));
            }
            return res;
        }).doAfterSuccess(strings -> log.info("selectNonIncludedClips for chan='{}' size={}", streamerName, strings.size()));
    }


    // title, count(*), max(time), array_agg(clip.clip_id), min(streamer_name)
    public Single<List<Quintet<String, Integer, LocalDateTime, String[], String>>> titleGroupedNonIncluded(Collection<String> streamers) {
        String streamerCondition = streamers.stream()
                .map(streamer -> String.format("streamer_name = '%s'", streamer))
                .collect(Collectors.joining(" or ", "(", ")"));

        String sql = String.format(
                "select title as t, count(*) as c, max(time) as mt, array_agg(clip.clip_id) as agg, min(streamer_name) as msn\n" +
                        "from clip\n" +
                        "         left join clip_released cr on clip.clip_id = cr.clip_id\n" +
                        "where streamer_name = 'csruhub'\n" +
                        "  and included_in_release is null\n" +
                        "  and title is not null\n" +
                        "group by title\n" +
                        "order by max(time) desc;"
                , streamerCondition
        );

        log.info("SQL:\n{}", sql);
        return pgClient.rxPreparedQuery(sql).map(rows -> {
            List<Quintet<String, Integer, LocalDateTime, String[], String>> res = new ArrayList<>(rows.size());
            PgIterator iterator = rows.iterator();
            while (iterator.hasNext()) {
                Row next = iterator.next();
                res.add(
                        new Quintet<>(
                                next.getString("t"),
                                next.getInteger("c"),
                                next.getLocalDateTime("mt"),
                                next.getStringArray("agg"),
                                next.getString("msn")
                        )
                );
            }
            return res;
        });
    }

    public Completable bundleOfClips(List<String> clipIds, String youtubeVideoId, String youtubeChan) {
        String youtubeLink = String.format("https://www.youtube.com/watch?v=%s", youtubeVideoId);

        List<Tuple> batch = clipIds.stream().map(clipId -> Tuple.of(clipId, youtubeVideoId)).collect(Collectors.toList());
        return pgClient.rxPreparedQuery(
                "INSERT into release(youtube_video_id, youtube_link, producer_version, youtube_chan) values ($1, $2, $3, $4)",
                Tuple.of(youtubeVideoId, youtubeLink, version, youtubeChan)
        )
                .flatMap(pgRowSet -> pgClient.rxPreparedBatch("INSERT INTO clip_released(clip_id, included_in_release) values ($1, $2)", batch))
                .doAfterSuccess(pgRowSet ->
                        log.info("created bundleOfClips youtubeId='{}'. Clips={}", youtubeVideoId, clipIds.stream().collect(Collectors.joining(", ", "[", "]")))
                )
                .ignoreElement();
    }

    public Single<Map<LocalDate, Integer>> releasesOnChan(String youtubeChan) {
        return pgClient.rxPreparedQuery("select time::date as t, count(*) as c\n" +
                "from release\n" +
                "where youtube_chan = 'dota2owl'\n" +
                "group by time::date")
                .map(pgRowSet -> {
                    Map<LocalDate, Integer> res = new HashMap<>();
                    PgIterator iterator = pgRowSet.iterator();
                    while (iterator.hasNext()) {
                        Row next = iterator.next();
                        res.put(next.getLocalDate("t"), next.getInteger("c"));
                    }
                    return res;
                });

    }

    public Maybe<LocalDateTime> lastReleaseTimeOnChan(String youtubeChanName) {
        return pgClient.rxPreparedQuery(
                "select time from release where youtube_chan = $1 order by time desc limit 1",
                Tuple.of(youtubeChanName)
        ).flatMapMaybe(pgRowSet -> {
            PgIterator iterator = pgRowSet.iterator();
            if (iterator.hasNext()) {
                return Maybe.just(iterator.next().getLocalDateTime("time"));
            } else {
                return Maybe.empty();
            }
        });
    }

    public KV kv(String id) {
        return new KV(id, pgClient);
    }
}

