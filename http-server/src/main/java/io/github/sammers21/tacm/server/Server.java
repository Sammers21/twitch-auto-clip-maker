package io.github.sammers21.tacm.server;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Cookie;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.handler.CookieHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final String REGISTRED_REDIRECT_URL = "http://clip-maker.com/redirect-from-twitch";
    private final String TWITCH_CLIENT_ID = "rld376iuzgb5mzpfos9kvh6zjdpih1";
    private final String TWITCH_CLIENT_SECRET_CODE = "j5ovf00t6x92wlxg6abvck9dsg1qcs";
    private final String INDEX_HTML_PAGE;

    private final String TWITCH_ACCESS_TOKEN_COOKIE = "access_token";
    private final String TWITCH_REFRESH_TOKEN_COOKIE = "refresh_token";
    private final String TWITCH_SCOPE_COOKIE = "twitch_scope";

    private final Vertx vertx;
    private final Integer port;
    private final WebClient webClient;

    public Server(Vertx vertx, Integer port) throws IOException {
        this.vertx = vertx;
        this.port = port;
        INDEX_HTML_PAGE = readIndexHtml();
        webClient = WebClient.create(vertx);
    }

    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(CookieHandler.create());
        router.get("/").handler(ctx -> {
            if (ctx.getCookie(TWITCH_ACCESS_TOKEN_COOKIE) == null
                || ctx.getCookie(TWITCH_REFRESH_TOKEN_COOKIE) == null
                || ctx.getCookie(TWITCH_SCOPE_COOKIE) == null
            ) {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/login").end();
            } else {
                ctx.response().end(INDEX_HTML_PAGE);
            }
        });
        router.route("/clip-maker-bot-redirect").handler(ctx -> ctx.response().end("OK"));
        router.get("/redirect-from-twitch").handler(ctx -> {
            HttpServerRequest request = ctx.request();
            String params = request.params().entries().stream().map(e -> String.format("%s: %s", e.getKey(), e.getValue())).collect(Collectors.joining("; ", "[", "]"));
            log.info("PATH:{}, PARAMS:{}", request.path(), params);

            String code = request.getParam("code");
            String scope = request.getParam("scope");

            webClient.postAbs("https://id.twitch.tv/oauth2/token")
                .addQueryParam("client_id", TWITCH_CLIENT_ID)
                .addQueryParam("client_secret", TWITCH_CLIENT_SECRET_CODE)
                .addQueryParam("code", code)
                .addQueryParam("grant_type", "authorization_code")
                .addQueryParam("redirect_uri", REGISTRED_REDIRECT_URL)
                .rxSend()
                .subscribe(resp -> {
                    JsonObject entries = resp.bodyAsJsonObject();
                    if (resp.statusCode() == 200) {
                        Integer expiresIsSeconds = entries.getInteger("expires_in");
                        ctx
                            .addCookie(Cookie.cookie(TWITCH_ACCESS_TOKEN_COOKIE, entries.getString("access_token")).setPath("/").setMaxAge(expiresIsSeconds))
                            .addCookie(Cookie.cookie(TWITCH_SCOPE_COOKIE, scope.replace(" ", "_")).setPath("/").setMaxAge(expiresIsSeconds))
                            .addCookie(Cookie.cookie(TWITCH_REFRESH_TOKEN_COOKIE, entries.getString("refresh_token")).setPath("/").setMaxAge(expiresIsSeconds))
                            .response()
                            .setStatusCode(303)
                            .putHeader(HttpHeaders.LOCATION, "/")
                            .end();
                    } else {
                        log.error("Failed obtain token operation. STATUS:{}, RESPONSE:{}", resp.statusCode(), entries.encodePrettily());
                    }
                }, error -> {
                    log.error("Obtain token error", error);
                });
        });
        router.get("/login").handler(ctx -> {
            var cookie = ctx.getCookie(TWITCH_ACCESS_TOKEN_COOKIE);
            if (cookie == null) {
                ctx.response().end(INDEX_HTML_PAGE);
            } else {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/").end();
            }
        });
        router.route().handler(StaticHandler.create());
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(port);
        log.info("Started on port:{}", port);
    }

    private String readIndexHtml() throws IOException {
        long start = System.nanoTime();
        InputStream resourceAsStream = Server.class.getClassLoader().getResourceAsStream("webroot/index.html");
        final int bufferSize = 1024;
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(Objects.requireNonNull(resourceAsStream), StandardCharsets.UTF_8);
        for (; ; ) {
            int rsz = in.read(buffer, 0, buffer.length);
            if (rsz < 0)
                break;
            out.append(buffer, 0, rsz);
        }
        long stop = System.nanoTime();
        long elapsed = stop - start;
        log.info("Index html read time: {}ns", TimeUnit.NANOSECONDS.toMillis(elapsed));
        return out.toString();
    }
}
