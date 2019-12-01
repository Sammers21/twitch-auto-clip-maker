package io.github.sammers21.tacm.server

import io.vertx.reactivex.core.Vertx

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    var port = 8080
    if (args.size == 1) {
        port = args[0].toInt()
    }
    val server = Server(vertx, port)
    server.start()
}