package com.ismartcoding.plain.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoTest {

    @Test
    fun xchacha_roundTrip() {
        val key = Crypto.generateKey()
        val msg = "hello, LAN — привет — 你好".encodeToByteArray()
        val ct = Crypto.encrypt(key, msg)
        // 24-byte nonce is prepended, plus a 16-byte Poly1305 tag.
        assertTrue(ct.size >= msg.size + 24 + 16)
        val pt = Crypto.decrypt(key, ct)
        assertTrue(pt is DecryptResult.Success && pt.plaintext.contentEquals(msg))
    }

    @Test
    fun xchacha_associatedData_isAuthenticated() {
        val key = Crypto.generateKey()
        val msg = "body".encodeToByteArray()
        val aad = "ctx".encodeToByteArray()
        val ct = Crypto.encrypt(key, msg, aad)
        assertTrue(Crypto.decrypt(key, ct, aad) is DecryptResult.Success)
        // Wrong / missing AAD must fail authentication.
        assertTrue(Crypto.decrypt(key, ct, "other".encodeToByteArray()) is DecryptResult.Failure)
        assertTrue(Crypto.decrypt(key, ct, null) is DecryptResult.Failure)
    }

    @Test
    fun xchacha_wrongKey_fails() {
        val ct = Crypto.encrypt(Crypto.generateKey(), "secret".encodeToByteArray())
        assertTrue(Crypto.decrypt(Crypto.generateKey(), ct) is DecryptResult.Failure)
    }

    @Test
    fun xchacha_tamper_fails() {
        val key = Crypto.generateKey()
        val ct = Crypto.encrypt(key, "secret".encodeToByteArray())
        ct[ct.size - 1] = (ct[ct.size - 1].toInt() xor 0x01).toByte()
        assertTrue(Crypto.decrypt(key, ct) is DecryptResult.Failure)
    }

    @Test
    fun decrypt_never_throws_on_garbage() {
        val key = Crypto.generateKey()
        assertTrue(Crypto.decrypt(key, ByteArray(0)) is DecryptResult.Failure)
        assertTrue(Crypto.decrypt(key, ByteArray(5)) is DecryptResult.Failure)
    }

    @Test
    fun randomPassword_lengthAndAlphabet() {
        val alphabet = PASSWORD_ALPHABET.toSet()
        assertEquals(54, PASSWORD_ALPHABET.length)
        repeat(50) {
            val pw = Crypto.randomPassword(16)
            assertEquals(16, pw.length)
            assertTrue(pw.all { it in alphabet })
        }
        // Overwhelmingly likely to be unique across draws (sanity check, not a strict guarantee).
        val draws = (1..100).map { Crypto.randomPassword(12) }.toSet()
        assertTrue(draws.size > 90)
    }

    @Test
    fun ed25519_signVerify() {
        val kp = Crypto.generateSignKeyPair()
        val msg = "authenticate me".encodeToByteArray()
        val sig = Crypto.sign(kp.privateKey, msg)
        assertTrue(Crypto.verify(kp.publicKey, msg, sig))
        assertFalse(Crypto.verify(kp.publicKey, "tampered".encodeToByteArray(), sig))
        // Wrong public key rejects.
        assertFalse(Crypto.verify(Crypto.generateSignKeyPair().publicKey, msg, sig))
    }

    @Test
    fun ecdh_bothParties_deriveSameSessionKey() {
        val a = Crypto.generateEcKeyPair()
        val b = Crypto.generateEcKeyPair()
        assertEquals(65, a.publicKey.size)
        assertEquals(0x04, a.publicKey[0].toInt() and 0xFF)

        val ka = Crypto.ecdhSessionKey(a.privateKey, b.publicKey)
        val kb = Crypto.ecdhSessionKey(b.privateKey, a.publicKey)
        assertNotNull(ka)
        assertNotNull(kb)
        assertEquals(32, ka.size)
        assertTrue(ka.contentEquals(kb))
    }

    @Test
    fun ecdh_rejects_malformedAndOffCurve_peerKeys() {
        val a = Crypto.generateEcKeyPair()
        // Wrong length.
        assertNull(Crypto.ecdhSessionKey(a.privateKey, ByteArray(10)))
        // Right length, wrong prefix (not uncompressed 0x04).
        assertNull(Crypto.ecdhSessionKey(a.privateKey, ByteArray(65)))
        // Deterministic off-curve point: X=0, Y=1 is not on secp256r1.
        val offCurve = ByteArray(65).also { it[0] = 0x04; it[64] = 0x01 }
        assertNull(Crypto.ecdhSessionKey(a.privateKey, offCurve))
    }

    @Test
    fun constantTimeEquals() {
        val a = byteArrayOf(1, 2, 3, 4)
        assertTrue(Crypto.constantTimeEquals(a, byteArrayOf(1, 2, 3, 4)))
        assertFalse(Crypto.constantTimeEquals(a, byteArrayOf(1, 2, 3, 5)))
        assertFalse(Crypto.constantTimeEquals(a, byteArrayOf(1, 2, 3)))
    }

    @Test
    fun sha_knownVectors() {
        // SHA-256("") and SHA-512("") — well-known empty-input digests.
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            AuthTokens.hex(Crypto.sha256(ByteArray(0))),
        )
        assertEquals(
            "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e",
            AuthTokens.hex(Crypto.sha512(ByteArray(0))),
        )
    }

    @Test
    fun authTokens_derivations() {
        assertEquals(32, AuthTokens.handshakeToken("pw").size)
        val bytes = Crypto.secureRandomBytes(20)
        assertTrue(AuthTokens.unhex(AuthTokens.hex(bytes)).contentEquals(bytes))
        assertEquals(64, AuthTokens.newUrlToken().length) // 32 bytes -> 64 hex chars
    }
}
