package io.github.sammers21.tacm.server;

import io.vertx.reactivex.core.Vertx;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Vertx vertx = Vertx.vertx();
        int port = 8080;
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        }
        Server server = new Server(vertx, port);
        server.start();
    }
}
