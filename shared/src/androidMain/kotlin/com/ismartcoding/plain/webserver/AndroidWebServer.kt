package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.web.auth.AuthManager
import com.ismartcoding.plain.web.auth.PendingApproval
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide observable state for the running server. The foreground service updates it; the
 * Compose UI (via [com.ismartcoding.plain.web.WebServerController]) observes it. Server and UI share
 * one process, so a plain in-memory holder is sufficient.
 */
object AndroidWebServer {
    val running = MutableStateFlow(false)
    val sslPort = MutableStateFlow(-1)

    /** The login password to type in the browser (machine-generated, shown on-device). */
    val loginPassword = MutableStateFlow<String?>(null)

    /** Logins awaiting on-device 2FA approval. */
    val pendingApprovals = MutableStateFlow<List<PendingApproval>>(emptyList())

    /**
     * Server-wide opaque token embedded in `/fs`, `/zip`, `/upload` URLs (spec §5). Rotates on each
     * server start. Only clients that have authenticated learn it (via the `urlToken` operation).
     */
    @Volatile
    var urlToken: String? = null
        internal set

    @Volatile
    var manager: HttpServerManager? = null
        private set

    private val authManager: AuthManager? get() = manager?.authManager

    fun onStarted(m: HttpServerManager) {
        manager = m
        sslPort.value = m.sslPort
        running.value = m.isRunning
        refreshApprovals()
    }

    fun onStopped() {
        manager = null
        sslPort.value = -1
        running.value = false
        pendingApprovals.value = emptyList()
    }

    fun refreshApprovals() {
        pendingApprovals.value = authManager?.pendingApprovals() ?: emptyList()
    }

    fun approve(approvalId: String) {
        authManager?.approve(approvalId)
        refreshApprovals()
    }

    fun reject(approvalId: String) {
        authManager?.reject(approvalId)
        refreshApprovals()
    }
}
