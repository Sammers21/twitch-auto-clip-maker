package io.github.sammers21.tacm.youtube;

import io.github.sammers21.twac.core.db.DbController;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Producer {

    private static final Logger log = LoggerFactory.getLogger(Producer.class);

    private final Vertx vertx;
    private final Set<ProductionPolicy> policies;
    private final YouTube youTube;
    private final VideoMaker videoMaker;
    private final DbController dbController;

    private static final Long VIDEOS_IN_A_DAY;
    private static final Long RELEASES_DELAY_MILLIS;

    static {
        VIDEOS_IN_A_DAY = 5L;
        long millisInADay = 24 * 3600 * 1000L;
        RELEASES_DELAY_MILLIS = millisInADay / VIDEOS_IN_A_DAY;
    }

    public Producer(Vertx vertx, Set<ProductionPolicy> policies, YouTube youTube, VideoMaker videoMaker, DbController dbController) {
        this.vertx = vertx;
        this.policies = policies;
        this.youTube = youTube;
        this.videoMaker = videoMaker;
        this.dbController = dbController;
    }

    public void runProduction() {
        policies.forEach(productionPolicy -> {
            dbController.lastReleaseTimeOnChan(productionPolicy.Release_on_youtube_chan())
                    .doOnComplete(() -> {
                        log.info("Not found last release for {}", productionPolicy.Release_on_youtube_chan());
                        runReleasesFromNow(productionPolicy);
                    })
                    .doOnSuccess(localDateTime -> {
                        long lastRelease = localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli();
                        long now = Instant.now(Clock.systemUTC()).toEpochMilli();
                        if (now > RELEASES_DELAY_MILLIS + lastRelease) {
                            log.info("Will release instantly");
                            runReleasesFromNow(productionPolicy);
                        } else {
                            long nextRelease = RELEASES_DELAY_MILLIS + lastRelease - now;
                            log.info("Will release instantly in {} hours {} minutes", TimeUnit.MILLISECONDS.toHours(nextRelease), TimeUnit.MILLISECONDS.toMinutes(nextRelease) % 60);
                            vertx.setTimer(nextRelease, event -> runReleasesFromNow(productionPolicy));
                        }
                    })
                    .doOnError(throwable -> log.error("error with policy for {}", productionPolicy.Release_on_youtube_chan(), throwable))
                    .subscribe();
        });
    }

    private void runReleasesFromNow(ProductionPolicy productionPolicy) {
        log.info("Time for an instant release on {}", productionPolicy.Release_on_youtube_chan());
        attemptToMakeBundle(productionPolicy);
        vertx.setPeriodic(RELEASES_DELAY_MILLIS, event -> {
            log.info("Regular release on {}", productionPolicy.Release_on_youtube_chan());
            attemptToMakeBundle(productionPolicy);
        });
    }

    public void attemptToMakeBundle(ProductionPolicy productionPolicy) {
        log.info("Releasing on '{}'", productionPolicy.Release_on_youtube_chan());
        AtomicReference<List<String>> selectedIds = new AtomicReference<>();
        dbController.selectNonIncludedClips(productionPolicy.Policy_for_streamer())
                .map(strings -> {
                            List<String> ids = strings.stream()
                                    .limit(productionPolicy.Clips_per_release())
                                    .collect(Collectors.toList());
                            selectedIds.set(ids);
                            return ids;
                        }
                )
                .doAfterSuccess(clipIds -> log.info("Clips to include:{}", clipIds.stream().collect(Collectors.joining(", ", "[", "]"))))
                .flatMap(selectedClips ->
                        videoMaker
                                .mkVideoOnChan(productionPolicy)
                                .flatMap(compiledVideoFile -> vertx.rxExecuteBlocking((Future<String> event) -> {
                                            try {
                                                String videoId = youTube.uploadVideo(compiledVideoFile);
                                                event.complete(videoId);
                                            } catch (IOException e) {
                                                event.fail(e);
                                                throw new IllegalStateException("Upload error", e);
                                            }
                                        }).doAfterSuccess(s -> {
                                            String absolutePath = compiledVideoFile.getAbsolutePath();
                                            vertx.fileSystem()
                                                    .rxDelete(absolutePath)
                                                    .subscribe(() -> log.info("Compiled video file remove: {}", absolutePath));
                                        }).toSingle()
                                )
                )
                .flatMapCompletable(videoId -> dbController.bundleOfClips(selectedIds.get(), videoId, productionPolicy.Release_on_youtube_chan()))
                .subscribe(
                        () -> log.info("Bundle are made and available on YouTube"),
                        err -> log.error("Bundle are not made", err)
                );
    }
}
