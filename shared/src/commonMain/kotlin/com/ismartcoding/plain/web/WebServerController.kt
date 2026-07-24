package com.ismartcoding.plain.web

import com.ismartcoding.plain.web.auth.PendingApproval
import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing handle to the embedded web server. The Compose "Web Console" screen observes these
 * flows and calls the actions; the Android actual drives the foreground service and auth manager.
 */
expect object WebServerController {
    val running: StateFlow<Boolean>
    val sslPort: StateFlow<Int>

    /** The login password to type in the browser (machine-generated, shown on-device). */
    val loginPassword: StateFlow<String?>

    /** Logins awaiting on-device 2FA approval. */
    val pendingApprovals: StateFlow<List<PendingApproval>>

    fun start()
    fun stop()

    fun approve(approvalId: String)
    fun reject(approvalId: String)

    /** Current LAN IPv4 for building the browser URL, or null when off-network. */
    fun lanAddress(): String?
}
