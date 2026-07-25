package com.ismartcoding.plain.web

import com.ismartcoding.plain.crypto.Crypto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSink(
    override val clientId: String,
    override val token: ByteArray,
) : WsSink {
    val sent = mutableListOf<ByteArray>()
    override suspend fun send(bytes: ByteArray) { sent.add(bytes) }
}

class WsHubTest {

    @Test
    fun broadcast_encryptsPerConnectionToken() = runTest {
        val hub = WsHub()
        val t1 = Crypto.generateKey()
        val t2 = Crypto.generateKey()
        val s1 = FakeSink("c1", t1)
        val s2 = FakeSink("c2", t2)
        hub.add("conn-1", s1)
        hub.add("conn-2", s2)
        assertEquals(2, hub.size())

        hub.broadcast(WebEventType.NOTIFICATION, "hi".encodeToByteArray())

        // Each connection received exactly one frame, decodable only with its own token.
        val d1 = WsFrame.decode(t1, s1.sent.single())
        assertNotNull(d1)
        assertEquals(WebEventType.NOTIFICATION.code, d1.type)
        assertEquals("hi", d1.payload.decodeToString())
        // s2's frame is not decodable with t1 (per-connection keying).
        assertNull(WsFrame.decode(t1, s2.sent.single()))
        assertNotNull(WsFrame.decode(t2, s2.sent.single()))
    }

    @Test
    fun sendTo_targetsOneClient() = runTest {
        val hub = WsHub()
        val s1 = FakeSink("c1", Crypto.generateKey())
        val s2 = FakeSink("c2", Crypto.generateKey())
        hub.add("a", s1)
        hub.add("b", s2)

        hub.sendTo("c2", WebEventType.DEVICE_NAME_UPDATED, "x".encodeToByteArray())
        assertTrue(s1.sent.isEmpty())
        assertEquals(1, s2.sent.size)
    }

    @Test
    fun remove_dropsConnection() = runTest {
        val hub = WsHub()
        val s1 = FakeSink("c1", Crypto.generateKey())
        hub.add("a", s1)
        hub.remove("a")
        assertEquals(0, hub.size())
        hub.broadcast(WebEventType.NOTIFICATION, "y".encodeToByteArray())
        assertTrue(s1.sent.isEmpty())
    }
}
