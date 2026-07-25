package com.ismartcoding.plain.db

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A portable snapshot of the app's local, user-authored data (spec §12 backup/restore). Only the
 * data a user would want to carry between installs is included — notes, bookmarks, feeds,
 * pomodoros, and P2P chat. Device-derived content (media, contacts, SMS, calls, apps) lives on the
 * OS and is deliberately excluded; sessions/tokens are never exported.
 *
 * Every field defaults to empty so older/newer backup files decode leniently.
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = 0,
    val notes: List<DNote> = emptyList(),
    val bookmarkGroups: List<DBookmarkGroup> = emptyList(),
    val bookmarks: List<DBookmark> = emptyList(),
    val feeds: List<DFeed> = emptyList(),
    val chatChannels: List<DChatChannel> = emptyList(),
    val chats: List<DChat> = emptyList(),
    val pomodoros: List<DPomodoroItem> = emptyList(),
)

/** Serializes [BackupData] with the same `type`-discriminated polymorphism used for chat content. */
object BackupCodec {
    val json: Json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }
    fun encode(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)
    fun decode(text: String): BackupData = json.decodeFromString(BackupData.serializer(), text)
}
