package io.github.sammers21.tacm.youtube;

import com.google.common.collect.Lists;
import io.github.sammers21.twac.core.Utils;
import io.github.sammers21.twac.core.db.DbController;
import io.vertx.core.json.JsonObject;
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
import java.util.List;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private final static List<String> SCOPES = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload");
    private static String VERSION;

    private static DbController dbController;

    public static void main(String[] args) throws IOException, ParseException {
        VERSION = Utils.version();
        log.info("VERSION={}", VERSION);
        Options options = new Options();
        options.addOption("db", true, "db json config file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject dbCfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))));
        dbController = new DbController(dbCfg, VERSION);

    }
}
