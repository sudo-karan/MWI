package com.ismartcoding.plain.web.api

import com.ismartcoding.plain.crypto.Crypto
import com.ismartcoding.plain.crypto.DecryptResult
import com.ismartcoding.plain.crypto.ReplayGuard
import com.ismartcoding.plain.web.TokenEnvelope
import kotlinx.serialization.json.Json

/**
 * Per-request API pipeline (spec §5): a token-mode body is
 * `XChaCha20(token, "TIMESTAMP|NONCE|{operation,variables}")`. This decrypts, enforces the
 * [ReplayGuard] window+nonce, dispatches to a domain resolver, and re-encrypts the response with the
 * same token. Errors collapse to generic codes — no internal detail leaks.
 *
 * [process] (string-in/string-out) is crypto-free and directly unit-testable; [processEncrypted]
 * adds the XChaCha20 envelope.
 */
class ApiPipeline(
    private val replayGuard: ReplayGuard = ReplayGuard(),
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    sealed interface Result {
        data class Ok(val responseJson: String) : Result
        data object Stale : Result
        data object Replay : Result
        data object BadRequest : Result
    }

    suspend fun process(body: String, dispatch: suspend (ApiRequest) -> ApiResponse): Result {
        val env = TokenEnvelope.parse(body) ?: return Result.BadRequest
        when (replayGuard.check(env.timestampMs, env.nonce)) {
            ReplayGuard.Result.Stale -> return Result.Stale
            ReplayGuard.Result.Replay -> return Result.Replay
            ReplayGuard.Result.Ok -> {}
        }
        val request = runCatching { json.decodeFromString<ApiRequest>(env.json) }.getOrNull()
            ?: return Result.BadRequest
        val response = runCatching { dispatch(request) }
            .getOrElse { ApiResponse(error = "internal_error") }
        return Result.Ok(json.encodeToString(response))
    }

    /** Full token-mode round trip. Returns null if the request body fails to authenticate/decrypt. */
    suspend fun processEncrypted(
        token: ByteArray,
        ciphertext: ByteArray,
        dispatch: suspend (ApiRequest) -> ApiResponse,
    ): ByteArray? {
        val body = when (val d = Crypto.decrypt(token, ciphertext)) {
            is DecryptResult.Success -> d.plaintext.decodeToString()
            DecryptResult.Failure -> return null
        }
        val responseJson = when (val r = process(body, dispatch)) {
            is Result.Ok -> r.responseJson
            Result.Stale -> json.encodeToString(ApiResponse(error = "stale_request"))
            Result.Replay -> json.encodeToString(ApiResponse(error = "replayed_request"))
            Result.BadRequest -> json.encodeToString(ApiResponse(error = "bad_request"))
        }
        return Crypto.encrypt(token, responseJson.encodeToByteArray())
    }
}
