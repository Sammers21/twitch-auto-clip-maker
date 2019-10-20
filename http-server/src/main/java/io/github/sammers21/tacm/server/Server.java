package io.github.sammers21.tacm.server;

import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server {
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private final String name = "twitch-key";
    private final Vertx vertx;
    private final Integer port;

    public Server(Vertx vertx, Integer port) {
        this.vertx = vertx;
        this.port = port;
    }

    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(event -> {
            event.next();
        });
        router.route().handler(StaticHandler.create());
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port);
        log.info("Started on port:{}", port);
    }
}
