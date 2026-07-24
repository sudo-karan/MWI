package com.ismartcoding.plain.web

import com.ismartcoding.plain.crypto.Crypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WsFrameTest {

    @Test
    fun roundTrip_preservesTypeAndPayload() {
        val token = Crypto.generateKey()
        val payload = "{\"hello\":\"world\"}".encodeToByteArray()
        val frame = WsFrame.encode(WebEventType.NOTIFICATION, token, payload)
        // 4-byte header + 24-byte nonce + ciphertext + 16-byte tag.
        assertTrue(frame.size >= 4 + 24 + payload.size + 16)

        val decoded = WsFrame.decode(token, frame)
        assertNotNull(decoded)
        assertEquals(WebEventType.NOTIFICATION.code, decoded.type)
        assertEquals(WebEventType.NOTIFICATION, decoded.eventType)
        assertTrue(decoded.payload.contentEquals(payload))
    }

    @Test
    fun bigEndianTypeHeader() {
        val token = Crypto.generateKey()
        val frame = WsFrame.encode(0x01020304, token, ByteArray(0))
        assertEquals(0x01.toByte(), frame[0])
        assertEquals(0x02.toByte(), frame[1])
        assertEquals(0x03.toByte(), frame[2])
        assertEquals(0x04.toByte(), frame[3])
    }

    @Test
    fun wrongToken_orTruncated_decodesToNull() {
        val token = Crypto.generateKey()
        val frame = WsFrame.encode(1, token, "secret".encodeToByteArray())
        assertNull(WsFrame.decode(Crypto.generateKey(), frame))
        assertNull(WsFrame.decode(token, frame.copyOfRange(0, 3)))       // shorter than header
        assertNull(WsFrame.decode(token, frame.copyOfRange(0, frame.size - 1))) // tampered/truncated body
    }
}

class TokenEnvelopeTest {

    @Test
    fun parse_extractsThreeParts_jsonMayContainPipes() {
        val json = "{\"q\":\"a|b|c\"}"
        val raw = TokenEnvelope.format(1720000000000L, "nonce-xyz", json)
        val p = TokenEnvelope.parse(raw)
        assertNotNull(p)
        assertEquals(1720000000000L, p.timestampMs)
        assertEquals("nonce-xyz", p.nonce)
        assertEquals(json, p.json) // pipes inside JSON are preserved
    }

    @Test
    fun parse_rejectsMalformed() {
        assertNull(TokenEnvelope.parse(""))
        assertNull(TokenEnvelope.parse("noseparators"))
        assertNull(TokenEnvelope.parse("123|onlyonesep"))
        assertNull(TokenEnvelope.parse("notanumber|nonce|{}"))
        assertNull(TokenEnvelope.parse("|nonce|{}"))        // empty timestamp
        assertNull(TokenEnvelope.parse("123||{}"))          // empty nonce
    }

    @Test
    fun eventType_codeLookupIsStable() {
        assertEquals(WebEventType.CALL_STATE_CHANGED, WebEventType.fromCode(1500))
        assertNull(WebEventType.fromCode(999999))
    }
}
