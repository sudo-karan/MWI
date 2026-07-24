package com.ismartcoding.plain.crypto

/**
 * The cryptographic seam (spec §5 — "BUILD FIRST").
 *
 * Every primitive the security model relies on is declared here as an `expect` and
 * implemented per-platform (`Crypto.android.kt` via Google Tink + BouncyCastle). Keeping
 * one narrow interface means the web/auth layers in `commonMain` never touch a JVM/Tink
 * type directly, and the whole thing stays testable.
 *
 * Invariants (enforced by the actual + covered by CryptoTest):
 *  - Body cipher is XChaCha20-Poly1305 with 32-byte keys and a random 24-byte nonce that
 *    is prepended to the ciphertext. AEAD instances are cached per-key.
 *  - All randomness for secrets comes from a CSPRNG. `kotlin.random.Random` is never used
 *    for anything security-relevant.
 *  - `randomPassword` samples uniformly from a fixed 54-char alphabet using rejection
 *    sampling (no modulo bias).
 *  - ECDH peer public keys are validated to be on secp256r1 before agreement; the session
 *    key is SHA-256(shared_secret).
 */
expect object Crypto {

    // ---------------------------------------------------------------- Randomness

    /** `n` cryptographically-secure random bytes (CSPRNG). */
    fun secureRandomBytes(n: Int): ByteArray

    /**
     * A fresh 32-byte symmetric key from the strongest available CSPRNG
     * (`SecureRandom.getInstanceStrong()` on the JVM). Used for tokens/session keys.
     */
    fun generateKey(): ByteArray

    /**
     * A human-typable password of [length] characters, sampled uniformly from the
     * 54-char alphabet [PASSWORD_ALPHABET] via rejection sampling.
     */
    fun randomPassword(length: Int = 12): String

    // ---------------------------------------------------------- XChaCha20-Poly1305

    /**
     * AEAD-encrypt [plaintext] under 32-byte [key]. Output = 24-byte nonce ‖ ciphertext ‖ tag.
     * [associatedData] is authenticated but not encrypted.
     */
    fun encrypt(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray? = null): ByteArray

    /**
     * AEAD-decrypt. Returns [DecryptResult.Failure] (never throws) on any auth/format error.
     */
    fun decrypt(key: ByteArray, ciphertext: ByteArray, associatedData: ByteArray? = null): DecryptResult

    // -------------------------------------------------------------------- Ed25519

    fun generateSignKeyPair(): SignKeyPair
    fun sign(privateKey: ByteArray, message: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean

    // ------------------------------------------------------------ ECDH (secp256r1)

    /** Generate an ephemeral secp256r1 key pair for a key-agreement handshake. */
    fun generateEcKeyPair(): EcKeyPair

    /**
     * Derive a 32-byte session key = SHA-256(ECDH(our private, peer public)).
     * [peerPublicKey] is validated to be a point on secp256r1; returns null if it is not
     * (invalid-curve attack defense).
     */
    fun ecdhSessionKey(privateKey: Any, peerPublicKey: ByteArray): ByteArray?

    // --------------------------------------------------------------------- Hashing

    fun sha256(data: ByteArray): ByteArray
    fun sha512(data: ByteArray): ByteArray

    // -------------------------------------------------------------- Constant time

    /** Constant-time equality — the only comparison allowed on secrets/tokens/MACs. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
}

/**
 * 54 unambiguous characters (visually confusable glyphs — 0/O/o, 1/l/I — removed).
 * The count is asserted in the actual so the rejection-sampling bound stays correct.
 */
const val PASSWORD_ALPHABET: String =
    "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
