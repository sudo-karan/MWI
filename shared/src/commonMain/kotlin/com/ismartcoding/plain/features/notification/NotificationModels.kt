package com.ismartcoding.plain.features.notification

import kotlinx.serialization.Serializable

/** A mirrored notification (spec §6 `notifications`). */
@Serializable
data class DNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val subText: String,
    val postTime: Long,
    val canReply: Boolean,
    val clearable: Boolean,
)
