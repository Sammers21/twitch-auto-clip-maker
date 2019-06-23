package io.github.sammers21.twitch;

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
    private final WebClient webClient;
    private final String clientId;
    private final String bearerToken;
    private final Set<String> channelsToWatch;

    public Streams(Vertx vertx, WebClient webClient, String clientId, String bearerToken, Set<String> channelsToWatch, Integer updateEachMillis) {
        this.webClient = webClient;
        this.clientId = clientId;
        this.bearerToken = bearerToken;
        this.channelsToWatch = channelsToWatch;
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
            log.info("Streamers info:{}", entries.encodePrettily());
            return streamsInfo;
        }).doOnSuccess(streamsInfo -> {
            synchronized (this) {
                streamersAndInfo = streamsInfo;
            }
            log.info("Fetched:{}, total:{}", streamersAndInfo.size(), channelsToWatch.size());
        }).ignoreElement();
    }

    private HttpRequest<Buffer> request() {
        final HttpRequest<Buffer> infoRequest = webClient.getAbs("https://api.twitch.tv/helix/streams")
                .putHeader("Client-ID", clientId)
                .addQueryParam("first", String.valueOf(100))
                .addQueryParam("token", bearerToken);
        channelsToWatch.forEach(chan -> {
            infoRequest.addQueryParam("user_login", chan);
        });
        return infoRequest;
    }

    public synchronized Map<String, JsonObject> streamersAndInfo() {
        return streamersAndInfo;
    }

    public Single<String> createClipOnChannel(String channelName) {
        JsonObject entries = streamersAndInfo.get(channelName);
        if (entries == null) {
            return Single.error(new IllegalStateException("Streamer are not alive:" + channelName));
        }
        String userId = entries.getString("user_id");
        HttpRequest<Buffer> clipReq = webClient.postAbs("https://api.twitch.tv/helix/clips");
        clipReq.putHeader("Authorization", String.format("Bearer %s", bearerToken));
        clipReq.addQueryParam("broadcaster_id", userId);

        return clipReq.rxSend().map(resp -> {
            JsonObject arg = resp.bodyAsJsonObject();
            log.info("Clip endpoint response:\n{}", arg.encodePrettily());
            return arg.getJsonArray("data").getJsonObject(0).getString("id");
        });
    }

}
