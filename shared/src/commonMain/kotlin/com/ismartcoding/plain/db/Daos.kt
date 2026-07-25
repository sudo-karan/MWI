package com.ismartcoding.plain.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DSession>

    @Query("SELECT * FROM sessions WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): DSession?

    @Query("SELECT * FROM sessions WHERE token = :token LIMIT 1")
    suspend fun getByToken(token: String): DSession?

    @Upsert
    suspend fun upsert(item: DSession)

    @Delete
    suspend fun delete(item: DSession)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE type = :type ORDER BY name")
    fun flowByType(type: Int): Flow<List<DTag>>

    @Query("SELECT * FROM tags WHERE type = :type ORDER BY name")
    suspend fun getByType(type: Int): List<DTag>

    @Upsert
    suspend fun upsert(item: DTag)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(item: DTag)
}

@Dao
interface TagRelationDao {
    @Query("SELECT * FROM tag_relations WHERE tagId = :tagId")
    suspend fun getByTag(tagId: String): List<DTagRelation>

    @Query("SELECT * FROM tag_relations WHERE key = :key AND type = :type")
    suspend fun getByKey(key: String, type: Int): List<DTagRelation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DTagRelation)

    @Query("DELETE FROM tag_relations WHERE tagId = :tagId AND key = :key")
    suspend fun delete(tagId: String, key: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun flowAll(): Flow<List<DNote>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<DNote>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DNote?

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: DNote)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(item: DNote)
}

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY createdAt")
    suspend fun getAll(): List<DFeed>

    @Query("SELECT * FROM feeds WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DFeed?

    @Query("SELECT COUNT(*) FROM feeds")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: DFeed)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(item: DFeed)
}

@Dao
interface FeedEntryDao {
    @Query("SELECT * FROM feed_entries WHERE feedId = :feedId ORDER BY publishedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByFeed(feedId: String, limit: Int, offset: Int): List<DFeedEntry>

    @Query("SELECT * FROM feed_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DFeedEntry?

    @Query("SELECT COUNT(*) FROM feed_entries WHERE feedId = :feedId")
    suspend fun countByFeed(feedId: String): Int

    @Upsert
    suspend fun upsert(items: List<DFeedEntry>)

    @Query("DELETE FROM feed_entries WHERE feedId = :feedId")
    suspend fun deleteByFeed(feedId: String)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DBook>

    @Upsert
    suspend fun upsert(item: DBook)

    @Delete
    suspend fun delete(item: DBook)
}

@Dao
interface BookChapterDao {
    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY `index`")
    suspend fun getByBook(bookId: String): List<DBookChapter>

    @Upsert
    suspend fun upsert(items: List<DBookChapter>)
}

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_items ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DPomodoroItem>

    @Upsert
    suspend fun upsert(item: DPomodoroItem)
}

@Dao
interface BookmarkGroupDao {
    @Query("SELECT * FROM bookmark_groups ORDER BY name")
    suspend fun getAll(): List<DBookmarkGroup>

    @Upsert
    suspend fun upsert(item: DBookmarkGroup)

    @Query("DELETE FROM bookmark_groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(item: DBookmarkGroup)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE groupId = :groupId ORDER BY createdAt DESC")
    suspend fun getByGroup(groupId: String): List<DBookmark>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<DBookmark>

    @Upsert
    suspend fun upsert(item: DBookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Delete
    suspend fun delete(item: DBookmark)
}

@Dao
interface AppFileDao {
    @Query("SELECT * FROM app_files ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<DAppFile>

    @Query("SELECT COUNT(*) FROM app_files")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: DAppFile)
}

@Dao
interface ImageEmbeddingDao {
    @Query("SELECT * FROM image_embeddings")
    suspend fun getAll(): List<DImageEmbedding>

    @Query("SELECT COUNT(*) FROM image_embeddings")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(item: DImageEmbedding)

    @Query("DELETE FROM image_embeddings")
    suspend fun clear()
}

@Dao
interface ArchivedConversationDao {
    @Query("SELECT * FROM archived_conversations")
    suspend fun getAll(): List<DArchivedConversation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DArchivedConversation)

    @Query("DELETE FROM archived_conversations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface VideoPlayProgressDao {
    @Query("SELECT * FROM video_play_progress WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DVideoPlayProgress?

    @Upsert
    suspend fun upsert(item: DVideoPlayProgress)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE channelId = :channelId ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getByChannel(channelId: String, limit: Int, offset: Int): List<DChat>

    @Query("SELECT * FROM chats WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DChat?

    @Upsert
    suspend fun upsert(item: DChat)

    @Update
    suspend fun update(item: DChat)

    @Delete
    suspend fun delete(item: DChat)
}

@Dao
interface ChatChannelDao {
    @Query("SELECT * FROM chat_channels ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DChatChannel>

    @Query("SELECT * FROM chat_channels WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DChatChannel?

    @Upsert
    suspend fun upsert(item: DChatChannel)

    @Delete
    suspend fun delete(item: DChatChannel)
}

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DPeer>

    @Query("SELECT * FROM peers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DPeer?

    @Upsert
    suspend fun upsert(item: DPeer)

    @Query("DELETE FROM peers WHERE id = :id")
    suspend fun deleteById(id: String)
}
