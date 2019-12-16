package io.github.sammers21.tacm.server

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.github.sammers21.twac.core.Utils
import io.github.sammers21.twac.core.db.DB
import io.reactivex.plugins.RxJavaPlugins
import io.vertx.core.json.JsonObject
import io.vertx.core.json.jackson.DatabindCodec
import io.vertx.reactivex.core.RxHelper
import io.vertx.reactivex.core.Vertx
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import java.nio.file.Files
import java.nio.file.Paths

fun main(args: Array<String>) {
    val vertx = Vertx.vertx()
    initStatic(vertx)
    var port = 8080
    if (args.size == 1) {
        port = args[0].toInt()
    }
    val options = Options()
    options.addOption("db", true, "db json config file")
    val parser: CommandLineParser = DefaultParser()
    val cmd = parser.parse(options, args)
    val dbCfg = JsonObject(String(Files.readAllBytes(Paths.get(cmd.getOptionValue("db")))))
    val server = Server(vertx, port, DB(dbCfg, "NO_VERSION"))
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
