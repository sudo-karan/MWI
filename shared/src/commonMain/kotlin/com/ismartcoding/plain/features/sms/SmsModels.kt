package com.ismartcoding.plain.features.sms

import kotlinx.serialization.Serializable

/** A single SMS message (spec §6 `sms`). */
@Serializable
data class DSms(
    val id: String,
    val threadId: String,
    val address: String,
    val body: String,
    val date: Long,
    val type: String,       // inbox / sent / draft / outbox / failed / queued
    val read: Boolean,
    val subId: Int = -1,
)

/** An SMS conversation/thread summary (spec §6 `smsConversations`). */
@Serializable
data class SmsConversation(
    val threadId: String,
    val address: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int,
    val unreadCount: Int,
)

/** A SIM/subscription (spec §6 `sims`). */
@Serializable
data class SimInfo(
    val subId: Int,
    val slotIndex: Int,
    val carrierName: String,
    val displayName: String,
    val number: String,
)
