package com.ismartcoding.plain.crypto

import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import com.google.crypto.tink.subtle.XChaCha20Poly1305
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyAgreement
import org.bouncycastle.jce.interfaces.ECPublicKey as BcEcPublicKey

/**
 * Android/JVM implementation of the crypto seam (spec §5).
 *
 * Deliberately free of any `android.*` framework call so the whole thing runs under a plain
 * JVM unit test (see CryptoTest). Backed by Google Tink (XChaCha20-Poly1305, Ed25519) and
 * BouncyCastle (secp256r1 ECDH with explicit on-curve validation).
 */
actual object Crypto {

    private const val EC_CURVE = "secp256r1"
    private val bc: BouncyCastleProvider = BouncyCastleProvider().also {
        if (Security.getProvider(it.name) == null) Security.addProvider(it)
    }
    private val ecSpec: ECNamedCurveParameterSpec = ECNamedCurveTable.getParameterSpec(EC_CURVE)

    /** General-purpose CSPRNG (NativePRNG / /dev/urandom on Linux). */
    private val random: SecureRandom = SecureRandom()

    /** Strong CSPRNG for key material. Lazily created — first use may seed from the OS pool. */
    private val strongRandom: SecureRandom by lazy {
        runCatching { SecureRandom.getInstanceStrong() }.getOrDefault(SecureRandom())
    }

    /** AEAD instances are relatively expensive to build; cache per 32-byte key (spec §5). */
    private val aeadCache = ConcurrentHashMap<String, XChaCha20Poly1305>()

    // ---------------------------------------------------------------- Randomness

    actual fun secureRandomBytes(n: Int): ByteArray {
        require(n >= 0) { "n must be non-negative" }
        return ByteArray(n).also { random.nextBytes(it) }
    }

    actual fun generateKey(): ByteArray = ByteArray(32).also { strongRandom.nextBytes(it) }

    actual fun randomPassword(length: Int): String {
        require(length > 0) { "length must be positive" }
        val alphabet = PASSWORD_ALPHABET
        val n = alphabet.length
        check(n == 54) { "PASSWORD_ALPHABET must be 54 chars, was $n" }
        // Rejection sampling: discard bytes in the biased tail so every glyph is equiprobable.
        val bound = 256 - (256 % n)
        val sb = StringBuilder(length)
        val buf = ByteArray(64)
        var i = 0
        while (sb.length < length) {
            if (i == 0) random.nextBytes(buf)
            val v = buf[i].toInt() and 0xFF
            i = (i + 1) % buf.size
            if (v < bound) sb.append(alphabet[v % n])
        }
        return sb.toString()
    }

    // ---------------------------------------------------------- XChaCha20-Poly1305

    private fun aeadFor(key: ByteArray): XChaCha20Poly1305 {
        require(key.size == 32) { "XChaCha20-Poly1305 key must be 32 bytes, was ${key.size}" }
        val id = hex(key)
        return aeadCache.getOrPut(id) { XChaCha20Poly1305(key) }
    }

    actual fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray?): ByteArray =
        aeadFor(key).encrypt(plaintext, associatedData ?: EMPTY)

    actual fun decrypt(key: ByteArray, ciphertext: ByteArray, associatedData: ByteArray?): DecryptResult =
        try {
            DecryptResult.Success(aeadFor(key).decrypt(ciphertext, associatedData ?: EMPTY))
        } catch (_: Throwable) {
            // Any failure — wrong key, bad tag, truncated frame — collapses to one opaque result.
            DecryptResult.Failure
        }

    // -------------------------------------------------------------------- Ed25519

    actual fun generateSignKeyPair(): SignKeyPair {
        val kp = Ed25519Sign.KeyPair.newKeyPair()
        return SignKeyPair(publicKey = kp.publicKey, privateKey = kp.privateKey)
    }

    actual fun sign(privateKey: ByteArray, message: ByteArray): ByteArray =
        Ed25519Sign(privateKey).sign(message)

    actual fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        try {
            Ed25519Verify(publicKey).verify(signature, message)
            true
        } catch (_: Throwable) {
            false
        }

    // ------------------------------------------------------------ ECDH (secp256r1)

    actual fun generateEcKeyPair(): EcKeyPair {
        val gen = KeyPairGenerator.getInstance("EC", bc.name)
        gen.initialize(ECGenParameterSpec(EC_CURVE), random)
        val kp = gen.generateKeyPair()
        val pub = kp.public as BcEcPublicKey
        // Uncompressed SEC1 encoding: 0x04 ‖ X ‖ Y (65 bytes).
        val encoded = pub.q.getEncoded(false)
        return EcKeyPair(publicKey = encoded, privateKey = kp.private)
    }

    actual fun ecdhSessionKey(privateKey: Any, peerPublicKey: ByteArray): ByteArray? {
        if (privateKey !is PrivateKey) return null
        // Format gate: only accept the uncompressed 65-byte SEC1 encoding.
        if (peerPublicKey.size != 65 || peerPublicKey[0].toInt() != 0x04) return null
        return try {
            val point = ecSpec.curve.decodePoint(peerPublicKey)
            // Invalid-curve / small-subgroup defense: the point must be on secp256r1 and finite.
            if (point.isInfinity || !point.isValid) return null
            val kf = KeyFactory.getInstance("EC", bc.name)
            val peerPub = kf.generatePublic(ECPublicKeySpec(point, ecSpec))
            val ka = KeyAgreement.getInstance("ECDH", bc.name)
            ka.init(privateKey)
            ka.doPhase(peerPub, true)
            val shared = ka.generateSecret()
            sha256(shared)
        } catch (_: Throwable) {
            null
        }
    }

    // --------------------------------------------------------------------- Hashing

    actual fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    actual fun sha512(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-512").digest(data)

    // -------------------------------------------------------------- Constant time

    actual fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        // Length is not itself a secret here; branch on it, then compare bytes in constant time.
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    // ----------------------------------------------------------------- Internals

    private val EMPTY = ByteArray(0)
    private val HEX = "0123456789abcdef".toCharArray()

    private fun hex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
