package io.github.sammers21.tacm.cproducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import io.github.sammers21.twac.core.Streams;
import io.github.sammers21.twac.core.db.DbController;
import io.reactiverse.pgclient.PgPoolOptions;
import io.reactiverse.reactivex.pgclient.PgClient;
import io.reactivex.Single;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
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
        InputStream resourceAsStream = Main.class.getResourceAsStream("/version.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
        VERSION = reader.readLine();

        Options options = new Options();
        options.addOption("config", true, "json config file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject cfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("config")))));

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
        PgPoolOptions pgOptions = new PgPoolOptions()
                .setPort(cfg.getInteger("pg_port"))
                .setHost(cfg.getString("pg_host"))
                .setDatabase(cfg.getString("pd_db"))
                .setUser(cfg.getString("pg_user"))
                .setPassword(cfg.getString("pg_password"))
                .setMaxSize(5);

        dbController = new DbController(PgClient.pool(pgOptions), VERSION);

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
        initStoragesAndViewersCounter(CHANNELS_TO_WATCH);

        log.info("Token={}", TOKEN);
        log.info("VERSION={}", VERSION);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("CLIENT_ID={}", CLIENT_ID);
        log.info("CLIENT_SECRET={}", CLIENT_SECRET);
        log.info("BEARER_TOKEN={}", BEARER_TOKEN);
        log.info("Channels={}", CHANNELS_TO_WATCH.stream().collect(Collectors.joining(",", "[", "]")));
        initReporters(metricRegistry);

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
        router.route("/cc").handler(event -> {
            String chan = event.request().getParam("chan");
            dbController.selectClips(chan)
                    .flatMap(clipIds -> {
                        List<Single<File>> downloads = clipIds.stream().limit(2).map(clipId -> {
                            String fileName = clipId + ".mp4";
                            return streams.downloadClip(clipId, fileName).andThen(Single.defer(() -> Single.just(new File(fileName))));
                        }).collect(Collectors.toList());
                        return Single.concat(downloads).toList();
                    })
                    .flatMap(files -> concatVideos(files, String.format("%s-%s.mp4", chan, Instant.now().toString().replace(":", "_"))))
                    .subscribe(file -> {
                        log.info("Concat video:{}", file.getAbsolutePath());
                        event.response().end("OK");
                    });
        });
        httpServer.requestHandler(router).listen(HTTP_PORT, event -> {
            if (event.succeeded()) {
                log.info("Http server started on port:{}", HTTP_PORT);
            } else {
                log.error("Cant start http server on port:{}", HTTP_PORT);
            }
        });
    }

    private static Single<File> concatVideos(List<File> mp4Files, String resultedName) {
        String txtBuildFileText = mp4Files.stream().map(file -> String.format("file '%s'", file.getAbsolutePath())).collect(Collectors.joining("\n"));
        String txtBuildFile = String.format("%s.txt", resultedName.replace(".mp4", ""));
        String cmd = String.format("ffmpeg -f concat -safe 0 -i %s -c copy %s", txtBuildFile, resultedName);
        return vertx.fileSystem()
                .rxWriteFile(
                        txtBuildFile,
                        Buffer.buffer(txtBuildFileText.getBytes(StandardCharsets.UTF_8))
                ).andThen(
                        vertx.rxExecuteBlocking((io.vertx.reactivex.core.Future<File> event) -> {
                            Runtime run = Runtime.getRuntime();
                            Process pr = null;
                            try {
                                pr = run.exec(cmd);
                                pr.waitFor();
                                BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
                                BufferedReader buf2 = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                                StringBuilder res = new StringBuilder();
                                String line = "";
                                while ((line = buf.readLine()) != null) {
                                    res.append(line).append("\n");
                                }
                                while ((line = buf2.readLine()) != null) {
                                    res.append(line).append("\n");
                                }
                                log.info("CMD:\n$ {}\n{}", cmd, res.toString());
                                event.complete(new File(resultedName));
                            } catch (IOException | InterruptedException e) {
                                e.printStackTrace();
                                event.fail(e);
                            }
                        }).toSingle()
                );
    }

    private static void initStoragesAndViewersCounter(Set<String> channelToWatch) {
        channelToWatch.forEach(chan -> {
            viewersByChan.put(chan, new AtomicInteger(0));
            LastMessagesStorage storage = new LastMessagesStorage(2 * 60_000);
            new ClipMakingDecisionEngine(vertx.getDelegate(), chan, storage, streams);
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
