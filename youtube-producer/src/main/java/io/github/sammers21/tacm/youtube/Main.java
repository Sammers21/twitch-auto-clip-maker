package io.github.sammers21.tacm.youtube;

import io.github.sammers21.tacm.youtube.production.Producer;
import io.github.sammers21.tacm.youtube.production.VideoMaker;
import io.github.sammers21.tacm.youtube.production.YouTube;
import io.github.sammers21.twac.core.Utils;
import io.github.sammers21.twac.core.db.DbController;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static String VERSION;

    private static DbController dbController;
    private static YouTube youTube;
    private static WebClient webClient;
    private static Vertx vertx;
    private static VideoMaker vMaker;

    public static void main(String[] args) throws IOException, ParseException {
        vertx = Vertx.vertx(new VertxOptions()
                .setBlockedThreadCheckInterval(1)
                .setBlockedThreadCheckIntervalUnit(TimeUnit.HOURS)
        );
        webClient = WebClient.create(vertx);
        VERSION = Utils.version();
        log.info("VERSION={}", VERSION);
        Options options = new Options();
        options.addOption("cfg", true, "cfg json config file");
        options.addOption("db", true, "db json config file");
        options.addOption("pd", true, "Youtube production directory with youtube config files");
        options.addOption("host", true, "host");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject cfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("cfg")))));
        JsonObject dbCfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))));
        String host = cmd.getOptionValue("host");
        File productionDir = new File(cmd.getOptionValue("pd"));
        if (productionDir.exists() && productionDir.isDirectory()) {
            log.info("Production dir: OK");
        } else {
            throw new IllegalStateException("Not valid production dir");
        }
        dbController = new DbController(dbCfg, VERSION);
        vMaker = new VideoMaker(dbController, vertx, webClient, cfg.getString("client_id"));
        AtomicBoolean videoReleaseLockFlag = new AtomicBoolean(false);
        Arrays.stream(Objects.requireNonNull(productionDir.listFiles(File::isFile))).forEach(file -> {
            try {
                JsonObject json = new JsonObject(new String(Files.readAllBytes(Paths.get(file.getAbsolutePath()))));
                youTube = new YouTube(host, file, dbController);
                Producer producer = new Producer(vertx, json, youTube, vMaker, dbController, videoReleaseLockFlag);
                producer.runProduction();
            } catch (IOException e) {
                throw new IllegalStateException("Init error");
            }
        });
    }
}
