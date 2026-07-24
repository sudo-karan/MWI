package com.ismartcoding.plain.features.media

import kotlinx.serialization.Serializable

/** The three media kinds the dashboard browses (spec §6). */
enum class MediaType { IMAGE, VIDEO, AUDIO }

/**
 * A single media item. Shared across images/videos/audios; type-specific fields (`duration`,
 * `width`/`height`) are 0 when not applicable. `path` feeds `/fs` for streaming/thumbnails.
 */
@Serializable
data class DMediaItem(
    val id: String,
    val path: String,
    val title: String,
    val mimeType: String,
    val size: Long,
    val dateModified: Long,
    val duration: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bucketId: String = "",
    val bucketName: String = "",
)

/** A media "bucket" (album / folder), spec §6 `mediaBuckets`. */
@Serializable
data class MediaBucket(
    val id: String,
    val name: String,
    val itemCount: Int,
    val coverPath: String,
)
