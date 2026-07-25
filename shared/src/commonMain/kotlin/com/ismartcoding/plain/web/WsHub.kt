package com.ismartcoding.plain.web

import com.ismartcoding.plain.platform.Lock

/**
 * A single registered event socket. Implemented by the Ktor session on Android; abstracted here so
 * the hub is unit-testable. Each connection carries its own 32-byte session token — events are
 * encrypted per-connection with that token.
 */
interface WsSink {
    val clientId: String
    val token: ByteArray
    suspend fun send(bytes: ByteArray)
}

/**
 * Fan-out registry for server→browser WS events (spec §5 step 4 / §6). Every event is framed as
 * `[4-byte type] + XChaCha20(connectionToken, payload)` and delivered to each registered socket
 * (broadcast) or the sockets of one client ([sendTo]).
 *
 * Sends happen outside the lock (only the connection snapshot is taken under it), so a slow client
 * never blocks the registry, and the non-suspend [Lock] is never held across a suspension.
 */
class WsHub {
    private val connections = LinkedHashMap<String, WsSink>()
    private val lock = Lock()

    fun add(connectionId: String, sink: WsSink) = lock.withLock { connections[connectionId] = sink }
    fun remove(connectionId: String) = lock.withLock { connections.remove(connectionId) }
    fun size(): Int = lock.withLock { connections.size }

    suspend fun broadcast(type: Int, payload: ByteArray) =
        deliver(payload, type, snapshot())

    suspend fun broadcast(type: WebEventType, payload: ByteArray) = broadcast(type.code, payload)

    suspend fun sendTo(clientId: String, type: Int, payload: ByteArray) =
        deliver(payload, type, snapshot().filter { it.clientId == clientId })

    suspend fun sendTo(clientId: String, type: WebEventType, payload: ByteArray) =
        sendTo(clientId, type.code, payload)

    private fun snapshot(): List<WsSink> = lock.withLock { connections.values.toList() }

    private suspend fun deliver(payload: ByteArray, type: Int, sinks: List<WsSink>) {
        for (sink in sinks) {
            runCatching { sink.send(WsFrame.encode(type, sink.token, payload)) }
        }
    }
}
