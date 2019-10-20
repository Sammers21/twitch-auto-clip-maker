package io.github.sammers21.tacm.server;

import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.Cookie;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.CookieHandler;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final String INDEX_HTML_PAGE;
    private final String TWITCH_CODE_COOKIE = "twitch_code";
    private final String TWITCH_SCOPE_COOKIE = "twitch_scope";

    private final Vertx vertx;
    private final Integer port;

    public Server(Vertx vertx, Integer port) throws IOException {
        this.vertx = vertx;
        this.port = port;
        InputStream resourceAsStream = Server.class.getClassLoader().getResourceAsStream("webroot/index.html");
        INDEX_HTML_PAGE = readString(resourceAsStream);
    }

    private String readString(InputStream resourceAsStream) throws IOException {
        final int bufferSize = 1024;
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        Reader in = new InputStreamReader(resourceAsStream, "UTF-8");
        for (; ; ) {
            int rsz = in.read(buffer, 0, buffer.length);
            if (rsz < 0)
                break;
            out.append(buffer, 0, rsz);
        }
        return out.toString();
    }

    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(CookieHandler.create());
        router.get("/").handler(ctx -> {
            var cookie = ctx.getCookie(TWITCH_CODE_COOKIE);
            if (cookie == null) {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/login").end();
            } else {
                ctx.response().end(INDEX_HTML_PAGE);
            }
        });
        router.get("/redirect-from-twitch").handler(ctx -> {
            HttpServerRequest request = ctx.request();
            String params = request.params().entries().stream().map(e -> String.format("%s: %s", e.getKey(), e.getValue())).collect(Collectors.joining("; ", "[", "]"));
            log.info("PATH:{}, PARAMS:{}", request.path(), params);

            ctx
                    .addCookie(Cookie.cookie(TWITCH_CODE_COOKIE, request.getParam("code")).setPath("/").setMaxAge(TimeUnit.DAYS.toSeconds(10)))
                    .addCookie(Cookie.cookie(TWITCH_SCOPE_COOKIE, request.getParam("scope")).setPath("/").setMaxAge(TimeUnit.DAYS.toSeconds(10)))
                    .response()
                    .setStatusCode(303)
                    .putHeader(HttpHeaders.LOCATION, "/")
                    .end();
        });
        router.get("/login").handler(ctx -> {
            var cookie = ctx.getCookie(TWITCH_CODE_COOKIE);
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
}
