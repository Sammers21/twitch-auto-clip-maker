package io.github.sammers21.tacm.server

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.reactivex.plugins.RxJavaPlugins
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.reactivex.core.RxHelper
import io.vertx.reactivex.core.Vertx

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    initStatic(vertx)
    var port = 8080
    if (args.size == 1) {
        port = args[0].toInt()
    }
    val server = Server(vertx, port)
    server.start()
}

fun initStatic(vertx: Vertx) {
    System.setProperty("vertx.logger-delegate-factory-class-name", "io.vertx.core.logging.SLF4JLogDelegateFactory")
    DatabindCodec.mapper().apply {
        registerKotlinModule()
    }
    DatabindCodec.prettyMapper().apply {
        registerKotlinModule()
    }
    RxJavaPlugins.setComputationSchedulerHandler { RxHelper.scheduler(vertx) }
    RxJavaPlugins.setIoSchedulerHandler { RxHelper.blockingScheduler(vertx) }
    RxJavaPlugins.setNewThreadSchedulerHandler { RxHelper.scheduler(vertx) }
}
