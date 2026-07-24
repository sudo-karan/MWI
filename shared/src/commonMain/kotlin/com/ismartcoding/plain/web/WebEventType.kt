package com.ismartcoding.plain.web

/**
 * WebSocket event types (spec §6). Each event on the wire is `[4-byte big-endian type] +
 * XChaCha20(token, payload)`. Codes are MWI's own protocol contract; keep them stable — the web
 * client switches on them.
 */
enum class WebEventType(val code: Int) {
    MESSAGE_CREATED(100),
    MESSAGE_UPDATED(101),
    MESSAGE_DELETED(102),
    FEEDS_FETCHED(200),
    SCREEN_MIRRORING(300),
    SCREEN_MIRROR_VIDEO(301),
    SCREEN_MIRROR_VIDEO_CODEC(302),
    SCREEN_MIRROR_AUDIO(303),
    SCREEN_MIRROR_AUDIO_GRANTED(304),
    NOTIFICATION(400),
    NOTIFICATION_DELETED(401),
    POMODORO_UPDATED(500),
    BOOKMARK_UPDATED(600),
    DOWNLOAD_PROGRESS(700),
    MMS_SENT(800),
    CHANNELS_UPDATED(900),
    IMAGE_SEARCH_UPDATED(1000),
    PEER_STATUS_UPDATED(1100),
    DEVICE_NAME_UPDATED(1200),
    PAIRING_REQUESTED(1300),
    PAIRING_RESPONDED(1301),
    NEARBY_DEVICE_FOUND(1400),
    NEARBY_DEVICE_LOST(1401),
    CALL_STATE_CHANGED(1500),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): WebEventType? = byCode[code]
    }
}
