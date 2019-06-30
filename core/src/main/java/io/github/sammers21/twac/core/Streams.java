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

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Streams {
    private static final Logger log = LoggerFactory.getLogger(Streams.class);
    private Map<String, JsonObject> streamersAndInfo = new ConcurrentHashMap<>();
    private Vertx vertx;
    private final DbController dbController;
    private final WebClient webClient;
    private final String clientId;
    private final String bearerToken;
    private final Set<String> channelsToWatch;
    private final Integer updateEachMillis;
    private final MetricRegistry metricRegistry;

    public Streams(Vertx vertx, DbController dbController, WebClient webClient, String clientId, String bearerToken, Set<String> channelsToWatch, Integer updateEachMillis, MetricRegistry metricRegistry) {
        this.vertx = vertx;
        this.dbController = dbController;
        this.webClient = webClient;
        this.clientId = clientId;
        this.bearerToken = bearerToken;
        this.channelsToWatch = channelsToWatch;
        this.updateEachMillis = updateEachMillis;
        this.metricRegistry = metricRegistry;
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
        if (isOffline(channelName)) {
            return Single.error(new IllegalStateException("Streamer is offline:" + channelName));
        }
        JsonObject entries;
        synchronized (this) {
            entries = streamersAndInfo.get(channelName);
        }
        String userId = entries.getString("user_id");
        HttpRequest<Buffer> clipReq = webClient.postAbs("https://api.twitch.tv/helix/clips");
        clipReq.putHeader("Authorization", String.format("Bearer %s", bearerToken));
        clipReq.addQueryParam("broadcaster_id", userId);

        return clipReq.rxSend().map(resp -> {
            JsonObject arg = resp.bodyAsJsonObject();
            log.info("Clip endpoint response:\n{}", arg.encodePrettily());
            return arg.getJsonArray("data").getJsonObject(0).getString("id");
        }).doOnSuccess(ok -> dbController.insertClip(ok, channelName, userId)
                .subscribe(() -> {
                    metricRegistry.meter(String.format("channel.%s.createClip", channelName)).mark();
                    log.info("Clip is in the database");
                }, error -> {
                    log.error("Unable to insert a clip", error);
                }));
    }

    public static final Pattern CLIP_PATTERN = Pattern.compile("https://([\\w-]+)\\.twitch\\.tv/(\\d+)-offset-(\\d+).*");

    public Completable downloadClip(String clipId, String pathToSave) {
        return webClient.getAbs("https://api.twitch.tv/helix/clips")
                .putHeader("Client-ID", clientId)
                .addQueryParam("id", clipId)
                .rxSend()
                .flatMap(resp -> {
                    String url = resp.bodyAsJsonObject().getJsonArray("data").getJsonObject(0).getString("thumbnail_url");
                    Matcher matcher = CLIP_PATTERN.matcher(url);
                    matcher.find();
                    String mediaSubDomain = matcher.group(1);
                    String preOffset = matcher.group(2);
                    String offset = matcher.group(3);
                    String downloadUrl = String.format("https://%s.twitch.tv/%s-offset-%s.mp4", mediaSubDomain, preOffset, offset);
                    log.info("Downloading: '{}'", downloadUrl);
                    return webClient.getAbs(downloadUrl).rxSend();
                })
                .flatMapCompletable(resp -> vertx.fileSystem().rxWriteFile(pathToSave, resp.body()))
                .doOnComplete(() -> log.info("Clip has been downloaded:'{}'", new File(pathToSave).getAbsolutePath()));
    }

    public synchronized boolean isOnline(String channelName) {
        return streamersAndInfo.get(channelName) != null;
    }

    public synchronized boolean isOffline(String channelName) {
        return !isOnline(channelName);
    }

}
