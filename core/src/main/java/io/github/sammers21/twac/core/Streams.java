package io.github.sammers21.twac.core;

import com.codahale.metrics.MetricRegistry;
import io.github.sammers21.twac.core.db.DbController;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Streams {
    private static final Logger log = LoggerFactory.getLogger(Streams.class);
    private Map<String, JsonObject> streamersAndInfo = new ConcurrentHashMap<>();
    private Vertx vertx;
    private final DbController dbController;
    private final WebClient webClient;
    private final String clientId;
    private final String bearerToken;
    private final Set<Channel> channelsToWatch;
    private final Integer updateEachMillis;
    private final MetricRegistry metricRegistry;

    public Streams(Vertx vertx,
                   DbController dbController,
                   WebClient webClient,
                   String clientId,
                   String bearerToken,
                   Set<Channel> channelsToWatch,
                   Integer updateEachMillis,
                   MetricRegistry metricRegistry) {

        this.vertx = vertx;
        this.dbController = dbController;
        this.webClient = webClient;
        this.clientId = clientId;
        this.bearerToken = bearerToken;
        this.channelsToWatch = channelsToWatch;
        this.updateEachMillis = updateEachMillis;
        this.metricRegistry = metricRegistry;
    }

    public void enableStreamsInfo() {
        fillStreamersInfo().blockingAwait();
        vertx.setPeriodic(updateEachMillis, event -> fillStreamersInfo().subscribe());
    }

    private Completable fillStreamersInfo() {
        final HttpRequest<Buffer> infoRequest = request();
        return infoRequest.rxSend().map(bufferHttpResponse -> {
            Map<String, JsonObject> streamsInfo = new ConcurrentHashMap<>();
            JsonObject entries = bufferHttpResponse.bodyAsJsonObject();
            entries.getJsonArray("data").stream().map(o -> (JsonObject) o).forEach(json -> {
                streamsInfo.put(json.getString("user_name").toLowerCase(), json);
            });
            return streamsInfo;
        }).doOnSuccess(streamsInfo -> {
            synchronized (this) {
                streamersAndInfo = streamsInfo;
            }
            log.info("Live:{}, total:{}", streamersAndInfo.size(), channelsToWatch.size());
        }).ignoreElement();
    }

    private HttpRequest<Buffer> request() {
        final HttpRequest<Buffer> infoRequest = webClient.getAbs("https://api.twitch.tv/helix/streams")
                .putHeader("Client-ID", clientId)
                .addQueryParam("first", String.valueOf(100))
                .addQueryParam("token", bearerToken);
        channelsToWatch.forEach(chan -> {
            infoRequest.addQueryParam("user_login", chan.getName());
        });
        return infoRequest;
    }

    public synchronized Map<String, JsonObject> streamersAndInfo() {
        return streamersAndInfo;
    }

    public Single<String> createClipOnChannel(String channelName) {
        if (isOffline(channelName)) {
            return Single.error(new IllegalStateException("Streamer is offline:" + channelName));
        }
        JsonObject json;
        synchronized (this) {
            json = streamersAndInfo.get(channelName);
        }
        String userId = json.getString("user_id");
        HttpRequest<Buffer> clipReq = webClient.postAbs("https://api.twitch.tv/helix/clips");
        clipReq.putHeader("Authorization", String.format("Bearer %s", bearerToken));
        clipReq.addQueryParam("broadcaster_id", userId);

        return clipReq.rxSend().map(resp -> {
            JsonObject arg = resp.bodyAsJsonObject();
            log.info("Clip endpoint response:\n{}", arg.encodePrettily());
            return arg.getJsonArray("data").getJsonObject(0).getString("id");
        }).doOnSuccess(ok -> dbController.insertClip(ok, channelName, userId, json.getString("title"))
                .subscribe(() -> {
                    metricRegistry.meter(String.format("channel.%s.createClip", channelName)).mark();
                    log.info("Clip is in the database");
                }, error -> {
                    log.error("Unable to insert a clip", error);
                }));
    }

    public synchronized int viewersOnStream(String channelName) {
        JsonObject stremerInfo = streamersAndInfo.get(channelName);
        if (stremerInfo == null) {
            return 0;
        } else {
            return stremerInfo.getInteger("viewer_count");
        }
    }

    public synchronized boolean isOnline(String channelName) {
        return streamersAndInfo.get(channelName) != null;
    }

    public synchronized boolean isOffline(String channelName) {
        return !isOnline(channelName);
    }

}
