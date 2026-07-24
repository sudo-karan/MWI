package com.ismartcoding.plain.web

import com.ismartcoding.plain.platform.AndroidApp
import com.ismartcoding.plain.platform.lanIpAddress
import com.ismartcoding.plain.web.auth.PendingApproval
import com.ismartcoding.plain.webserver.AndroidWebServer
import com.ismartcoding.plain.webserver.HttpServerService
import kotlinx.coroutines.flow.StateFlow

actual object WebServerController {
    actual val running: StateFlow<Boolean> = AndroidWebServer.running
    actual val sslPort: StateFlow<Int> = AndroidWebServer.sslPort
    actual val loginPassword: StateFlow<String?> = AndroidWebServer.loginPassword
    actual val pendingApprovals: StateFlow<List<PendingApproval>> = AndroidWebServer.pendingApprovals

    actual fun start() = HttpServerService.start(AndroidApp.context)
    actual fun stop() = HttpServerService.stop(AndroidApp.context)

    actual fun approve(approvalId: String) = AndroidWebServer.approve(approvalId)
    actual fun reject(approvalId: String) = AndroidWebServer.reject(approvalId)

    actual fun lanAddress(): String? = lanIpAddress()
}
