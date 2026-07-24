package com.ismartcoding.plain.web

import kotlinx.coroutines.flow.StateFlow

/**
 * UI-facing handle to the embedded web server. The Compose "Web Console" screen observes [running]
 * and [sslPort] and calls [start]/[stop]; the Android actual drives the foreground service.
 */
expect object WebServerController {
    val running: StateFlow<Boolean>
    val sslPort: StateFlow<Int>

    fun start()
    fun stop()

    /** Current LAN IPv4 for building the browser URL, or null when off-network. */
    fun lanAddress(): String?
}
