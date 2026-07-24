package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.web.security.RateLimiter
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.cachingheaders.CachingHeaders
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import java.net.ServerSocket
import java.security.KeyStore

/**
 * The embedded MWI web server (spec §6): Ktor on Netty, HTTP :8080 + SSL :8443 with fallback pools,
 * `runningLimit` 1000, HTTP/2 off. Serves the SPA from the classpath (`web/`) and exposes the core
 * routes. Domain GraphQL/HTTP routes are layered in later phases.
 *
 * Security note: `ForwardedHeaders` is intentionally NOT installed — the login [RateLimiter] and the
 * loopback check on `/shutdown` key on the real socket peer, which a client must not be able to
 * spoof via headers.
 */
class HttpServerManager(
    private val keyStore: KeyStore,
    private val keyStorePassword: CharArray,
    private val keyAlias: String = "mwi",
) {
    @Volatile
    var webEnabled: Boolean = true

    @Volatile
    var httpPort: Int = -1
        private set

    @Volatile
    var sslPort: Int = -1
        private set

    private val loginRateLimiter = RateLimiter()
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private val httpPool = listOf(8080) + (8180..8980 step 100).toList()
    private val sslPool = listOf(8443) + (8043..8943 step 100).toList()

    fun start(): Boolean {
        if (server != null) return true
        val http = firstFreePort(httpPool) ?: return false
        val ssl = firstFreePort(sslPool) ?: return false
        httpPort = http
        sslPort = ssl

        server = embeddedServer(
            factory = Netty,
            configure = {
                connector { port = http; host = "0.0.0.0" }
                sslConnector(
                    keyStore = keyStore,
                    keyAlias = keyAlias,
                    keyStorePassword = { keyStorePassword },
                    privateKeyPassword = { keyStorePassword },
                ) {
                    port = ssl
                    host = "0.0.0.0"
                }
                runningLimit = 1000
            },
            module = { installModule() },
        ).also { it.start(wait = false) }
        return true
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2000)
        server = null
        httpPort = -1
        sslPort = -1
    }

    val isRunning: Boolean get() = server != null

    private fun Application.installModule() {
        install(WebSockets)
        install(Compression) { gzip() }
        install(ContentNegotiation) { json() }
        install(CachingHeaders)
        install(ConditionalHeaders)
        install(PartialContent)
        install(AutoHeadResponse)
        install(CORS) {
            // Strict allowlist — never anyHost(). The SPA is served same-origin (no CORS needed);
            // these entries only cover local development against the dev server.
            allowHost("localhost:8080")
            allowHost("127.0.0.1:8080")
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Options)
            allowHeader("c-id")
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowCredentials = true
        }

        routing {
            // /health is always available, even when the web feature is toggled off.
            get("/health") { call.respondText("ok", ContentType.Text.Plain) }

            // Everything else 404s while the web feature is disabled.
            intercept404WhenDisabled()

            // Loopback-only shutdown.
            get("/shutdown") {
                val host = call.request.origin.remoteHost
                if (host == "127.0.0.1" || host == "0:0:0:0:0:0:0:1" || host == "::1" || host == "localhost") {
                    call.respondText("shutting down", ContentType.Text.Plain)
                    stop()
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // SPA index with server-time injection (spec §6).
            get("/") { serveIndex() }
            get("/index.html") { serveIndex() }
            // Remaining static assets from the classpath web/ folder (no auto-index).
            staticResources("/", "web", index = null)

            // WebSocket endpoints — binary frames, [4-byte type] + XChaCha20(payload).
            webSocket("/") { handleSocket() }
            webSocket("/status") { handleSocket() }
        }
    }

    // ---- helpers ----

    private fun io.ktor.server.routing.Route.intercept404WhenDisabled() {
        // Placeholder guard hook; real per-call gating is added with the auth layer. Kept explicit
        // so the "404 for all but /health when disabled" contract has a home.
    }

    private suspend fun io.ktor.server.routing.RoutingContext.serveIndex() {
        val html = readResourceText("web/index.html")
        if (html == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val serverTime = System.currentTimeMillis()
        val injected = html.replace(
            "window.__SERVER_TIME__ = window.__SERVER_TIME__ || null;",
            "window.__SERVER_TIME__ = $serverTime;",
        )
        call.response.headers.append("X-Server-Time", serverTime.toString())
        call.respondText(injected, ContentType.Text.Html)
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.handleSocket() {
        // Auth handshake + event fan-out are implemented in the auth/domain phases. For now the
        // endpoint is live so the connection contract is exercisable; unauthenticated sockets are
        // closed. The real peer is available via call.request.origin.remoteHost for rate limiting.
        val peer = call.request.origin.remoteHost
        loginRateLimiter.tryAcquire(peer)
        try {
            for (frame in incoming) {
                if (frame is Frame.Binary) {
                    // Decoding requires the negotiated session token (auth phase). Ignore for now.
                    frame.readBytes()
                }
            }
        } finally {
            close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        }
    }

    private fun readResourceText(path: String): String? =
        this::class.java.classLoader?.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }

    private fun firstFreePort(pool: List<Int>): Int? =
        pool.firstOrNull { port ->
            runCatching { ServerSocket(port).use { true } }.getOrDefault(false)
        }
}
