package com.ismartcoding.plain.features.screenmirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.IntentCompat
import com.ismartcoding.plain.web.WebEventType
import com.ismartcoding.plain.webserver.AndroidWebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Foreground service (type `mediaProjection`) that owns the MediaProjection + [ScreenMirrorEncoder]
 * and pushes encoded H.264 frames to WS clients as `SCREEN_MIRROR_VIDEO` events (spec §8).
 */
class ScreenMirrorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val frames = Channel<ByteArray>(capacity = 64)
    private var projection: MediaProjection? = null
    private var encoder: ScreenMirrorEncoder? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data: Intent? = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        val quality = intent?.getStringExtra(EXTRA_QUALITY) ?: "720p"
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data, quality)
        return START_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent, quality: String) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mpm.getMediaProjection(resultCode, data) ?: run { stopSelf(); return }
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() = stopSelf()
        }, null)
        projection = proj

        val metrics = displayMetrics()
        val targetHeight = if (quality == "1080p") 1080 else 720
        val scale = targetHeight.toFloat() / metrics.heightPixels.coerceAtLeast(1)
        val width = ((metrics.widthPixels * scale).toInt()).and(1.inv()).coerceAtLeast(2)   // even
        val height = targetHeight.and(1.inv())
        val bitrate = if (quality == "1080p") 6_000_000 else 3_000_000

        // Consumer: broadcast frames to WS clients (kept off the encoder drain thread).
        scope.launch {
            for (frame in frames) {
                runCatching { AndroidWebServer.wsHub.broadcast(WebEventType.SCREEN_MIRROR_VIDEO, frame) }
            }
        }
        encoder = ScreenMirrorEncoder { bytes, _ -> frames.trySend(bytes) }
            .also { it.start(proj, width, height, metrics.densityDpi, bitrate, 30) }

        scope.launch { runCatching { AndroidWebServer.wsHub.broadcast(WebEventType.SCREEN_MIRRORING, ByteArray(0)) } }
    }

    override fun onDestroy() {
        encoder?.stop()
        encoder = null
        runCatching { projection?.stop() }
        projection = null
        frames.close()
        ScreenMirror.state.value = "idle"
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun displayMetrics(): DisplayMetrics {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
    }

    private fun notification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Screen mirror", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("MWI screen mirror")
            .setContentText("Streaming your screen to the web console")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "mwi_screen_mirror"
        private const val NOTIFICATION_ID = 1002
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val EXTRA_QUALITY = "quality"

        fun start(context: Context, resultCode: Int, data: Intent, quality: String) {
            val intent = Intent(context, ScreenMirrorService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_QUALITY, quality)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenMirrorService::class.java))
        }
    }
}
