package io.github.sammers21.tacm.server;

import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Cookie;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final String INDEX_HTML_PAGE;
    private final String TWITCH_KEY_COOKIE = "twitch-key";

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
        router.get("/").handler(ctx -> {
            var cookie = ctx.getCookie(TWITCH_KEY_COOKIE);
            if (cookie == null) {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/login").end();
            } else {
                ctx.response().end(INDEX_HTML_PAGE);
            }
        });
        router.get("/redirect-from-twitch").handler(ctx -> {

            ctx.addCookie(Cookie.cookie(TWITCH_KEY_COOKIE, "123"))
                    .response()
                    .setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/").end();
        });
        router.get("/login").handler(ctx -> {
            var cookie = ctx.getCookie(TWITCH_KEY_COOKIE);
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
