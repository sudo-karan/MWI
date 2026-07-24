package com.ismartcoding.plain.webserver

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide observable state for the running server. The foreground service updates it; the
 * Compose UI (via [com.ismartcoding.plain.web.WebServerController]) observes it. Server and UI share
 * one process, so a plain in-memory holder is sufficient.
 */
object AndroidWebServer {
    val running = MutableStateFlow(false)
    val sslPort = MutableStateFlow(-1)

    @Volatile
    var manager: HttpServerManager? = null
        private set

    fun onStarted(m: HttpServerManager) {
        manager = m
        sslPort.value = m.sslPort
        running.value = m.isRunning
    }

    fun onStopped() {
        manager = null
        sslPort.value = -1
        running.value = false
    }
}
