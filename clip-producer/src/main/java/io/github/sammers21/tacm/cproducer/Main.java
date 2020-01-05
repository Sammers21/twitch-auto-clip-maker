package io.github.sammers21.tacm.cproducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.github.sammers21.tacm.cproducer.decision.LastMsgFirstDecisionEngine;
import io.github.sammers21.tacm.cproducer.decision.ShortIntervalDecisionEngine;
import io.github.sammers21.twac.core.Channel;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.Utils;
import io.github.sammers21.twac.core.chat.TwitchChatClient;
import io.github.sammers21.twac.core.db.DB;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.commons.cli.*;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.Nameable;
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
    private static Set<Channel> CHANNELS_TO_WATCH;
    private static final Map<String, AtomicInteger> viewersByChan = new ConcurrentHashMap<>();
    private static final Map<String, LastMessagesStorage> storageByChan = new ConcurrentHashMap<>();
    private static Vertx vertx;
    private static WebClient webClient;
    private static DB DB;
    private static Streams streams;
    public static String VERSION;
    private static DiscordApi discord;

    public static void main(String[] args) throws IOException, ParseException {
        VERSION = Utils.version();

        Options options = new Options();
        options.addOption("cfg", true, "json config file");
        options.addOption("db", true, "db json config file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject cfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("cfg")))));
        JsonObject dbCfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))));

        CARBON_HOST = cfg.getString("carbon_host");
        CARBON_PORT = cfg.getInteger("carbon_port");
        CLIENT_ID = cfg.getString("client_id");
        CLIENT_SECRET = cfg.getString("client_secret");
        JsonArray streamers = cfg.getJsonArray("streamers");
        CHANNELS_TO_WATCH = streamers.stream()
            .map(o -> (JsonObject) o)
            .map(entries -> entries.mapTo(Channel.class))
            // set default engine
            .map(channel -> {
                String currentEngine = channel.getEngine();
                if (currentEngine == null) {
                    channel.setEngine(ShortIntervalDecisionEngine.class.getSimpleName());
                }
                return channel;
            })
            .collect(Collectors.toSet());
        if (CHANNELS_TO_WATCH.size() == 0) {
            throw new IllegalStateException("No channels to watch");
        }
        discord = new DiscordApiBuilder().setToken("NjMxOTUyNzQwMDk2MTQ3NDk0.XZ-Wcg.slvHO99rAcWcdzaIKZU8IXnt7ks").login().join();

        MetricRegistry metricRegistry = new MetricRegistry();
        DB = new DB(dbCfg, VERSION);

        vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(CHANNELS_TO_WATCH.size()));
        webClient = WebClient.create(vertx);
        BEARER_TOKEN.set(DB.token().blockingGet());
        log.info("User token form DB:'{}'", BEARER_TOKEN.get());
        streams = new Streams(vertx, discord, DB, webClient, CLIENT_ID, BEARER_TOKEN.get(), CHANNELS_TO_WATCH, 5_000, metricRegistry);
        String botInvite = discord.createBotInvite();
        String list = discord.getServers().stream().map(Nameable::getName).collect(Collectors.joining(",", "[", "]"));
        log.info("Server list:{}, inv link:{}", list, botInvite);
        streams.enableStreamsInfo();

        TwitchChatClient twitchChatClient = new TwitchChatClient(BEARER_TOKEN.get(), "clip_maker_bot");
        initStoragesAndViewersCounter(CHANNELS_TO_WATCH, twitchChatClient);

        log.info("VERSION={}", VERSION);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("CLIENT_ID={}", CLIENT_ID);
        log.info("CLIENT_SECRET={}", CLIENT_SECRET);
        log.info("BEARER_TOKEN={}", BEARER_TOKEN);
        carbonReporting(metricRegistry, "twitch.chat", CARBON_HOST, CARBON_PORT);
        twitchChatClient.start();
        CHANNELS_TO_WATCH.stream().map(Channel::getName).forEach(twitchChatClient::joinChannel);
        reportMetrics(CHANNELS_TO_WATCH, metricRegistry, twitchChatClient);
        twitchChatClient.setMetricRegistry(metricRegistry);
    }

    private static void initStoragesAndViewersCounter(Set<Channel> channelToWatch, TwitchChatClient twitchChatClient) {
        channelToWatch.forEach(chan -> {
            viewersByChan.put(chan.getName(), new AtomicInteger(0));
            LastMessagesStorage storage = new LastMessagesStorage(2 * 60_000);
            switch (chan.getEngine()) {
                case "LastMsgFirstDecisionEngine":
                    new LastMsgFirstDecisionEngine(vertx.getDelegate(), chan, storage, streams, twitchChatClient).startDecisionEngine();
                    break;
                case "ShortIntervalDecisionEngine":
                default:
                    new ShortIntervalDecisionEngine(vertx.getDelegate(), chan, storage, streams, twitchChatClient).startDecisionEngine();
                    break;
            }
            storageByChan.put(chan.getName(), storage);
        });
    }

    private static void reportMetrics(Set<Channel> channelToWatch, MetricRegistry metricRegistry, TwitchChatClient twitchChatClient) {
        twitchChatClient.messageHandler(msg -> {
            String channelName = msg.getChanName();
            Meter messagesPerSec = metricRegistry.meter(String.format("channel.%s.messages", channelName));
            Meter totalMessagesPerSec = metricRegistry.meter("total.messages");
            totalMessagesPerSec.mark();
            messagesPerSec.mark();
            LastMessagesStorage lastMessagesStorage = storageByChan.get(channelName);
            if (lastMessagesStorage == null) {
                log.error("Last message storage is null for chan:{}", channelName);
            } else {
                lastMessagesStorage.push(msg);
            }
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
