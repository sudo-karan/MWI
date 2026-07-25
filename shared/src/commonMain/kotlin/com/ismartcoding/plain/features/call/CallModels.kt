package com.ismartcoding.plain.features.call

import kotlinx.serialization.Serializable

/** A call-log entry (spec §6 `calls`). */
@Serializable
data class DCall(
    val id: String,
    val number: String,
    val name: String,
    val date: Long,
    val duration: Long,
    val type: String,   // incoming / outgoing / missed / voicemail / rejected / blocked / ...
)

/** Current call/audio state (spec §6 `callState`, `callSpeakerOn`). */
@Serializable
data class CallState(
    val state: String,      // idle / ringing / offhook
    val speakerOn: Boolean,
)
