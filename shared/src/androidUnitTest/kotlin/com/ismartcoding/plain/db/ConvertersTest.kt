package com.ismartcoding.plain.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Directly exercises the Room [Converters] serialization (the risky part of the DB layer — sealed
 * polymorphism + lists) without needing a live database.
 */
class ConvertersTest {
    private val c = Converters()

    @Test
    fun chatContent_text_roundTrips() {
        val text: ChatContent = ChatContent.Text("hello|world")
        val back = c.toChatContent(c.fromChatContent(text))
        assertTrue(back is ChatContent.Text)
        assertEquals("hello|world", (back as ChatContent.Text).text)
    }

    @Test
    fun chatContent_files_roundTrips() {
        val files: ChatContent = ChatContent.Files(listOf(DFileItem("content://x", 10, "f.txt", 0)))
        val back = c.toChatContent(c.fromChatContent(files))
        assertTrue(back is ChatContent.Files)
        assertEquals("f.txt", (back as ChatContent.Files).items.single().fileName)
    }

    @Test
    fun channelMembers_roundTrip() {
        val members = listOf(DChannelMember("m1", "Alice", "pk1"), DChannelMember("m2", "Bob", ""))
        assertEquals(members, c.toMembers(c.fromMembers(members)))
    }

    @Test
    fun stringList_roundTrip() {
        val list = listOf("a", "b|c", "d")
        assertEquals(list, c.toStringList(c.fromStringList(list)))
    }
}
