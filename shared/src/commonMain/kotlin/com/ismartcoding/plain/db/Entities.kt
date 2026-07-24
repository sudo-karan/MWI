package com.ismartcoding.plain.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ismartcoding.plain.platform.newId
import kotlinx.serialization.Serializable

/*
 * The full at-rest data model (spec §10). Every row entity is also `@Serializable` so it can be
 * projected straight into GraphQL/WS payloads. Timestamps are epoch-millis Longs. IDs are UUID
 * strings. A fresh install starts this schema at version 1 (the rebrand begins its own migration
 * history; see AppDatabase).
 */

// ------------------------------------------------------------------- Auth / server

/** An authenticated browser session (spec §5). Denylisted from the DB browser. */
@Serializable
@Entity(tableName = "sessions")
data class DSession(
    @PrimaryKey val id: String = newId(),
    val clientId: String = "",
    val name: String = "",
    val token: String = "",
    val tokenType: String = "session",
    val osName: String = "",
    val browserName: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

// ------------------------------------------------------------------------- Tags

@Serializable
@Entity(tableName = "tags")
data class DTag(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val type: Int = 0,
    val count: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "tag_relations")
data class DTagRelation(
    @PrimaryKey val id: String = newId(),
    val tagId: String = "",
    val key: String = "",
    val type: Int = 0,
)

// ------------------------------------------------------------------------- Notes

@Serializable
@Entity(tableName = "notes")
data class DNote(
    @PrimaryKey val id: String = newId(),
    val title: String = "",
    val content: String = "",
    val deletedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

// -------------------------------------------------------------------------- Feeds

@Serializable
@Entity(tableName = "feeds")
data class DFeed(
    @PrimaryKey val id: String = newId(),
    val url: String = "",
    val name: String = "",
    val fetchContent: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "feed_entries")
data class DFeedEntry(
    @PrimaryKey val id: String = newId(),
    val feedId: String = "",
    val title: String = "",
    val url: String = "",
    val author: String = "",
    val image: String = "",
    val description: String = "",
    val content: String = "",
    val rawId: String = "",
    val read: Boolean = false,
    val publishedAt: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

// -------------------------------------------------------------------------- Books

@Serializable
@Entity(tableName = "books")
data class DBook(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val author: String = "",
    val path: String = "",
    val cover: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "book_chapters")
data class DBookChapter(
    @PrimaryKey val id: String = newId(),
    val bookId: String = "",
    val name: String = "",
    val index: Int = 0,
    val start: Long = 0,
    val end: Long = 0,
)

// ----------------------------------------------------------------------- Pomodoro

@Serializable
@Entity(tableName = "pomodoro_items")
data class DPomodoroItem(
    @PrimaryKey val id: String = newId(),
    val durationSeconds: Int = 0,
    val kind: Int = 0,          // 0 = focus, 1 = short break, 2 = long break
    val startedAt: Long = 0,
    val completedAt: Long? = null,
    val createdAt: Long = 0,
)

// ---------------------------------------------------------------------- Bookmarks

@Serializable
@Entity(tableName = "bookmark_groups")
data class DBookmarkGroup(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
@Entity(tableName = "bookmarks")
data class DBookmark(
    @PrimaryKey val id: String = newId(),
    val groupId: String = "",
    val title: String = "",
    val url: String = "",
    val icon: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

// ------------------------------------------------------------------------ AppFiles

@Serializable
@Entity(tableName = "app_files")
data class DAppFile(
    @PrimaryKey val id: String = newId(),
    val path: String = "",
    val size: Long = 0,
    val createdAt: Long = 0,
)

// ------------------------------------------------------- AI image search (embeddings)

@Serializable
@Entity(tableName = "image_embeddings")
data class DImageEmbedding(
    @PrimaryKey val id: String = newId(),
    val mediaId: String = "",
    val path: String = "",
    /** CLIP embedding stored as a raw little-endian float BLOB. */
    val embedding: ByteArray = ByteArray(0),
    val createdAt: Long = 0,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is DImageEmbedding && id == other.id)

    override fun hashCode(): Int = id.hashCode()
}

// ---------------------------------------------------------- SMS archived conversations

@Serializable
@Entity(tableName = "archived_conversations")
data class DArchivedConversation(
    @PrimaryKey val id: String = "",   // thread id
    val createdAt: Long = 0,
)

// ------------------------------------------------------------------ Video progress

@Serializable
@Entity(tableName = "video_play_progress")
data class DVideoPlayProgress(
    @PrimaryKey val id: String = "",   // media id / path key
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
)
