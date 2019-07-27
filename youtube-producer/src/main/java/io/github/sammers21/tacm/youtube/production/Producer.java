package io.github.sammers21.tacm.youtube.production;

import io.github.sammers21.tacm.youtube.settings.Settings;
import io.github.sammers21.tacm.youtube.settings.SimpleIntervalCheck;
import io.github.sammers21.twac.core.db.DbController;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import org.javatuples.Triplet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Producer {

    private final Logger log;
    private final Vertx vertx;
    private final JsonObject productionPolicy;
    private final YouTube youTube;
    private final VideoMaker videoMaker;
    private final DbController dbController;
    private final String youtubeChan;
    private final Settings settings;
    private final Set<String> streamerts;
    private final AtomicBoolean locked;
    private final Random rnd = new Random();

    public Producer(Vertx vertx, JsonObject youtubeJson, YouTube youTube, VideoMaker videoMaker, DbController dbController, AtomicBoolean locked) {
        this.vertx = vertx;
        this.productionPolicy = youtubeJson.getJsonObject("youtube_production");
        this.youTube = youTube;
        this.videoMaker = videoMaker;
        this.dbController = dbController;
        this.youtubeChan = productionPolicy.getString("youtube_chan");
        this.locked = locked;
        this.settings = Settings.parseJson(productionPolicy.getJsonObject("production_settings"));
        log = LoggerFactory.getLogger(String.format("%s:[%s]", Producer.class.getName(), youtubeChan));
        ArrayList<String> streamers = new ArrayList<>();
        productionPolicy.getJsonArray("clips_from_channels").forEach(o -> streamers.add((String) o));
        streamerts = new HashSet<>(streamers);
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
            res = releasedTodayTimes().map(todayReleased -> {
                log.info("today_released={}, max={}", todayReleased, maxReleasesPerDay);
                return maxReleasesPerDay > todayReleased;
            });
        } else {
            res = Single.just(false);
        }
        return res.map(resBool -> {
            boolean locked = this.locked.get();
            boolean decision = resBool && locked;
            log.info("release decision={}[locked={}, video_count={}]", decision, locked, resBool);
            return decision;
        });
    }

    public void runProduction() {
        releaseAttempt();
    }

    private void releaseAttempt() {
        canRelease().subscribe(canRelease -> {
            if (canRelease) {
                if (locked.compareAndSet(false, true)) {
                    log.info("production lock has been taken");
                    attemptToMakeBundle(() -> {
                        boolean res = locked.compareAndSet(true, false);
                        log.info("Releasing production lock={}", res);
                    });
                }
            } else {
                log.info("Can't release today on chan={}", youtubeChan);
            }
        }, error -> log.error("Release error", error));
        // each 5-15 minutes
        long nextAttemptIn = TimeUnit.MINUTES.toMillis(5) + rnd.nextInt(Math.toIntExact(TimeUnit.MINUTES.toMillis(10)));
        log.info("Next release attempt will be in next {}min {}sec", TimeUnit.MILLISECONDS.toMinutes(nextAttemptIn), TimeUnit.MILLISECONDS.toSeconds(nextAttemptIn) % 60);
        vertx.setTimer(nextAttemptIn, event -> releaseAttempt());
    }

    public void attemptToMakeBundle(Runnable release) {
        log.info("Releasing on '{}'", youtubeChan);
        if (settings instanceof SimpleIntervalCheck) {
            int size = streamerts.size();
            int skip = rnd.nextInt(size);
            Set<String> titles = Collections.synchronizedSet(new HashSet<>());
            String streamerToRelease = streamerts.stream().skip(skip).findFirst().get();
            log.info("Streamer {} has been chosen out of {} streamers to be released", streamerToRelease, size);
            SimpleIntervalCheck simpleIntervalCheck = (SimpleIntervalCheck) this.settings;
            Integer max = simpleIntervalCheck.getMax();
            Integer min = simpleIntervalCheck.getMin();
            dbController.titleGroupedNonIncluded(streamerToRelease)
                    .flatMap(triplets -> {
                        List<String> clipsToRelease = new LinkedList<>();
                        for (Triplet<String, LocalDateTime, String[]> next : triplets) {

                            List<String> iterClips = Arrays.asList(next.getValue2());

                            int currentElems = clipsToRelease.size();
                            int lengthOfClipPack = next.getValue2().length;

                            if (currentElems + lengthOfClipPack <= max) {
                                clipsToRelease.addAll(iterClips);
                                titles.add(next.getValue0());
                            } else {
                                // only part needed
                                if (currentElems < min) {
                                    int toTake = max - currentElems;
                                    iterClips.stream().limit(toTake).forEach(clipsToRelease::add);
                                    log.info("Took only part of another clip pack");
                                    break;
                                }
                                // already enough
                                else {
                                    log.info("Full clip pack");
                                    break;
                                }
                            }
                        }
                        Collections.reverse(clipsToRelease);
                        return Single.just(clipsToRelease);
                    })
                    .flatMapMaybe(strings -> {
                        if (strings.size() < min) {
                            log.info("clips_size={} is too low to be released on chan={}", strings.size(), streamerToRelease);
                            return Maybe.empty();
                        } else {
                            log.info("clips_size={} is fine to be released on chan={}", strings.size(), streamerToRelease);
                            return Maybe.just(strings);
                        }
                    })
                    .doOnComplete(() -> log.info("Nothing to release on chan={}, streamer={}", youtubeChan, streamerToRelease))
                    .doAfterSuccess(clipIds -> log.info("Clips to include:{}", clipIds.stream().collect(Collectors.joining(", ", "[", "]"))))
                    .flatMapCompletable((List<String> selectedClips) ->
                            videoMaker
                                    .mkVideoOfClips(selectedClips)
                                    .flatMapCompletable(compiledVideoFile -> {
                                                Maybe<String> maybeUpload = vertx.rxExecuteBlocking(ev -> {
                                                    try {
                                                        String title = mkYouTubeTitle(streamerToRelease, titles);
                                                        List<String> tags = mkYouTubeTags(streamerToRelease, titles);
                                                        String videoId = youTube.uploadVideo(title, "", tags, compiledVideoFile);
                                                        ev.complete(videoId);
                                                    } catch (IOException e) {
                                                        ev.fail(e);
                                                        log.error("upload error", e);
                                                    }
                                                });
                                                return maybeUpload.toSingle()
                                                        .flatMapCompletable(videoId -> dbController.bundleOfClips(selectedClips, videoId, youtubeChan))
                                                        .doAfterTerminate(() -> {
                                                            String absolutePath = compiledVideoFile.getAbsolutePath();
                                                            vertx.fileSystem()
                                                                    .rxDelete(absolutePath)
                                                                    .subscribe(() -> log.info("Compiled video file remove: {}", absolutePath));
                                                        });
                                            }
                                    ))
                    .doAfterTerminate(release::run)
                    .subscribe(
                            () -> log.info("Bundle are made and available on YouTube"),
                            err -> log.error("Can't make a bundle", err)
                    );

        } else {
            log.error("Unknown settings type:{}", settings.getType());
            release.run();
        }
    }

    private List<String> mkYouTubeTags(String streamerToRelease, Set<String> titles) {
        HashSet<String> tags = new HashSet<>();
        tags.add(streamerToRelease);

        for (String title : titles) {
            Arrays.stream(title.split(""))
                    .map(word -> word.replaceAll("[^a-zA-Zа-яА-Я]", ""))
                    .filter(s -> !s.isEmpty())
                    .forEach(tags::add);
        }

        return new LinkedList<>(tags);
    }

    private String mkYouTubeTitle(String streamerName, Set<String> titles) {
        StringBuilder builder = new StringBuilder();
        builder.append(streamerName).append(":").append(" ");
        for (String title : titles) {
            builder.append(title).append(" | ");
        }

        if (builder.length() > 70) {
            return builder.substring(0, 68) + "...";
        } else {
            return builder.toString();
        }
    }

}
