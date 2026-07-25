package com.ismartcoding.plain.features.screenmirror

import android.content.Intent
import com.ismartcoding.plain.platform.AndroidApp
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Screen-mirror coordinator (spec §8). Because MediaProjection requires on-device user consent, a
 * web `startScreenMirror` only *requests* it: [pendingStart] is observed by `MainActivity`, which
 * shows the system consent dialog; on approval the capture service starts.
 */
object ScreenMirror {
    val state = MutableStateFlow("idle")            // idle / requesting / running
    val quality = MutableStateFlow("720p")          // 720p / 1080p
    val codec = MutableStateFlow("h264")
    val controlEnabled = MutableStateFlow(false)    // true when the AccessibilityService is bound

    /** MainActivity observes this to launch the capture-permission dialog. */
    val pendingStart = MutableStateFlow(false)

    fun info(): ScreenMirrorInfo =
        ScreenMirrorInfo(state.value, quality.value, codec.value, controlEnabled.value)

    fun requestStart() {
        if (state.value == "running") return
        state.value = "requesting"
        pendingStart.value = true
    }

    fun onCaptureGranted(resultCode: Int, data: Intent) {
        ScreenMirrorService.start(AndroidApp.context, resultCode, data, quality.value)
        state.value = "running"
    }

    fun onCaptureDenied() {
        state.value = "idle"
    }

    fun stop() {
        ScreenMirrorService.stop(AndroidApp.context)
        state.value = "idle"
    }

    fun updateQuality(q: String) {
        quality.value = if (q == "1080p") "1080p" else "720p"
    }

    fun control(c: ScreenMirrorControl): Boolean = MwiAccessibilityService.dispatch(c)

    /** Target capture height for the current quality (width derived from the display aspect). */
    fun targetHeight(): Int = if (quality.value == "1080p") 1080 else 720
}
