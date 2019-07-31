package io.github.sammers21.tacm.cproducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import io.github.sammers21.tacm.cproducer.decision.ShortIntervalDecisionEngine;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.Utils;
import io.github.sammers21.twac.core.db.DbController;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static io.github.sammers21.twac.core.Utils.carbonReporting;


public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);
    private static String CARBON_HOST;
    private static String CLIENT_ID;
    private static String CLIENT_SECRET;
    private static AtomicReference<String> BEARER_TOKEN = new AtomicReference<>(null);
    private static Integer CARBON_PORT;
    private static int HTTP_PORT;
    private static String TOKEN;
    private static Set<String> CHANNELS_TO_WATCH;
    private static final Map<String, AtomicInteger> viewersByChan = new ConcurrentHashMap<>();
    private static final Map<String, LastMessagesStorage> storageByChan = new ConcurrentHashMap<>();
    private static Vertx vertx;
    private static WebClient webClient;
    private static TwitchClient twitchClient;
    private static DbController dbController;
    private static Streams streams;
    public static String VERSION;

    public static void main(String[] args) throws IOException, ParseException {
        VERSION = Utils.version();

        Options options = new Options();
        options.addOption("cfg", true, "json config file");
        options.addOption("db", true, "db json config file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject cfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("cfg")))));
        JsonObject dbCfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))));

        TOKEN = cfg.getString("token");
        CARBON_HOST = cfg.getString("carbon_host");
        CARBON_PORT = cfg.getInteger("carbon_port");
        CLIENT_ID = cfg.getString("client_id");
        CLIENT_SECRET = cfg.getString("client_secret");
        HTTP_PORT = cfg.getInteger("http_port");
        CHANNELS_TO_WATCH = Arrays.stream(cfg.getString("streamers").split(",")).collect(Collectors.toSet());
        if (CHANNELS_TO_WATCH.size() == 0) {
            throw new IllegalStateException("No channels to watch");
        }

        MetricRegistry metricRegistry = new MetricRegistry();
        dbController = new DbController(dbCfg, VERSION);

        vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(CHANNELS_TO_WATCH.size()));
        webClient = WebClient.create(vertx);
        var credential = new OAuth2Credential("twitch", TOKEN);
        twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withEnableTMI(true)
                .withEnableHelix(true)
                .withChatAccount(credential)
                .build();

        BEARER_TOKEN.set(dbController.token().blockingGet());
        log.info("User token form DB:'{}'", BEARER_TOKEN.get());
        streams = new Streams(vertx, dbController, webClient, CLIENT_ID, BEARER_TOKEN.get(), CHANNELS_TO_WATCH, 30_000, metricRegistry);
        streams.enableStreamsInfo();

        initStoragesAndViewersCounter(CHANNELS_TO_WATCH);

        log.info("Token={}", TOKEN);
        log.info("VERSION={}", VERSION);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("CLIENT_ID={}", CLIENT_ID);
        log.info("CLIENT_SECRET={}", CLIENT_SECRET);
        log.info("BEARER_TOKEN={}", BEARER_TOKEN);
        log.info("Channels={}", CHANNELS_TO_WATCH.stream().collect(Collectors.joining(",", "[", "]")));
        carbonReporting(metricRegistry, "twitch.chat", CARBON_HOST, CARBON_PORT);

        var chat = twitchClient.getChat();
        CHANNELS_TO_WATCH.forEach(chat::joinChannel);
        reportMetrics(CHANNELS_TO_WATCH, vertx, metricRegistry, twitchClient);
        intiServer();
    }

    private static void intiServer() {
        final HttpServer httpServer = vertx.createHttpServer();
        final Router router = Router.router(vertx);
        router.route("/").handler(event -> {
            final String path = event.request().path();
            log.info("Path:{}", path);
            event.response().end("OK");
        });
        httpServer.requestHandler(router).listen(HTTP_PORT, event -> {
            if (event.succeeded()) {
                log.info("Http server started on port:{}", HTTP_PORT);
            } else {
                log.error("Cant start http server on port:{}", HTTP_PORT);
            }
        });
    }

    private static void initStoragesAndViewersCounter(Set<String> channelToWatch) {
        channelToWatch.forEach(chan -> {
            viewersByChan.put(chan, new AtomicInteger(0));
            LastMessagesStorage storage = new LastMessagesStorage(2 * 60_000);
            new ShortIntervalDecisionEngine(vertx.getDelegate(), chan, storage, streams);
            storageByChan.put(chan, storage);
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

        vertx.setPeriodic(30_000, periodic -> {
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

}
