package com.ismartcoding.plain.web.auth

import com.ismartcoding.plain.crypto.AuthTokens
import com.ismartcoding.plain.crypto.Crypto
import com.ismartcoding.plain.db.DSession
import com.ismartcoding.plain.platform.Lock
import com.ismartcoding.plain.platform.epochMillis
import com.ismartcoding.plain.platform.newId
import com.ismartcoding.plain.web.AuthRequest
import com.ismartcoding.plain.web.AuthStatus
import kotlinx.coroutines.CompletableDeferred

/** Persistence seam for the auth layer — implemented over Room + DataStore on Android. */
interface AuthStore {
    /** hex(SHA-256(loginPassword)) or null if the device has no login password yet. */
    fun passwordHash(): String?
    fun saveSession(session: DSession)
    fun sessionByToken(token: String): DSession?
}

/** A login awaiting on-device 2FA approval (spec §5). Surfaced to the UI for approve/reject. */
data class PendingApproval(
    val id: String,
    val clientId: String,
    val name: String,
    val osName: String,
    val browserName: String,
    val createdAt: Long,
    // The session token/id are pre-minted and only persisted once the user approves.
    internal val token: String,
    internal val sessionId: String,
)

/**
 * The web-console authentication state machine (spec §5), deliberately free of Ktor/DB types so it
 * is unit-testable. Password comparison is constant-time; session tokens come from the CSPRNG.
 */
class AuthManager(
    private val store: AuthStore,
    private val twoFactorEnabled: () -> Boolean,
    private val now: () -> Long = ::epochMillis,
) {
    sealed interface Result {
        data class Granted(val token: String, val sessionId: String) : Result
        data class Pending(val approvalId: String) : Result
        data object Denied : Result

        val status: AuthStatus
            get() = when (this) {
                is Granted -> AuthStatus.GRANTED
                is Pending -> AuthStatus.PENDING
                Denied -> AuthStatus.DENIED
            }
    }

    private val pending = LinkedHashMap<String, PendingApproval>()
    private val awaiters = HashMap<String, CompletableDeferred<Result.Granted?>>()
    private val lock = Lock()

    /**
     * Verify [req]'s password (constant-time). On success either grant immediately or, when 2FA is
     * on, create a [PendingApproval] the user must approve on the device.
     */
    fun authenticate(clientId: String, req: AuthRequest): Result {
        val storedHex = store.passwordHash() ?: return Result.Denied
        val expected = AuthTokens.unhex(storedHex)
        val actual = Crypto.sha256(req.password.encodeToByteArray())
        if (!Crypto.constantTimeEquals(actual, expected)) return Result.Denied

        val token = AuthTokens.newSessionToken()
        val sessionId = newId()

        if (twoFactorEnabled()) {
            val approval = PendingApproval(
                id = newId(),
                clientId = clientId,
                name = req.name,
                osName = req.osName,
                browserName = req.browserName,
                createdAt = now(),
                token = token,
                sessionId = sessionId,
            )
            lock.withLock {
                pending[approval.id] = approval
                awaiters[approval.id] = CompletableDeferred()
            }
            return Result.Pending(approval.id)
        }

        persistSession(clientId, req, token, sessionId)
        return Result.Granted(token, sessionId)
    }

    /** User approved a pending login on the device → persist the session and grant. */
    fun approve(approvalId: String): Result.Granted? {
        val approval = lock.withLock { pending.remove(approvalId) } ?: return null
        persistSession(
            clientId = approval.clientId,
            req = AuthRequest(password = "", name = approval.name, osName = approval.osName, browserName = approval.browserName),
            token = approval.token,
            sessionId = approval.sessionId,
        )
        val granted = Result.Granted(approval.token, approval.sessionId)
        lock.withLock { awaiters.remove(approvalId) }?.complete(granted)
        return granted
    }

    /** User declined a pending login on the device. */
    fun reject(approvalId: String): Boolean {
        val removed = lock.withLock { pending.remove(approvalId) } != null
        lock.withLock { awaiters.remove(approvalId) }?.complete(null)
        return removed
    }

    fun pendingApprovals(): List<PendingApproval> = lock.withLock { pending.values.toList() }

    /**
     * Suspend until the pending login [approvalId] is approved (→ [Result.Granted]) or rejected
     * (→ null). Returns null immediately if the id is unknown. The caller applies any timeout.
     */
    suspend fun awaitApproval(approvalId: String): Result.Granted? {
        val deferred = lock.withLock { awaiters[approvalId] } ?: return null
        return deferred.await()
    }

    /** Resolve a session by its bearer/session token (constant-time lookup via the store). */
    fun sessionForToken(token: String): DSession? = store.sessionByToken(token)

    private fun persistSession(clientId: String, req: AuthRequest, token: String, sessionId: String) {
        val ts = now()
        store.saveSession(
            DSession(
                id = sessionId,
                clientId = clientId,
                name = req.name,
                token = token,
                tokenType = "session",
                osName = req.osName,
                browserName = req.browserName,
                createdAt = ts,
                updatedAt = ts,
            ),
        )
    }
}
