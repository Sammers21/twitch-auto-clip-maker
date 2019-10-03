package io.github.sammers21.tacm.cproducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.github.sammers21.tacm.cproducer.decision.ShortIntervalDecisionEngine;
import io.github.sammers21.twac.core.Channel;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.Utils;
import io.github.sammers21.twac.core.chat.TwitchChatClient;
import io.github.sammers21.twac.core.db.DbController;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
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
    private static Set<Channel> CHANNELS_TO_WATCH;
    private static final Map<String, AtomicInteger> viewersByChan = new ConcurrentHashMap<>();
    private static final Map<String, LastMessagesStorage> storageByChan = new ConcurrentHashMap<>();
    private static Vertx vertx;
    private static WebClient webClient;
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
        JsonArray streamers = cfg.getJsonArray("streamers");
        CHANNELS_TO_WATCH = streamers.stream().map(o -> (JsonObject) o).map(entries -> entries.mapTo(Channel.class)).collect(Collectors.toSet());
        if (CHANNELS_TO_WATCH.size() == 0) {
            throw new IllegalStateException("No channels to watch");
        }

        MetricRegistry metricRegistry = new MetricRegistry();
        dbController = new DbController(dbCfg, VERSION);

        vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(CHANNELS_TO_WATCH.size()));
        webClient = WebClient.create(vertx);
        BEARER_TOKEN.set(dbController.token().blockingGet());
        log.info("User token form DB:'{}'", BEARER_TOKEN.get());
        streams = new Streams(vertx, dbController, webClient, CLIENT_ID, BEARER_TOKEN.get(), CHANNELS_TO_WATCH, 5_000, metricRegistry);
        streams.enableStreamsInfo();

        initStoragesAndViewersCounter(CHANNELS_TO_WATCH);

        log.info("Token={}", TOKEN);
        log.info("VERSION={}", VERSION);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("CLIENT_ID={}", CLIENT_ID);
        log.info("CLIENT_SECRET={}", CLIENT_SECRET);
        log.info("BEARER_TOKEN={}", BEARER_TOKEN);
        carbonReporting(metricRegistry, "twitch.chat", CARBON_HOST, CARBON_PORT);

        TwitchChatClient twitchChatClient = new TwitchChatClient(TOKEN, "sammers21");
        twitchChatClient.start();
        CHANNELS_TO_WATCH.stream().map(Channel::getName).forEach(twitchChatClient::joinChannel);
        reportMetrics(CHANNELS_TO_WATCH, vertx, metricRegistry, twitchChatClient);
        twitchChatClient.setMetricRegistry(metricRegistry);
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

    private static void initStoragesAndViewersCounter(Set<Channel> channelToWatch) {
        channelToWatch.forEach(chan -> {
            viewersByChan.put(chan.getName(), new AtomicInteger(0));
            LastMessagesStorage storage = new LastMessagesStorage(2 * 60_000);
            new ShortIntervalDecisionEngine(vertx.getDelegate(), chan, storage, streams).startDecisionEngine();
            storageByChan.put(chan.getName(), storage);
        });
    }

    private static void reportMetrics(Set<Channel> channelToWatch, Vertx vertx, MetricRegistry metricRegistry, TwitchChatClient twitchChatClient) {
        twitchChatClient.messageHandler(msg -> {
            String channelName = msg.getChanName();
            Meter messagesPerSec = metricRegistry.meter(String.format("channel.%s.messages", channelName));
            Meter totalMessagesPerSec = metricRegistry.meter("total.messages");
            totalMessagesPerSec.mark();
            messagesPerSec.mark();
            LastMessagesStorage lastMessagesStorage = storageByChan.get(channelName);
            if (lastMessagesStorage == null) {
                log.info(channelName);
            }
            lastMessagesStorage.push(msg);
        });

        channelToWatch.forEach(channel -> {
            String channelName = channel.getName();
            metricRegistry.gauge(String.format("channel.%s.viewers", channelName), () -> () -> streams.viewersOnStream(channelName));
            List.of(1, 2, 3, 4, 5, 6).stream().map(integer -> integer * 5_000).forEach(integer -> {
                final int metricNum = integer / 1000;
                metricRegistry.gauge(String.format("channel.%s.lenIndex.%d", channelName, metricNum), () -> () -> storageByChan.get(channelName).lenIndex(integer));
                metricRegistry.gauge(String.format("channel.%s.uniqWordsIndex.%d", channelName, metricNum), () -> () -> storageByChan.get(channelName).uniqWordsIndex(integer));
                metricRegistry.gauge(String.format("channel.%s.spamUniqIndex.%d", channelName, metricNum), () -> () -> storageByChan.get(channelName).spamUniqIndex(integer));
            });
        });
    }

}
