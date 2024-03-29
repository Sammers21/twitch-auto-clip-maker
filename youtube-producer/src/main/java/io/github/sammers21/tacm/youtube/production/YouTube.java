package io.github.sammers21.tacm.youtube.production;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.StoredCredential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStore;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import com.google.common.collect.Lists;
import io.github.sammers21.tacm.youtube.store.PgDataFactory;
import io.github.sammers21.twac.core.db.DB;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class YouTube {

    private final static List<String> SCOPES = Lists.newArrayList("https://www.googleapis.com/auth/youtube.upload");
    private final Logger log;

    private static final String VIDEO_FILE_FORMAT = "video/*";
    /**
     * Define a global instance of the HTTP transport.
     */
    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private static com.google.api.services.youtube.YouTube youtube;

    /**
     * Define a global instance of the JSON factory.
     */
    public static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private final String host;
    private final File youtubeCfgPath;
    private final DB DB;
    private final JsonObject json;
    private final String youtubeChan;

    public YouTube(String host, File youtubeCfgPath, DB DB) {
        this.youtubeCfgPath = youtubeCfgPath;
        this.youtubeChan = this.youtubeCfgPath.getName().replace(".json", "");
        this.host = host;
        log = LoggerFactory.getLogger(String.format("%s:[%s]", YouTube.class.getName(), youtubeChan));
        try {
            this.json = new JsonObject(new String(Files.readAllBytes(Paths.get(this.youtubeCfgPath.getPath()))));
            this.DB = DB;
            log.info("Authorizing on youtube");
            Credential credential = authorize();
            log.info("Youtube auth is OK");
            youtube = new com.google.api.services.youtube.YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName("youtube-producer")
                    .build();
        } catch (IOException e) {
            log.error("YouTube: Failed");
            throw new IllegalStateException("YouTube: Failed initialization");
        }
    }

    private Credential authorize() throws IOException {
        Reader clientSecretReader = new InputStreamReader(new FileInputStream(youtubeCfgPath));
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, clientSecretReader);
        PgDataFactory pgDataFactory = new PgDataFactory(DB);
        DataStore<StoredCredential> youtubeChanDataStore = pgDataFactory.getDataStore(youtubeChan);
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setAccessType("offline")
                .setCredentialDataStore(youtubeChanDataStore)
                .build();
        LocalServerReceiver localReceiver = new LocalServerReceiver.Builder().setHost(host).setPort(8081).build();
        return new AuthorizationCodeInstalledApp(flow, localReceiver).authorize(String.format("%s-key", youtubeChan));
    }

    public String uploadVideo(String title, String description, List<String> tags, File videoFile) throws IOException {
        log.info("Uploading video with title='{}', description={}, tags={}", title, description, tags.stream().collect(Collectors.joining(",", "[", "]")));
        long fLength = videoFile.length();
        // Add extra information to the video before uploading.
        Video videoObjectDefiningMetadata = new Video();
        // Set the video to be publicly visible. This is the default
        // setting. Other supporting settings are "unlisted" and "private."
        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus("public");
        videoObjectDefiningMetadata.setStatus(status);

        // Most of the video's metadata is set on the VideoSnippet object.
        VideoSnippet snippet = new VideoSnippet();

        snippet.setTitle(title);
        snippet.setDescription(description);

        snippet.setTags(tags);
        InputStreamContent mediaContent = new InputStreamContent(VIDEO_FILE_FORMAT, new FileInputStream(videoFile));
        // Insert the video. The command sends three arguments. The first
        // specifies which information the API request is setting and which
        // information the API response should return. The second argument
        // is the video resource that contains metadata about the new video.
        // The third argument is the actual video content.
        videoObjectDefiningMetadata.setSnippet(snippet);
        com.google.api.services.youtube.YouTube.Videos.Insert videoInsert = youtube.videos()
                .insert("snippet,statistics,status", videoObjectDefiningMetadata, mediaContent);

        // Add the completed snippet object to the video resource.
        // Set the upload type and add an event listener.
        MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
        // Indicate whether direct media upload is enabled. A value of
        // "True" indicates that direct media upload is enabled and that
        // the entire media content will be uploaded in a single request.
        // A value of "False," which is the default, indicates that the
        // request will use the resumable media upload protocol, which
        // supports the ability to resume an upload operation after a
        // network interruption or other transmission failure, saving
        // time and bandwidth in the event of network failures.
        uploader.setDirectUploadEnabled(false);

        MediaHttpUploaderProgressListener progressListener = httpUploader -> {
            switch (httpUploader.getUploadState()) {
                case INITIATION_STARTED:
                    log.info("Initiation Started");
                    break;
                case INITIATION_COMPLETE:
                    log.info("Initiation Completed");
                    break;
                case MEDIA_IN_PROGRESS:
                    log.info("Upload in progress");
                    log.info(String.format("Upload percentage: %.02f%%", ((double) httpUploader.getNumBytesUploaded() / (double) fLength) * 100));
                    break;
                case MEDIA_COMPLETE:
                    log.info("Upload Completed!");
                    break;
                case NOT_STARTED:
                    log.info("Upload Not Started!");
                    break;
            }
        };
        uploader.setProgressListener(progressListener);

        // Call the API and upload the video.
        Video returnedVideo = videoInsert.execute();

        // Print data about the newly inserted video from the API response.
        log.info("\n================== Returned Video ==================\n");
        log.info("  - Id: " + returnedVideo.getId());
        log.info("  - Title: " + returnedVideo.getSnippet().getTitle());
        log.info("  - Tags: " + returnedVideo.getSnippet().getTags());
        log.info("  - Privacy Status: " + returnedVideo.getStatus().getPrivacyStatus());
        return returnedVideo.getId();
    }
}
