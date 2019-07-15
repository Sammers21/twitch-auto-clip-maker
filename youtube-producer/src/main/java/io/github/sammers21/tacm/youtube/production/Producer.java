package io.github.sammers21.tacm.youtube.production;

import io.github.sammers21.twac.core.db.DbController;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Producer {

    private final Logger log;

    private final Vertx vertx;
    private final JsonObject productionPolicy;
    private final YouTube youTube;
    private final VideoMaker videoMaker;
    private final DbController dbController;

    private final String youtubeChan;
    private final JsonObject settings;
    private final Set<String> twitchChans;
    private final Integer maxVideosDaily;

    public Producer(Vertx vertx, JsonObject productionPolicy, YouTube youTube, VideoMaker videoMaker, DbController dbController) {
        this.vertx = vertx;
        this.productionPolicy = productionPolicy.getJsonObject("youtube_production");
        this.youTube = youTube;
        this.videoMaker = videoMaker;
        this.dbController = dbController;
        this.youtubeChan = productionPolicy.getString("youtubeChan");
        this.settings = productionPolicy.getJsonObject("production_settings");
        log = LoggerFactory.getLogger(String.format("%s:[%sъ", Producer.class.getName(), youtubeChan));
        ArrayList<String> strings = new ArrayList<>();
        productionPolicy.getJsonArray("clips_from_channels").forEach(o -> strings.add((String) o));
        twitchChans = new HashSet<>(strings);
        maxVideosDaily = settings.getInteger("max_videos_daily");
    }

    public Single<Integer> releasedTodayTimes() {
        return dbController.releasesOnChan(youtubeChan).map(
                localDateIntegerMap ->
                        localDateIntegerMap.getOrDefault(LocalDate.now(), 0)
        );
    }

    public Single<Boolean> canReleaseToday() {
        return releasedTodayTimes().map(todayReleased -> {
            if (maxVideosDaily > todayReleased) {
                return true;
            } else {
                return false;
            }
        });
    }

    public void runProduction() {
//        dbController.lastReleaseTimeOnChan(youtubeChan)
//                .doOnComplete(() -> {
//                    log.info("Not found last release for {}", youtubeChan);
//                    runReleasesFromNow();
//                })
//                .doOnSuccess(localDateTime -> {
//                    long lastRelease = localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
//                    long now = Instant.now(Clock.systemUTC()).toEpochMilli();
//                    if (now > RELEASES_DELAY_MILLIS + lastRelease) {
//                        log.info("Will release instantly");
//                        runReleasesFromNow();
//                    } else {
//                        long nextRelease = RELEASES_DELAY_MILLIS + lastRelease - now;
//                        log.info("Will release instantly in {} hours {} minutes", TimeUnit.MILLISECONDS.toHours(nextRelease), TimeUnit.MILLISECONDS.toMinutes(nextRelease) % 60);
//                        vertx.setTimer(nextRelease, event -> runReleasesFromNow());
//                    }
//                })
//                .doOnError(throwable -> log.error("error with productionPolicy for {}", youtubeChan, throwable))
//                .subscribe();
    }

    private void runReleasesFromNow() {
//        log.info("Time for an instant release on {}", youtubeChan);
//        attemptToMakeBundle();
//        vertx.setPeriodic(RELEASES_DELAY_MILLIS, event -> {
//            log.info("Regular release on {}", youtubeChan);
//            attemptToMakeBundle();
//        });
    }

    public void attemptToMakeBundle() {
        log.info("Releasing on '{}'", youtubeChan);

        canReleaseToday()
                .subscribe(can -> {
                    if (can) {

                    } else {
                        log.info("Can't release videos today");
                    }
                }, error -> log.error("db problems", error));
//
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
