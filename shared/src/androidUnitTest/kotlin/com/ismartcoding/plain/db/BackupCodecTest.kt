package com.ismartcoding.plain.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the Backup/Restore serialization is lossless — especially the polymorphic [ChatContent],
 * which is the only non-trivial field — and lenient about unknown/missing keys so backup files stay
 * forward/backward compatible.
 */
class BackupCodecTest {

    @Test
    fun roundTrip_preservesAllTablesAndPolymorphicChatContent() {
        val data = BackupData(
            version = 1,
            exportedAt = 123456789L,
            notes = listOf(DNote(id = "n1", title = "Title", content = "body", createdAt = 1, updatedAt = 2)),
            bookmarkGroups = listOf(DBookmarkGroup(id = "g1", name = "Group", createdAt = 1, updatedAt = 2)),
            bookmarks = listOf(DBookmark(id = "b1", groupId = "g1", title = "site", url = "https://example.com", createdAt = 1, updatedAt = 2)),
            feeds = listOf(DFeed(id = "f1", url = "https://example.com/rss", name = "Feed", fetchContent = true, createdAt = 1, updatedAt = 2)),
            chatChannels = listOf(DChatChannel(id = "c1", name = "channel", createdAt = 1, updatedAt = 2)),
            chats = listOf(
                DChat(id = "m1", channelId = "c1", isMe = true, content = ChatContent.Text("hello"), createdAt = 1, updatedAt = 2),
                DChat(id = "m2", channelId = "c1", isMe = false, content = ChatContent.Images(listOf(DFileItem(uri = "u", size = 3, fileName = "a.png"))), createdAt = 3, updatedAt = 4),
            ),
            pomodoros = listOf(DPomodoroItem(id = "p1", durationSeconds = 1500, kind = 0, startedAt = 1, completedAt = 2, createdAt = 3)),
        )

        val back = BackupCodec.decode(BackupCodec.encode(data))

        assertEquals(data, back)
        assertTrue(back.chats[0].content is ChatContent.Text)
        assertTrue(back.chats[1].content is ChatContent.Images)
    }

    @Test
    fun decode_ignoresUnknownFieldsAndDefaultsMissingTables() {
        val json = """{"version":1,"exportedAt":9,"notes":[{"id":"n","title":"t","content":"c","createdAt":1,"updatedAt":2,"futureField":"x"}]}"""
        val back = BackupCodec.decode(json)
        assertEquals(1, back.notes.size)
        assertEquals("n", back.notes[0].id)
        assertTrue(back.bookmarks.isEmpty())
        assertTrue(back.chats.isEmpty())
    }
}
