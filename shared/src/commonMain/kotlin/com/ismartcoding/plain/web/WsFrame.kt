package com.ismartcoding.plain.web

import com.ismartcoding.plain.crypto.Crypto
import com.ismartcoding.plain.crypto.DecryptResult

/**
 * WebSocket binary-frame codec (spec §6): `[4-byte big-endian type] + XChaCha20(token, payload)`.
 *
 * The 4-byte type prefix is authenticated implicitly — it is outside the ciphertext, but the client
 * only trusts a frame whose body decrypts, and a flipped type on a valid body just selects the wrong
 * handler for authentic plaintext (never a forgery). [decode] returns null on any decryption failure.
 */
object WsFrame {

    fun encode(type: Int, token: ByteArray, payload: ByteArray): ByteArray {
        val body = Crypto.encrypt(token, payload)
        val out = ByteArray(4 + body.size)
        out[0] = (type ushr 24).toByte()
        out[1] = (type ushr 16).toByte()
        out[2] = (type ushr 8).toByte()
        out[3] = type.toByte()
        body.copyInto(out, destinationOffset = 4)
        return out
    }

    fun encode(type: WebEventType, token: ByteArray, payload: ByteArray): ByteArray =
        encode(type.code, token, payload)

    data class Decoded(val type: Int, val payload: ByteArray) {
        val eventType: WebEventType? get() = WebEventType.fromCode(type)
    }

    fun decode(token: ByteArray, frame: ByteArray): Decoded? {
        if (frame.size < 4) return null
        val type = ((frame[0].toInt() and 0xFF) shl 24) or
            ((frame[1].toInt() and 0xFF) shl 16) or
            ((frame[2].toInt() and 0xFF) shl 8) or
            (frame[3].toInt() and 0xFF)
        val body = frame.copyOfRange(4, frame.size)
        return when (val pt = Crypto.decrypt(token, body)) {
            is DecryptResult.Success -> Decoded(type, pt.plaintext)
            DecryptResult.Failure -> null
        }
    }
}
