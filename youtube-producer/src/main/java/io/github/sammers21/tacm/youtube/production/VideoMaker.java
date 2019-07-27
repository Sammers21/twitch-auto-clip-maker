package io.github.sammers21.tacm.youtube.production;

import io.github.sammers21.twac.core.db.DbController;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VideoMaker {

    private static final Logger log = LoggerFactory.getLogger(VideoMaker.class);
    public static final Pattern CLIP_PATTERN = Pattern.compile("https://([\\w-]+)\\.twitch\\.tv/(\\d+)-offset-(\\d+).*");
    private final DbController dbController;
    private final Vertx vertx;
    private final WebClient webClient;
    private final String clientId;

    public VideoMaker(DbController dbController, Vertx vertx, WebClient webClient, String clientId) {
        this.dbController = dbController;
        this.vertx = vertx;
        this.webClient = webClient;
        this.clientId = clientId;
    }

    public Completable downloadClip(String clipId, String pathToSave) {
        return webClient.getAbs("https://api.twitch.tv/helix/clips")
                .putHeader("Client-ID", clientId)
                .addQueryParam("id", clipId)
                .rxSend()
                .flatMap(resp -> {
                    JsonObject entries = resp.bodyAsJsonObject();
                    Objects.requireNonNull(entries);
                    if (!entries.containsKey("data") || !entries.getJsonArray("data").getJsonObject(0).containsKey("thumbnail_url")) {
                        return Single.error(new IllegalStateException("Bad response before clip download: " + entries.encodePrettily()));
                    }
                    String url = entries.getJsonArray("data").getJsonObject(0).getString("thumbnail_url");
                    Matcher matcher = CLIP_PATTERN.matcher(url);
                    matcher.find();
                    String mediaSubDomain = matcher.group(1);
                    String preOffset = matcher.group(2);
                    String offset = matcher.group(3);
                    String downloadUrl = String.format("https://%s.twitch.tv/%s-offset-%s.mp4", mediaSubDomain, preOffset, offset);
                    log.info("Downloading: '{}'", downloadUrl);
                    return webClient.getAbs(downloadUrl).rxSend();
                })
                .flatMapCompletable(resp -> vertx.fileSystem().rxWriteFile(pathToSave, resp.body()))
                .doOnComplete(() -> log.info("Clip has been downloaded:'{}'", new File(pathToSave).getAbsolutePath()))
                .delaySubscription(5, TimeUnit.SECONDS);
    }

    public Single<File> mkVideoOfClips(Collection<String> clips) {
        return Single.just(clips)
                .flatMap(clipIds -> {
                    List<Single<File>> downloads = clipIds.stream().map(clipId -> {
                        String fileName = clipId + ".mp4";
                        return this.downloadClip(clipId, fileName).andThen(Single.defer(() -> Single.just(new File(fileName))));
                    }).collect(Collectors.toList());
                    return Single.concat(downloads).toList();
                })
                .doAfterSuccess(files -> files.forEach(File::deleteOnExit))
                .flatMap(files -> concatVideos(files, String.format("%s.mp4", Instant.now().toString().replace(":", "_"))))
                .doAfterSuccess(File::deleteOnExit);
    }

    private Single<File> concatVideos(List<File> mp4Files, String resultedName) {
        String txtBuildFileText = mp4Files.stream().map(file -> String.format("file '%s'", file.getAbsolutePath())).collect(Collectors.joining("\n"));
        String txtBuildFilePath = String.format("%s.txt", resultedName.replace(".mp4", ""));
        String cmd = String.format("ffmpeg -f concat -safe 0 -i %s -c copy %s", txtBuildFilePath, resultedName);
        File txtBuildFile = new File(txtBuildFilePath);
        return vertx.fileSystem()
                .rxWriteFile(
                        txtBuildFilePath,
                        Buffer.buffer(txtBuildFileText.getBytes(StandardCharsets.UTF_8))
                ).andThen(
                        vertx.rxExecuteBlocking((io.vertx.reactivex.core.Future<File> event) -> {
                            Runtime run = Runtime.getRuntime();
                            Process pr;
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
                ).doFinally(() -> {
                    ArrayList<File> filesToRemove = new ArrayList<>(mp4Files);
                    filesToRemove.add(txtBuildFile);
                    filesToRemove.forEach(file ->
                            vertx.fileSystem()
                                    .rxDelete(file.getAbsolutePath())
                                    .subscribe(() -> log.info("File '{}' has been deleted", file.getAbsolutePath()))
                    );
                });
    }
}
