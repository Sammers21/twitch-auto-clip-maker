package io.github.sammers21.tacm.youtube;

import io.github.sammers21.twac.core.Utils;
import io.github.sammers21.twac.core.db.DbController;
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

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static String VERSION;

    private static DbController dbController;
    private static YouTube youTube;
    private static WebClient webClient;
    private static Vertx vertx;
    private static VideoMaker vMaker;

    public static void main(String[] args) throws IOException, ParseException {
        vertx = Vertx.vertx();
        webClient = WebClient.create(vertx);
        VERSION = Utils.version();
        log.info("VERSION={}", VERSION);
        Options options = new Options();
        options.addOption("cfg", true, "cfg json config file");
        options.addOption("db", true, "db json config file");
        options.addOption("yt", true, "YouTube json config file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject cfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("cfg")))));
        JsonObject dbCfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))));
        String youtubeCfgPath = cmd.getOptionValue("yt");

        youTube = new YouTube(youtubeCfgPath);
        dbController = new DbController(dbCfg, VERSION);
        vMaker = new VideoMaker(dbController, vertx, webClient, cfg.getString("client_id"));
        File dota2ruhub = vMaker.mkVideoOnChan("dota2ruhub").blockingGet();
        youTube.uploadVideo(dota2ruhub);
    }
}
