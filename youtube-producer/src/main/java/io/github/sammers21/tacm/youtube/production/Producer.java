package io.github.sammers21.tacm.youtube.production;

import io.github.sammers21.tacm.youtube.settings.Settings;
import io.github.sammers21.tacm.youtube.settings.SimpleIntervalCheck;
import io.github.sammers21.twac.core.db.DbController;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.javatuples.Quintet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Producer {

    private final Logger log;
    private final Vertx vertx;
    private final JsonObject productionPolicy;
    private final YouTube youTube;
    private final VideoMaker videoMaker;
    private final DbController dbController;
    private final String youtubeChan;
    private final Settings settings;
    private final Set<String> twitchChans;
    private final AtomicBoolean locked;

    public Producer(Vertx vertx, JsonObject youtubeJson, YouTube youTube, VideoMaker videoMaker, DbController dbController, AtomicBoolean locked) {
        this.vertx = vertx;
        this.productionPolicy = youtubeJson.getJsonObject("youtube_production");
        this.youTube = youTube;
        this.videoMaker = videoMaker;
        this.dbController = dbController;
        this.youtubeChan = youtubeJson.getString("youtubeChan");
        this.locked = locked;
        this.settings = Settings.parseJson(productionPolicy.getJsonObject("production_settings"));
        log = LoggerFactory.getLogger(String.format("%s:[%s]", Producer.class.getName(), youtubeChan));
        ArrayList<String> streamers = new ArrayList<>();
        productionPolicy.getJsonArray("clips_from_channels").forEach(o -> streamers.add((String) o));
        twitchChans = new HashSet<>(streamers);
    }

    public Single<Integer> releasedTodayTimes() {
        return dbController.releasesOnChan(youtubeChan).map(
                localDateIntegerMap ->
                        localDateIntegerMap.getOrDefault(LocalDate.now(), 0)
        );
    }

    public Single<Boolean> canRelease() {
        Single<Boolean> res;
        if (settings instanceof SimpleIntervalCheck) {
            SimpleIntervalCheck simpleIntervalCheck = (SimpleIntervalCheck) this.settings;
            Integer maxReleasesPerDay = simpleIntervalCheck.getMaxReleasesPerDay();
            res = releasedTodayTimes().map(todayReleased -> maxReleasesPerDay > todayReleased);
        } else {
            res = Single.just(false);
        }
        return res.map(resBool -> resBool && locked.get());
    }

    public void runProduction() {
        vertx.setPeriodic(10_000, event ->
                canRelease().subscribe(canRelease -> {
                    if (locked.compareAndSet(false, true)) {
                        attemptToMakeBundle(() -> locked.compareAndSet(true, false));
                    }
                }, error -> log.error("Release error", error))
        );
    }

    public void attemptToMakeBundle(Runnable release) {
        log.info("Releasing on '{}'", youtubeChan);
        if (settings instanceof SimpleIntervalCheck) {
            SimpleIntervalCheck simpleIntervalCheck = (SimpleIntervalCheck) this.settings;
            dbController.titleGroupedNonIncluded(twitchChans)
                    .subscribe(quintets -> {
                        Integer max = simpleIntervalCheck.getMax();
                        Integer min = simpleIntervalCheck.getMin();
                        Iterator<Quintet<String, Integer, LocalDateTime, String[], String>> iterator = quintets.iterator();
                        while (iterator.hasNext()) {
                            Quintet<String, Integer, LocalDateTime, String[], String> next = iterator.next();
                        }
                    }, error -> {
                        log.error("Unable to select from postgres grouped");
                        release.run();
                    });
        } else {
            log.error("Unknown settings type:{}", settings.getType());
            release.run();
        }
//        dbController.selectNonIncludedClips(settings.)
//                .map(strings -> {
//                            List<String> ids = strings.stream()
//                                    .limit(productionPolicy.Clips_per_release())
//                                    .collect(Collectors.toList());
//                            selectedIds.set(ids);
//                            return ids;
//                        }
//                )
//                .doAfterSuccess(clipIds -> log.info("Clips to include:{}", clipIds.stream().collect(Collectors.joining(", ", "[", "]"))))
//                .flatMap(selectedClips ->
//                        videoMaker
//                                .mkVideoOnChan(productionPolicy)
//                                .flatMap(compiledVideoFile -> vertx.rxExecuteBlocking((Future<String> event) -> {
//                                            try {
//                                                String videoId = youTube.uploadVideo(compiledVideoFile);
//                                                event.complete(videoId);
//                                            } catch (IOException e) {
//                                                event.fail(e);
//                                                throw new IllegalStateException("Upload error", e);
//                                            }
//                                        }).doFinally(() -> {
//                                            String absolutePath = compiledVideoFile.getAbsolutePath();
//                                            vertx.fileSystem()
//                                                    .rxDelete(absolutePath)
//                                                    .subscribe(() -> log.info("Compiled video file remove: {}", absolutePath));
//                                        }).toSingle()
//                                )
//                )
//                .flatMapCompletable(videoId -> dbController.bundleOfClips(selectedIds.get(), videoId, productionPolicy.Release_on_youtube_chan()))
//                .subscribe(
//                        () -> log.info("Bundle are made and available on YouTube"),
//                        err -> log.error("Bundle are not made", err)
//                );
    }
}
