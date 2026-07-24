package com.ismartcoding.plain.web

import kotlinx.serialization.Serializable

/** Result of an authentication attempt (spec §5). */
enum class AuthStatus {
    /** Credentials accepted, waiting for on-device 2FA approval. */
    PENDING,

    /** Fully authenticated; a session token is issued. */
    GRANTED,

    /** Credentials rejected. */
    DENIED,

    /** The on-device approval was declined. */
    REJECTED,
}

/** Browser → phone login frame (sent XChaCha20-encrypted over the WS with the handshake token). */
@Serializable
data class AuthRequest(
    val password: String,
    val name: String = "",
    val osName: String = "",
    val browserName: String = "",
)

/** Phone → browser login result. On [AuthStatus.GRANTED], [token] is the minted session token. */
@Serializable
data class AuthResponse(
    val status: String,
    val token: String = "",
    val sessionId: String = "",
    val require2fa: Boolean = false,
)

/** `POST /init` response — either paired (has session) or a freshly issued password to display. */
@Serializable
data class InitResponse(
    val paired: Boolean,
    val need2fa: Boolean = true,
)
