package com.ismartcoding.plain.webserver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.ismartcoding.plain.crypto.AuthTokens
import com.ismartcoding.plain.crypto.Crypto
import com.ismartcoding.plain.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Foreground service that owns the embedded web server (spec §8). Starting/stopping the server is
 * decoupled from the UI so it survives configuration changes and keeps running in the background.
 */
class HttpServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var manager: HttpServerManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { startServer() }
        return START_STICKY
    }

    private suspend fun startServer() {
        if (manager != null) return
        // Provision the machine-generated web-console login password once, and publish it so the UI
        // can show what to type in the browser.
        val loginPassword = AppPreferences.getLoginPassword() ?: Crypto.randomPassword(16).also {
            AppPreferences.setLoginPassword(it)
            AppPreferences.setPasswordHash(AuthTokens.passwordHash(it))
        }
        AndroidWebServer.loginPassword.value = loginPassword

        // Keystore password: persisted, or generated once with the CSPRNG and stored.
        val password = (AppPreferences.getKeystorePassword() ?: Crypto.randomPassword(24)
            .also { AppPreferences.setKeystorePassword(it) }).toCharArray()

        val keystoreFile = File(filesDir, "mwi.bks")
        val keyStore = TlsKeystore(keystoreFile, password).loadOrCreate()
        val m = HttpServerManager(keyStore, password)
        m.start()
        manager = m
        AndroidWebServer.onStarted(m)
    }

    override fun onDestroy() {
        manager?.stop()
        manager = null
        AndroidWebServer.onStopped()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Web server",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "MWI web console is running" }
            nm.createNotificationChannel(channel)
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("MWI web console")
            .setContentText("Serving on your local network")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mwi_web_server"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, HttpServerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HttpServerService::class.java))
        }
    }
}
