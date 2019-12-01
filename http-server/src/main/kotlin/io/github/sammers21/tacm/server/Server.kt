package io.github.sammers21.tacm.server

import io.vertx.core.http.HttpHeaders
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.core.buffer.Buffer
import io.vertx.reactivex.ext.web.Cookie
import io.vertx.reactivex.ext.web.Router
import io.vertx.reactivex.ext.web.RoutingContext
import io.vertx.reactivex.ext.web.client.HttpResponse
import io.vertx.reactivex.ext.web.client.WebClient
import io.vertx.reactivex.ext.web.handler.CookieHandler
import io.vertx.reactivex.ext.web.handler.StaticHandler
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

class Server(private val vertx: Vertx, private val port: Int) {
    private val REGISTRED_REDIRECT_URL = "http://clip-maker.com/redirect-from-twitch"
    private val TWITCH_CLIENT_ID = "rld376iuzgb5mzpfos9kvh6zjdpih1"
    private val TWITCH_CLIENT_SECRET_CODE = "j5ovf00t6x92wlxg6abvck9dsg1qcs"
    private val INDEX_HTML_PAGE: String
    private val TWITCH_ACCESS_TOKEN_COOKIE = "access_token"
    private val TWITCH_REFRESH_TOKEN_COOKIE = "refresh_token"
    private val TWITCH_SCOPE_COOKIE = "twitch_scope"
    private val webClient: WebClient
    fun start() {
        val router = Router.router(vertx)
        router.route().handler(CookieHandler.create())
        router["/"].handler { ctx: RoutingContext ->
            if (ctx.getCookie(TWITCH_ACCESS_TOKEN_COOKIE) == null || ctx.getCookie(TWITCH_REFRESH_TOKEN_COOKIE) == null || ctx.getCookie(TWITCH_SCOPE_COOKIE) == null) {
                ctx.response().setStatusCode(303).putHeader(HttpHeaders.LOCATION, "/login").end()
            } else {
                ctx.response().end(INDEX_HTML_PAGE)
            }
        }
        router.route("/clip-maker-bot-redirect").handler { ctx: RoutingContext -> ctx.response().end("OK") }
        router["/redirect-from-twitch"].handler { ctx: RoutingContext ->
            val request = ctx.request()
            val params = request.params().entries().stream().map { e: Map.Entry<String?, String?> -> String.format("%s: %s", e.key, e.value) }.collect(Collectors.joining("; ", "[", "]"))
            log.info("PATH:{}, PARAMS:{}", request.path(), params)
            val code = request.getParam("code")
            val scope = request.getParam("scope")
            webClient.postAbs("https://id.twitch.tv/oauth2/token")
                    .addQueryParam("client_id", TWITCH_CLIENT_ID)
                    .addQueryParam("client_secret", TWITCH_CLIENT_SECRET_CODE)
                    .addQueryParam("code", code)
                    .addQueryParam("grant_type", "authorization_code")
                    .addQueryParam("redirect_uri", REGISTRED_REDIRECT_URL)
                    .rxSend()
                    .subscribe({ resp: HttpResponse<Buffer?> ->
                        val entries = resp.bodyAsJsonObject()
                        if (resp.statusCode() == 200) {
                            val expiresIsSeconds = entries.getInteger("expires_in")
                            ctx
                                    .addCookie(Cookie.cookie(TWITCH_ACCESS_TOKEN_COOKIE, entries.getString("access_token")).setPath("/").setMaxAge(expiresIsSeconds.toLong()))
                                    .addCookie(Cookie.cookie(TWITCH_SCOPE_COOKIE, scope.replace(" ", "_")).setPath("/").setMaxAge(expiresIsSeconds.toLong()))
                                    .addCookie(Cookie.cookie(TWITCH_REFRESH_TOKEN_COOKIE, entries.getString("refresh_token")).setPath("/").setMaxAge(expiresIsSeconds.toLong()))
                                    .response()
                                    .setStatusCode(303)
                                    .putHeader(HttpHeaders.LOCATION, "/")
                                    .end()
                        } else {
                            log.error("Failed obtain token operation. STATUS:{}, RESPONSE:{}", resp.statusCode(), entries.encodePrettily())
                        }
                    }) { error: Throwable? -> log.error("Obtain token error", error) }
        }
        router["/login"].handler { ctx: RoutingContext ->
            val cookie = ctx.getCookie(TWITCH_ACCESS_TOKEN_COOKIE)
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
        log.info("Started on port:{}", port)
    }

    @Throws(IOException::class)
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

    companion object {
        private val log = LoggerFactory.getLogger(Server::class.java)
    }

    init {
        INDEX_HTML_PAGE = readIndexHtml()
        webClient = WebClient.create(vertx)
    }
}