package io.github.sammers21.tacm.youtube;

import io.github.sammers21.twac.core.db.DbController;
import io.vertx.reactivex.core.Future;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Producer {

    private static final Logger log = LoggerFactory.getLogger(Producer.class);

    private final Vertx vertx;
    private final Set<ProductionPolicy> policies;
    private final YouTube youTube;
    private final VideoMaker videoMaker;
    private final DbController dbController;

    public Producer(Vertx vertx, Set<ProductionPolicy> policies, YouTube youTube, VideoMaker videoMaker, DbController dbController) {
        this.vertx = vertx;
        this.policies = policies;
        this.youTube = youTube;
        this.videoMaker = videoMaker;
        this.dbController = dbController;
    }

    public void runProduction() {
        policies.forEach(productionPolicy -> {
            attemptToMakeBundle(productionPolicy);
            vertx.setPeriodic(300_000, event -> attemptToMakeBundle(productionPolicy));
        });
    }

    public void attemptToMakeBundle(ProductionPolicy productionPolicy) {
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
                .flatMapCompletable(videoId -> dbController.bundleOfClips(selectedIds.get(), videoId))
                .subscribe(
                        () -> log.info("Bundle are made and available on YouTube"),
                        err -> log.error("Bundle are not made", err)
                );
    }
}
