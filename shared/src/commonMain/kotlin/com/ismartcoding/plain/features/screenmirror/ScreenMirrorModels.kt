package com.ismartcoding.plain.features.screenmirror

import kotlinx.serialization.Serializable

/** Current screen-mirror state (spec §6). */
@Serializable
data class ScreenMirrorInfo(
    val state: String,          // idle / requesting / running
    val quality: String,        // 720p / 1080p
    val codec: String,          // h264
    val controlEnabled: Boolean,
)

/**
 * A remote-control gesture/key from the browser (spec §8). Coordinates are normalized [0,1] and
 * mapped to pixels on-device by the AccessibilityService.
 */
@Serializable
data class ScreenMirrorControl(
    val type: String,           // tap / longpress / swipe / scroll / key / text
    val x: Float = 0f,
    val y: Float = 0f,
    val x2: Float = 0f,
    val y2: Float = 0f,
    val durationMs: Long = 0,
    val key: String = "",       // back / home / recents / lock / enter
    val text: String = "",
)
