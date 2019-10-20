package io.github.sammers21.tacm.server;

import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.handler.StaticHandler;

public class Server {

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

        });
        router.route().handler(StaticHandler.create());
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port);
    }
}
