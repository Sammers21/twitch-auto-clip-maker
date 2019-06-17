package io.github.sammers21.twitch;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);
    private static String CARBON_HOST;
    private static Integer CARBON_PORT;
    private static final Map<String, AtomicInteger> viewersByChan = new ConcurrentHashMap<>();
    private static final Map<String, LastMessagesStorage> storageByChan = new ConcurrentHashMap<>();

    public static void main(String[] args) throws SocketException, UnknownHostException {
        String token = args[0];
        CARBON_HOST = args[1];
        CARBON_PORT = Integer.parseInt(args[2]);

        if (args.length < 5) {
            log.error("Should be at least 4 args");
            System.exit(-1);
        }
        Set<String> channelToWatch = Arrays.stream(args).skip(4).collect(Collectors.toSet());
        Vertx vertx = Vertx.vertx(new VertxOptions().setInternalBlockingPoolSize(channelToWatch.size()));
        channelToWatch.forEach(chan -> {
            viewersByChan.put(chan, new AtomicInteger(0));
            storageByChan.put(chan, new LastMessagesStorage(20_000));
        });
        log.info("Token={}", token);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("Channels={}", channelToWatch.stream().collect(Collectors.joining(",", "[", "]")));

        MetricRegistry metricRegistry = new MetricRegistry();
        initReporters(metricRegistry);
        var credential = new OAuth2Credential("twitch", token);
        var twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withEnableTMI(true)
                .withChatAccount(credential)
                .build();
        var chat = twitchClient.getChat();
        channelToWatch.forEach(chat::joinChannel);
        reportMetrics(channelToWatch, vertx, metricRegistry, twitchClient, chat);
//        twitchClient.getHelix().createClip()
        final HttpServer httpServer = vertx.createHttpServer();
        final Router router = Router.router(vertx);

    }

    private static void reportMetrics(Set<String> channelToWatch, Vertx vertx, MetricRegistry metricRegistry, TwitchClient twitchClient, TwitchChat chat) {
        var value = chat.getEventManager().onEvent(ChannelMessageEvent.class);
        value.subscribe((ChannelMessageEvent ok) -> {
            String channelName = ok.getChannel().getName();
            Meter messagesPerSec = metricRegistry.meter(String.format("channel.%s.messages", channelName));
            messagesPerSec.mark();
//            log.info("[{}] {}: {}", channelName, ok.getUser().getName(), ok.getMessage());
            LastMessagesStorage lastMessagesStorage = storageByChan.get(channelName);
            lastMessagesStorage.push(ok);
        });

        vertx.setPeriodic(2_000, periodic -> {
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
            metricRegistry.gauge(String.format("channel.%s.lenIndex", chan), () -> () -> storageByChan.get(chan).lenIndex());
            metricRegistry.gauge(String.format("channel.%s.uniqWordsIndex", chan), () -> () -> storageByChan.get(chan).uniqWordsIndex());
            metricRegistry.gauge(String.format("channel.%s.spamUniqIndex", chan), () -> () -> storageByChan.get(chan).spamUniqIndex());
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
