package com.ismartcoding.plain.features.backup

import com.ismartcoding.plain.db.AppDb
import com.ismartcoding.plain.db.BackupData
import com.ismartcoding.plain.platform.epochMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gathers and restores the user-authored Room tables for the web console's Backup/Restore (spec
 * §12). Restore is additive (upsert-by-id), so importing a backup merges into — never wipes — the
 * current data; re-importing the same file is idempotent.
 */
object BackupService {
    private val db get() = AppDb.instance
    private const val CAP = 100_000

    suspend fun export(): BackupData = withContext(Dispatchers.IO) {
        val channels = db.chatChannelDao().getAll()
        BackupData(
            version = 1,
            exportedAt = epochMillis(),
            notes = db.noteDao().page(CAP, 0),
            bookmarkGroups = db.bookmarkGroupDao().getAll(),
            bookmarks = db.bookmarkDao().getAll(),
            feeds = db.feedDao().getAll(),
            chatChannels = channels,
            chats = channels.flatMap { db.chatDao().getByChannel(it.id, CAP, 0) },
            pomodoros = db.pomodoroDao().recent(CAP),
        )
    }

    /** Upserts every record in [data]; returns the number of rows written. */
    suspend fun import(data: BackupData): Int = withContext(Dispatchers.IO) {
        var n = 0
        data.bookmarkGroups.forEach { db.bookmarkGroupDao().upsert(it); n++ }
        data.notes.forEach { db.noteDao().upsert(it); n++ }
        data.bookmarks.forEach { db.bookmarkDao().upsert(it); n++ }
        data.feeds.forEach { db.feedDao().upsert(it); n++ }
        data.chatChannels.forEach { db.chatChannelDao().upsert(it); n++ }
        data.chats.forEach { db.chatDao().upsert(it); n++ }
        data.pomodoros.forEach { db.pomodoroDao().upsert(it); n++ }
        n
    }
}
