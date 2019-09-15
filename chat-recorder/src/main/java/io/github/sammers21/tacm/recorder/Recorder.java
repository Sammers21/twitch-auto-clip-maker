package io.github.sammers21.tacm.recorder;

import io.github.sammers21.twac.core.chat.TwitchChatClient;
import io.github.sammers21.twac.core.db.DbController;
import io.vertx.core.json.JsonObject;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class Recorder {


    private static final Logger log = LoggerFactory.getLogger(Recorder.class);
    private static DbController dbController;
    private static AtomicBoolean rec = new AtomicBoolean(false);
    private static AtomicBoolean isHighLight = new AtomicBoolean(false);
    private static AtomicReference<String> session = new AtomicReference<>(null);
    private static AtomicLong msgThisSession = new AtomicLong(0);

    public static void main(String[] args) throws ParseException, IOException, InterruptedException {
        Options options = new Options();
        options.addOption("db", true, "db json config file");
        options.addOption("s", "streamer", true, "db json config file");
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        JsonObject dbCfg = new JsonObject(new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))));
        String streamer = cmd.getOptionValue("s");
        dbController = new DbController(dbCfg, "0.1.beta");

        TwitchChatClient twitchChatClient = new TwitchChatClient("ep8dqo0dfx43voyg5seb0l1z2xvn1l", "sammers21");
        twitchChatClient.start();
        twitchChatClient.joinChannel(streamer);
        twitchChatClient.messageHandler(msg -> {
            if (rec.get()) {
                dbController.insertChatMessage(msg, isHighLight.get(), session.get())
                        .subscribe(() -> {
                            msgThisSession.incrementAndGet();
                        }, error -> {
                            log.error("insert error:", error);
                        });
            }
        });

        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine().trim();
            String[] cmdArgs = line.split(" ");
            switch (cmdArgs[0].toLowerCase()) {
                case "n":
                    highLight(false);
                    break;
                case "h":
                    highLight(true);
                    break;
                case "strt":
                case "start":
                    start();
                    break;
                case "stp":
                case "stop":
                    stop();
                    break;
                case "f":
                case "fuck":
                    deleteSession(session.get());
                    break;
                default:
                    log.info("UNKNOWN COMMAND: {}", line);
                    break;
            }
        }
    }

    private static void deleteSession(String sessionId) throws InterruptedException {
        stop();
        dbController.deleteSession(sessionId).subscribe(deleted -> {
            log.info("Deleted session={}, message count={}", session.get(), deleted);
        }, error -> {
            log.error("Delete error", error);
        });
    }

    private static void highLight(boolean hlt) {
        isHighLight.set(hlt);
        log.info("Highlight:{}", hlt);
    }

    private static void stop() throws InterruptedException {
        rec.set(false);
        isHighLight.set(false);
        Thread.sleep(500);
        log.info("Recorded during session messages={}", msgThisSession.get());
    }

    private static void start() {
        log.info("Start recording");
        rec.set(true);
        isHighLight.set(false);
        session.set(RandomStringUtils.randomAlphanumeric(5));
        msgThisSession.set(0);
    }
}
