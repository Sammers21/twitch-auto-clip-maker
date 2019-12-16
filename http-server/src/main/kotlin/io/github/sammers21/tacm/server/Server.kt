package io.github.sammers21.tacm.server

import io.github.sammers21.twac.core.db.DB
import io.reactivex.Single
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.http.Cookie
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.handler.StaticHandler
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class Server(private val vertx: Vertx,
             private val port: Int,
             private val db: DB
) {

    private val webClient: WebClient
    private val INDEX_HTML_PAGE: String

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
        const val REGISTRED_REDIRECT_URL = "http://clip-maker.com/redirect-from-twitch"
        const val TWITCH_CLIENT_ID = "qrwctu82red26ek1d320lpr4uqbz9e"
        const val TWITCH_CLIENT_SECRET_CODE = "vs4zoe717fzeyplhiuqz4peyw5ak37"
        const val CLIP_MAKER_TOKEN = "clip_maker_token"
    }

    init {
        INDEX_HTML_PAGE = readIndexHtml()
        webClient = WebClient.create(vertx, WebClientOptions().setLogActivity(true))
    }

    fun start() {
        val router = Router.router(vertx)
        router.route("/clip-maker-bot-redirect").handler { ctx: RoutingContext -> ctx.response().end("OK") }
        router.get("/").handler { ctx: RoutingContext ->
            if (ctx.getCookie(CLIP_MAKER_TOKEN) == null) {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/login").end()
            } else {
                ctx.response().end(INDEX_HTML_PAGE)
            }
        }
        router.get("/redirect-from-twitch").handler { ctx: RoutingContext ->
            val request = ctx.request()
            val params = request.params().entries().stream().map { e: Map.Entry<String?, String?> -> String.format("%s: %s", e.key, e.value) }.collect(Collectors.joining("; ", "[", "]"))
            log.info("PATH:${request.path()}, PARAMS:${params}")
            val code = request.getParam("code")
            webClient.postAbs("https://id.twitch.tv/oauth2/token")
                    .addQueryParam("client_id", TWITCH_CLIENT_ID)
                    .addQueryParam("client_secret", TWITCH_CLIENT_SECRET_CODE)
                    .addQueryParam("code", code)
                    .addQueryParam("grant_type", "authorization_code")
                    .addQueryParam("redirect_uri", REGISTRED_REDIRECT_URL)
                    .rxSend()
                    .map { it.bodyAsJsonObject().mapTo(TwitchToken::class.java) }
                    .flatMap { token ->
                        webClient.getAbs("https://api.twitch.tv/helix/users")
                                .putHeader("Authorization", "Bearer " + token.access_token)
                                .rxSend()
                                .map { resp -> Pair(token, resp.bodyAsJsonObject().getJsonArray("data").getJsonObject(0).getString("login")) }
                    }.flatMap { db.authWithTwitchToken(it.first.access_token, it.second) }
                    .subscribe({ clipMakerToken ->
                        log.info("AUTH OK: {}", clipMakerToken)
                        ctx
                                .addCookie(Cookie.cookie(CLIP_MAKER_TOKEN, clipMakerToken).setPath("/").setMaxAge(TimeUnit.DAYS.toSeconds(30)))
                                .response()
                                .setStatusCode(303)
                                .putHeader(HttpHeaders.LOCATION, "/")
                                .end()
                    }, { error: Throwable? ->
                        log.error("Obtain token error", error)
                        ctx.response().setStatusCode(400).end("ERROR")
                    })
        }
        router.get("/login").handler { ctx: RoutingContext ->
            val cookie = ctx.getCookie(CLIP_MAKER_TOKEN)
            if (cookie == null) {
                ctx.response().end(INDEX_HTML_PAGE)
            } else {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/").end()
            }
        }
        router.route().handler(StaticHandler.create())
        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
        log.info("Started on port:${port}")
    }

    private fun readIndexHtml(): String {
        val start = System.nanoTime()
        val resourceAsStream = Server::class.java.classLoader.getResourceAsStream("webroot/index.html")
        val bufferSize = 1024
        val buffer = CharArray(bufferSize)
        val out = StringBuilder()
        val `in`: Reader = InputStreamReader(Objects.requireNonNull(resourceAsStream), StandardCharsets.UTF_8)
        while (true) {
            val rsz = `in`.read(buffer, 0, buffer.size)
            if (rsz < 0) break
            out.append(buffer, 0, rsz)
        }
        val stop = System.nanoTime()
        val elapsed = stop - start
        log.info("Index html read time: {}ns", TimeUnit.NANOSECONDS.toMillis(elapsed))
        return out.toString()
    }

    private fun <T> Single<HttpResponse<T>>.okResponse(): Single<HttpResponse<T>> {
        val stack = IllegalStateException("Stack trace from here")
        return this.flatMap {
            when (it.statusCode()) {
                200 -> this
                else -> Single.error(IllegalStateException("received non 200 response. HTTP status code: ${it.statusCode()}", stack))
            }
        }.flatMap {
            val json = it.bodyAsJsonObject()
            val st = json.getInteger("status")
            when {
                st == null -> this
                (st / 100) == 2 -> this
                else -> Single.error(IllegalStateException("received non 200 response in json body response($st): ${json.encodePrettily()}", stack))
            }
        }
    }
}