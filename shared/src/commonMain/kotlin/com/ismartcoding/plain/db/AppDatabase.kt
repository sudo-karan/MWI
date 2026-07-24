package com.ismartcoding.plain.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

/**
 * The single Room database (spec §10). A fresh MWI install starts this schema at version 1;
 * `exportSchema = true` (configured in the Gradle `room { schemaDirectory(...) }` block) records
 * each version so future changes ship as Room auto-migrations.
 */
@Database(
    entities = [
        DSession::class,
        DTag::class,
        DTagRelation::class,
        DNote::class,
        DFeed::class,
        DFeedEntry::class,
        DBook::class,
        DBookChapter::class,
        DPomodoroItem::class,
        DBookmarkGroup::class,
        DBookmark::class,
        DAppFile::class,
        DImageEmbedding::class,
        DArchivedConversation::class,
        DVideoPlayProgress::class,
        DChat::class,
        DChatChannel::class,
        DPeer::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun tagDao(): TagDao
    abstract fun tagRelationDao(): TagRelationDao
    abstract fun noteDao(): NoteDao
    abstract fun feedDao(): FeedDao
    abstract fun feedEntryDao(): FeedEntryDao
    abstract fun bookDao(): BookDao
    abstract fun bookChapterDao(): BookChapterDao
    abstract fun pomodoroDao(): PomodoroDao
    abstract fun bookmarkGroupDao(): BookmarkGroupDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun appFileDao(): AppFileDao
    abstract fun imageEmbeddingDao(): ImageEmbeddingDao
    abstract fun archivedConversationDao(): ArchivedConversationDao
    abstract fun videoPlayProgressDao(): VideoPlayProgressDao
    abstract fun chatDao(): ChatDao
    abstract fun chatChannelDao(): ChatChannelDao
    abstract fun peerDao(): PeerDao
}

/**
 * KSP generates the `actual` for each platform. Marked internal-by-convention; call
 * [buildAppDatabase] rather than constructing directly.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Build the configured [AppDatabase] for the current platform. */
expect fun buildAppDatabase(): AppDatabase
