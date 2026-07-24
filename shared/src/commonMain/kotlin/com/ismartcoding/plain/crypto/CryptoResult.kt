package com.ismartcoding.plain.crypto

/**
 * Outcome of an authenticated-decryption attempt.
 *
 * Decryption never throws across the [Crypto] seam — callers branch on this so that
 * a forged/replayed/corrupt frame is a normal control-flow event, not an exception that
 * could leak timing or stack details. See spec §5.
 */
sealed interface DecryptResult {
    data class Success(val plaintext: ByteArray) : DecryptResult {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Success && plaintext.contentEquals(other.plaintext))

        override fun hashCode(): Int = plaintext.contentHashCode()
    }

    /** Authentication failed, key wrong, or ciphertext malformed. Deliberately opaque. */
    data object Failure : DecryptResult
}

/** An ephemeral ECDH (secp256r1) key pair. Public key is the uncompressed SEC1 encoding (65 bytes). */
class EcKeyPair(
    val publicKey: ByteArray,
    /** Opaque handle to the private key material; never serialized off-device. */
    val privateKey: Any,
)

/** An Ed25519 signing key pair. */
class SignKeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
)
