package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.web.WsSink
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send

/** A [WsSink] backed by a Ktor WebSocket session — sends event frames as binary. */
class KtorWsSink(
    override val clientId: String,
    override val token: ByteArray,
    private val session: DefaultWebSocketServerSession,
) : WsSink {
    override suspend fun send(bytes: ByteArray) {
        session.send(Frame.Binary(fin = true, data = bytes))
    }
}
