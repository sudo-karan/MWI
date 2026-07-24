package com.ismartcoding.plain.crypto

/**
 * Derivations for the two-layer token model (spec §5).
 *
 * There is intentionally no password hashing with a work factor here: the "password" is a
 * high-entropy machine-generated secret (see [Crypto.randomPassword]) exchanged out-of-band
 * (QR / on-device display), not a human-chosen one, so a fast KDF (SHA-512 truncation) is the
 * documented design. Long-lived at-rest secrets are still stored as hashes (see preferences).
 */
object AuthTokens {

    /**
     * The WS handshake key: the first 32 bytes of SHA-512(password). Used to open the
     * `XChaCha20(AuthRequest)` frame during login.
     */
    fun handshakeToken(password: String): ByteArray =
        Crypto.sha512(password.encodeToByteArray()).copyOf(32)

    /**
     * A fresh opaque server-wide URL token, rotated on each server start. Used to mint
     * unguessable `/fs` and `/media` URLs. 32 bytes, hex-encoded for URL-safety.
     */
    fun newUrlToken(): String = hex(Crypto.generateKey())

    /** A fresh per-session API token (32 bytes, hex). Persisted as a [DSession]-scoped secret. */
    fun newSessionToken(): String = hex(Crypto.generateKey())

    /**
     * Stored form of the login password: hex(SHA-256(password)). Compared in constant time by
     * callers. Never store the plaintext password.
     */
    fun passwordHash(password: String): String = hex(Crypto.sha256(password.encodeToByteArray()))

    private val HEX = "0123456789abcdef".toCharArray()

    fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    fun unhex(s: String): ByteArray {
        require(s.length % 2 == 0) { "hex string must have even length" }
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val hi = hexDigit(s[i * 2])
            val lo = hexDigit(s[i * 2 + 1])
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("invalid hex char: $c")
    }
}
