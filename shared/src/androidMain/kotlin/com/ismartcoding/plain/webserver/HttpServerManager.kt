package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.crypto.AuthTokens
import com.ismartcoding.plain.crypto.Crypto
import com.ismartcoding.plain.crypto.DecryptResult
import com.ismartcoding.plain.db.AppDb
import com.ismartcoding.plain.platform.newId
import com.ismartcoding.plain.preferences.AppPreferences
import com.ismartcoding.plain.web.AuthRequest
import com.ismartcoding.plain.web.AuthResponse
import com.ismartcoding.plain.web.AuthStatus
import com.ismartcoding.plain.web.api.ApiPipeline
import com.ismartcoding.plain.web.auth.AuthManager
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
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import com.ismartcoding.plain.features.file.FileService
import com.ismartcoding.plain.web.file.FilePaths
import java.io.File
import java.util.concurrent.Semaphore
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.send
import java.net.ServerSocket
import java.security.KeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The web-console auth state machine (spec §5). Exposed so the UI can approve/reject 2FA logins. */
    val authManager: AuthManager = AuthManager(
        store = RoomAuthStore(AppDb.instance),
        twoFactorEnabled = { runBlocking(Dispatchers.IO) { AppPreferences.isTwoFactorEnabled() } },
    )

    private val apiPipeline = ApiPipeline()
    private val apiRegistry = AndroidApiRegistry.build()

    // Zip generation is memory/CPU heavy; serialize it (spec §6: Semaphore(1)).
    private val zipSemaphore = Semaphore(1)

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    private val httpPool = listOf(8080) + (8180..8980 step 100).toList()
    private val sslPool = listOf(8443) + (8043..8943 step 100).toList()

    fun start(): Boolean {
        if (server != null) return true
        val http = firstFreePort(httpPool) ?: return false
        val ssl = firstFreePort(sslPool) ?: return false
        httpPort = http
        sslPort = ssl
        // Rotate the opaque file-URL token on each start (spec §5).
        AndroidWebServer.urlToken = AuthTokens.newUrlToken()

        server = embeddedServer(
            factory = Netty,
            configure = {
                // Plaintext connector is bound to loopback only: the LAN-facing surface is HTTPS
                // exclusively (mDNS advertises the SSL port), so a passive LAN eavesdropper can never
                // capture the query-string urlToken from an http:// /fs request.
                connector { port = http; host = "127.0.0.1" }
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
                // Force HTTP/1.1. With TLS, Ktor-Netty otherwise advertises h2 via ALPN, but Netty's
                // HTTP/2 doesn't serve cleanly on Android's TLS stack — browsers then negotiate h2 and
                // get zero bytes back (ERR_EMPTY_RESPONSE). HTTP/1.1 is correct for this LAN console.
                enableHttp2 = false
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

            // Loopback-only shutdown, additionally gated on the server urlToken so a same-device app
            // or a CSRF "simple request" from a local browser can't stop the server without the
            // secret. Both checks must pass.
            get("/shutdown") {
                val host = call.request.origin.remoteHost
                val loopback = host == "127.0.0.1" || host == "0:0:0:0:0:0:0:1" || host == "::1" || host == "localhost"
                if (loopback && validUrlToken()) {
                    call.respondText("shutting down", ContentType.Text.Plain)
                    stop()
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // Authenticated, encrypted API endpoint (token mode, spec §5/§6).
            post("/graphql") { handleApi() }

            // File serving (spec §6). Authorized by the opaque server urlToken (constant-time),
            // then path-sandboxed. PartialContent handles Range for byte-range streaming.
            get("/fs") { handleFs() }
            get("/zip/dir") { handleZipDir() }
            get("/zip/files") { handleZipFiles() }
            post("/upload") { handleUpload() }

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

    // ---------------------------------------------------------------- file routes

    /** Constant-time check that the request carries the current server urlToken. */
    private fun io.ktor.server.routing.RoutingContext.validUrlToken(): Boolean {
        val provided = call.request.queryParameters["token"] ?: return false
        val expected = AndroidWebServer.urlToken ?: return false
        return Crypto.constantTimeEquals(provided.encodeToByteArray(), expected.encodeToByteArray())
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleFs() {
        if (!validUrlToken()) return call.respond(HttpStatusCode.Unauthorized)
        val path = call.request.queryParameters["path"] ?: return call.respond(HttpStatusCode.BadRequest)
        val file = runCatching { FileService.existingFile(path) }.getOrNull()
        if (file == null || !file.isFile) return call.respond(HttpStatusCode.NotFound)
        if (call.request.queryParameters["dl"] == "1") {
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${FilePaths.sanitizeDownloadName(file.name)}\"",
            )
        }
        call.respondFile(file) // PartialContent adds Range/Accept-Ranges automatically
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleZipDir() {
        if (!validUrlToken()) return call.respond(HttpStatusCode.Unauthorized)
        val path = call.request.queryParameters["path"] ?: return call.respond(HttpStatusCode.BadRequest)
        val dir = runCatching { FileService.existingFile(path) }.getOrNull()
        if (dir == null || !dir.isDirectory) return call.respond(HttpStatusCode.NotFound)
        if (!zipSemaphore.tryAcquire()) return call.respond(HttpStatusCode.TooManyRequests)
        try {
            call.response.headers.append(
                HttpHeaders.ContentDisposition,
                "attachment; filename=\"${FilePaths.sanitizeDownloadName(dir.name)}.zip\"",
            )
            call.respondOutputStream(ContentType.Application.Zip) {
                ZipOutputStream(this).use { zos ->
                    dir.walkTopDown().filter { it.isFile }.forEach { f ->
                        zos.putNextEntry(ZipEntry(FilePaths.zipEntryName(dir.absolutePath, f.absolutePath)))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        } finally {
            zipSemaphore.release()
        }
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleZipFiles() {
        if (!validUrlToken()) return call.respond(HttpStatusCode.Unauthorized)
        val paths = call.request.queryParameters.getAll("path").orEmpty()
        if (paths.isEmpty()) return call.respond(HttpStatusCode.BadRequest)
        val files = paths.mapNotNull { p -> runCatching { FileService.existingFile(p) }.getOrNull() }
            .filter { it.isFile }
        if (files.isEmpty()) return call.respond(HttpStatusCode.NotFound)
        if (!zipSemaphore.tryAcquire()) return call.respond(HttpStatusCode.TooManyRequests)
        try {
            call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=\"files.zip\"")
            call.respondOutputStream(ContentType.Application.Zip) {
                ZipOutputStream(this).use { zos ->
                    files.forEach { f ->
                        zos.putNextEntry(ZipEntry(FilePaths.sanitizeDownloadName(f.name)))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        } finally {
            zipSemaphore.release()
        }
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleUpload() {
        if (!validUrlToken()) return call.respond(HttpStatusCode.Unauthorized)
        val path = call.request.queryParameters["path"] ?: return call.respond(HttpStatusCode.BadRequest)
        val target = runCatching { FileService.writableFile(path) }.getOrNull()
            ?: return call.respond(HttpStatusCode.Forbidden)
        // Stream to a temp sibling, then rename over the target (atomic).
        val tmp = File(target.parentFile, "${target.name}.upload")
        call.receiveStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        call.respondText("ok", ContentType.Text.Plain)
    }

    /**
     * Token-mode API request (spec §5): `c-id` identifies the session; the body is
     * `XChaCha20(sessionToken, "TIMESTAMP|NONCE|{operation,variables}")`. We look up the session,
     * decrypt+replay-check+dispatch via [ApiPipeline], and return the re-encrypted response.
     */
    private suspend fun io.ktor.server.routing.RoutingContext.handleApi() {
        val cid = call.request.header("c-id")
        if (cid.isNullOrEmpty()) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val session = runBlocking(Dispatchers.IO) { AppDb.instance.sessionDao().getByClientId(cid) }
        if (session == null || session.token.isEmpty()) {
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        val token = AuthTokens.unhex(session.token)
        val ciphertext = call.receive<ByteArray>()
        val response = apiPipeline.processEncrypted(token, ciphertext) { req -> apiRegistry.dispatch(req) }
        if (response == null) {
            // Body failed to authenticate/decrypt.
            call.respond(HttpStatusCode.Unauthorized)
            return
        }
        call.respondBytes(response, ContentType.Application.OctetStream)
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
        val cid = call.request.queryParameters["cid"] ?: ""
        val isAuth = call.request.queryParameters["auth"] == "1"
        if (isAuth) doAuthHandshake(cid) else keepAlive()
    }

    /**
     * WS login (spec §5 steps 2–3): the browser sends `XChaCha20(handshakeToken, AuthRequest)` where
     * handshakeToken = SHA-512(loginPassword)[..32]. We decrypt with the same derivation, verify,
     * and reply `XChaCha20(handshakeToken, AuthResponse)`. When 2FA is on we hold the socket until
     * the on-device approval resolves (or a 2-minute timeout).
     */
    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.doAuthHandshake(cid: String) {
        val peer = call.request.origin.remoteHost
        if (!loginRateLimiter.tryAcquire(peer)) {
            close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "rate limited"))
            return
        }
        val password = runBlocking(Dispatchers.IO) { AppPreferences.getLoginPassword() }
        if (password == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "not provisioned"))
            return
        }
        val token = AuthTokens.handshakeToken(password)

        val first = incoming.receive()
        if (first !is Frame.Binary) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "expected binary"))
            return
        }
        val reqJson = when (val d = Crypto.decrypt(token, first.readBytes())) {
            is DecryptResult.Success -> d.plaintext.decodeToString()
            DecryptResult.Failure -> {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "bad handshake"))
                return
            }
        }
        val request = runCatching { json.decodeFromString<AuthRequest>(reqJson) }.getOrNull()
        if (request == null) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "bad request"))
            return
        }

        when (val result = authManager.authenticate(cid, request)) {
            is AuthManager.Result.Granted -> {
                sendEncrypted(token, AuthResponse(AuthStatus.GRANTED.name, result.token, result.sessionId))
            }
            AuthManager.Result.Denied -> {
                sendEncrypted(token, AuthResponse(AuthStatus.DENIED.name))
                close(CloseReason(CloseReason.Codes.NORMAL, "denied"))
            }
            is AuthManager.Result.Pending -> {
                sendEncrypted(token, AuthResponse(AuthStatus.PENDING.name, require2fa = true))
                AndroidWebServer.refreshApprovals()
                val granted = withTimeoutOrNull(2 * 60 * 1000L) {
                    authManager.awaitApproval(result.approvalId)
                }
                AndroidWebServer.refreshApprovals()
                if (granted != null) {
                    sendEncrypted(token, AuthResponse(AuthStatus.GRANTED.name, granted.token, granted.sessionId))
                } else {
                    sendEncrypted(token, AuthResponse(AuthStatus.REJECTED.name))
                    close(CloseReason(CloseReason.Codes.NORMAL, "rejected"))
                }
            }
        }
    }

    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.sendEncrypted(
        token: ByteArray,
        response: AuthResponse,
    ) {
        send(Frame.Binary(true, Crypto.encrypt(token, json.encodeToString(response).encodeToByteArray())))
    }

    /**
     * Registered event socket (spec §5 step 4). Full session-token registration + event fan-out land
     * with the domain phases; for now the socket is held open so the connection contract is live.
     */
    /**
     * Registered event socket (spec §5 step 4): the browser connects `/?cid=…` and proves it holds
     * the session token by sending `XChaCha20(sessionToken, "register")`. On success the socket joins
     * the [WsHub] and receives pushed events until it closes.
     */
    private suspend fun io.ktor.server.websocket.DefaultWebSocketServerSession.keepAlive() {
        val cid = call.request.queryParameters["cid"] ?: ""
        val session = runBlocking(Dispatchers.IO) { AppDb.instance.sessionDao().getByClientId(cid) }
        if (session == null || session.token.isEmpty()) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unauthorized"))
            return
        }
        val token = AuthTokens.unhex(session.token)

        // First frame must decrypt with the session token — proves the client is authenticated.
        val first = incoming.receive()
        if (first !is Frame.Binary || Crypto.decrypt(token, first.readBytes()) is DecryptResult.Failure) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "unauthorized"))
            return
        }

        val connectionId = newId()
        val sink = KtorWsSink(session.clientId, token, this)
        AndroidWebServer.wsHub.add(connectionId, sink)
        try {
            for (frame in incoming) {
                if (frame is Frame.Binary) frame.readBytes() // client pings/keepalives are ignored
            }
        } finally {
            AndroidWebServer.wsHub.remove(connectionId)
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
