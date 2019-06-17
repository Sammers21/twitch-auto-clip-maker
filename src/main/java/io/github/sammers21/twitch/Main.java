package io.github.sammers21.twitch;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);
    private static String CARBON_HOST;
    private static String CLIENT_ID;
    private static String CLIENT_SECRET;
    private static AtomicReference<String> BEARER_TOKEN = new AtomicReference<>(null);
    private static Integer CARBON_PORT;
    private static final Map<String, AtomicInteger> viewersByChan = new ConcurrentHashMap<>();
    private static final Map<String, LastMessagesStorage> storageByChan = new ConcurrentHashMap<>();
    private static final Map<String, JsonObject> streamersAndInfo = new ConcurrentHashMap<>();
    private static Vertx vertx;
    private static WebClient webClient;
    private static TwitchClient twitchClient;

    public static void main(String[] args) throws SocketException, UnknownHostException {
        String token = args[0];
        CARBON_HOST = args[1];
        CARBON_PORT = Integer.parseInt(args[2]);
        CLIENT_ID = args[3];
        CLIENT_SECRET = args[4];

        if (args.length < 6) {
            log.error("Should be at least 6 args");
            System.exit(-1);
        }

        Set<String> channelToWatch = Arrays.stream(args).skip(5).collect(Collectors.toSet());
        vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(channelToWatch.size()));
        webClient = WebClient.create(io.vertx.reactivex.core.Vertx.newInstance(vertx));
        var credential = new OAuth2Credential("twitch", token);
        twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withEnableTMI(true)
                .withEnableHelix(true)
                .withChatAccount(credential)
                .build();

        requestBearerToken();
        fillStreamersInfo(channelToWatch);
        initStoragesAndViewersCounter(channelToWatch);

        log.info("Token={}", token);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("CLIENT_ID={}", CLIENT_ID);
        log.info("CLIENT_SECRET={}", CLIENT_SECRET);
        log.info("Channels={}", channelToWatch.stream().collect(Collectors.joining(",", "[", "]")));


        MetricRegistry metricRegistry = new MetricRegistry();
        initReporters(metricRegistry);

        var chat = twitchClient.getChat();
        channelToWatch.forEach(chat::joinChannel);
        reportMetrics(channelToWatch, vertx, metricRegistry, twitchClient);
    }

    private static void initStoragesAndViewersCounter(Set<String> channelToWatch) {
        channelToWatch.forEach(chan -> {
            viewersByChan.put(chan, new AtomicInteger(0));
            storageByChan.put(chan, new LastMessagesStorage(60_000));
        });
    }

    private static void fillStreamersInfo(Set<String> channelToWatch) {
        final HttpRequest<Buffer> infoRequest = webClient.getAbs("https://api.twitch.tv/helix/streams")
                .putHeader("Client-ID", CLIENT_ID)
                .addQueryParam("first", String.valueOf(100))
                .addQueryParam("token", BEARER_TOKEN.get());
        channelToWatch.forEach(chan -> {
            infoRequest.addQueryParam("user_login", chan);
        });
        final JsonObject entries = infoRequest.rxSend().blockingGet().bodyAsJsonObject();
        entries.getJsonArray("data").stream().map(o -> (JsonObject) o).forEach(json -> {
            streamersAndInfo.put(json.getString("user_name").toLowerCase(), json);
        });

        log.info("Streamers info:{}", entries.encodePrettily());
        log.info("Fetched:{}, total:{}", streamersAndInfo.size(), channelToWatch.size());
    }

    private static void requestBearerToken() {
        final JsonObject entries = webClient.postAbs("https://id.twitch.tv/oauth2/token")
                .addQueryParam("client_id", CLIENT_ID)
                .addQueryParam("client_secret", CLIENT_SECRET)
                .addQueryParam("grant_type", "client_credentials")
                .addQueryParam("scope", "clips:edit")
                .rxSend().blockingGet().bodyAsJsonObject();
        final Integer expiresIn = entries.getInteger("expires_in");
        BEARER_TOKEN.set(entries.getString("access_token"));
        log.info("Bearer token is:'{}'. Will expire in: {}s", BEARER_TOKEN.get(), expiresIn);
        vertx.setTimer((expiresIn - 60) * 1_000, event -> {
            log.info("Refreshing bearer token....");
            requestBearerToken();
        });
    }

    private static void reportMetrics(Set<String> channelToWatch, Vertx vertx, MetricRegistry metricRegistry, TwitchClient twitchClient) {
        var value = twitchClient.getChat().getEventManager().onEvent(ChannelMessageEvent.class);
        value.subscribe((ChannelMessageEvent ok) -> {
            String channelName = ok.getChannel().getName();
            Meter messagesPerSec = metricRegistry.meter(String.format("channel.%s.messages", channelName));
            messagesPerSec.mark();
            LastMessagesStorage lastMessagesStorage = storageByChan.get(channelName);
            lastMessagesStorage.push(ok);
        });

        vertx.setPeriodic(10_000, periodic -> {
            channelToWatch.forEach(chan -> {
                vertx.executeBlocking((Future<Integer> event) -> {
                    Integer viewerCount = twitchClient.getMessagingInterface().getChatters(chan).execute().getViewerCount();
                    event.complete(viewerCount);
                }, event -> {
                    Integer viewerCount = event.result();
                    viewersByChan.get(chan).set(viewerCount);
                });
            });
        });

        channelToWatch.forEach(chan -> {
            metricRegistry.gauge(String.format("channel.%s.viewers", chan), () -> () -> viewersByChan.get(chan).get());
            List.of(1, 2, 3, 4, 5, 6).stream().map(integer -> integer * 5_000).forEach(integer -> {
                final int metricNum = integer / 1000;
                metricRegistry.gauge(String.format("channel.%s.lenIndex.%d", chan, metricNum), () -> () -> storageByChan.get(chan).lenIndex(integer));
                metricRegistry.gauge(String.format("channel.%s.uniqWordsIndex.%d", chan, metricNum), () -> () -> storageByChan.get(chan).uniqWordsIndex(integer));
                metricRegistry.gauge(String.format("channel.%s.spamUniqIndex.%d", chan, metricNum), () -> () -> storageByChan.get(chan).spamUniqIndex(integer));
            });
        });
    }

    private static void initReporters(MetricRegistry metricRegistry) throws UnknownHostException, SocketException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            log.info("Reporting from IP: '{}'", ip);
            Graphite graphite = new Graphite(new InetSocketAddress(CARBON_HOST, CARBON_PORT));
            final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                    .prefixedWith(String.format("%s.twitch.chat", ip.replace(".", "_")))
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .filter(MetricFilter.ALL)
                    .build(graphite);
            reporter.start(1, TimeUnit.SECONDS);
        }
    }
}
