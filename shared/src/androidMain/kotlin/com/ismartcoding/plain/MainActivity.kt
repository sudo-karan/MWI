package com.ismartcoding.plain

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ismartcoding.plain.features.screenmirror.ScreenMirror
import com.ismartcoding.plain.ui.App
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The single Activity host. All UI is Compose (`App()` lives in commonMain), so this class stays
 * thin. It also brokers the one thing the web can't do on its own: the MediaProjection consent
 * dialog for screen mirroring (spec §8) — requested via [ScreenMirror.pendingStart].
 */
class MainActivity : ComponentActivity() {

    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                ScreenMirror.onCaptureGranted(result.resultCode, data)
            } else {
                ScreenMirror.onCaptureDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }

        lifecycleScope.launch {
            ScreenMirror.pendingStart.collectLatest { pending ->
                if (pending) {
                    ScreenMirror.pendingStart.value = false
                    launchCapturePermission()
                }
            }
        }
    }

    private fun launchCapturePermission() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching { captureLauncher.launch(mpm.createScreenCaptureIntent()) }
            .onFailure { ScreenMirror.onCaptureDenied() }
    }
}
