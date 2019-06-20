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
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.client.HttpRequest;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static Set<String> CHANNELS_TO_WATCH;
    private static String TOKEN;

    public static void main(String[] args) throws SocketException, UnknownHostException, ParseException {
        Options options = new Options();
        options.addOption("token", true, "token");
        options.addOption("carbon_host", true, "carbon host");
        options.addOption("carbon_port", true, "carbon port");
        options.addOption("client_id", true, "client id");
        options.addOption("client_secret", true, "client secret");
        options.addOption(Option.builder().argName("streamers").hasArg(true).longOpt("streamers").build());
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        TOKEN = cmd.getOptionValue("token");
        CARBON_HOST = cmd.getOptionValue("carbon_host");
        CARBON_PORT = Integer.parseInt(cmd.getOptionValue("carbon_port"));
        CLIENT_ID = cmd.getOptionValue("client_id");
        CLIENT_SECRET = cmd.getOptionValue("client_secret");
        CHANNELS_TO_WATCH = Arrays.stream(cmd.getOptionValue("streamers").split(",")).collect(Collectors.toSet());
        if (CHANNELS_TO_WATCH.size() == 0) {
            throw new IllegalStateException("No channels to watch");
        }

        vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(CHANNELS_TO_WATCH.size()));
        webClient = WebClient.create(vertx);
        var credential = new OAuth2Credential("twitch", TOKEN);
        twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withEnableTMI(true)
                .withEnableHelix(true)
                .withChatAccount(credential)
                .build();

        requestBearerToken();
        fillStreamersInfo(CHANNELS_TO_WATCH);
        initStoragesAndViewersCounter(CHANNELS_TO_WATCH);

        log.info("Token={}", TOKEN);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("CLIENT_ID={}", CLIENT_ID);
        log.info("CLIENT_SECRET={}", CLIENT_SECRET);
        log.info("Channels={}", CHANNELS_TO_WATCH.stream().collect(Collectors.joining(",", "[", "]")));

        MetricRegistry metricRegistry = new MetricRegistry();
        initReporters(metricRegistry);

        var chat = twitchClient.getChat();
        CHANNELS_TO_WATCH.forEach(chat::joinChannel);
        reportMetrics(CHANNELS_TO_WATCH, vertx, metricRegistry, twitchClient);
        intiServer();
    }

    private static void intiServer() {
        final HttpServer httpServer = vertx.createHttpServer();
        final Router router = Router.router(vertx);
        router.route().handler(event -> {
            final String query = event.request().query();
            log.info("Query:{}", query);
            event.response().end("OK");
        });
        httpServer.requestHandler(router).listen(80);
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

        vertx.setPeriodic(15_000, periodic -> {
            channelToWatch.forEach(chan -> {
                vertx.getDelegate().executeBlocking((Future<Integer> event) -> {
                    Integer viewerCount = twitchClient.getMessagingInterface().getChatters(chan).execute().getViewerCount();
                    event.complete(viewerCount);
                }, event -> {
                    Integer viewerCount = event.result();
                    Objects.requireNonNull(viewerCount);
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
