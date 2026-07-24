package com.ismartcoding.plain.features.media

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.ismartcoding.plain.platform.AndroidApp

/**
 * Reads images/videos/audios from MediaStore (spec §6). Column reads are index-guarded so the same
 * code works across API 28–36, and pagination uses the query Bundle on API 30+ (where the old
 * `LIMIT`/`OFFSET`-in-sort-order trick is blocked) with a sort-order fallback below.
 */
object MediaProvider {

    private val resolver: ContentResolver get() = AndroidApp.context.contentResolver

    fun query(type: MediaType, offset: Int, limit: Int, bucketId: String?): List<DMediaItem> {
        val selection = bucketId?.let { "${MediaStore.MediaColumns.BUCKET_ID} = ?" }
        val args = bucketId?.let { arrayOf(it) }
        val out = ArrayList<DMediaItem>(limit.coerceAtMost(256))
        pagedQuery(uriFor(type), projection(type), selection, args, limit, offset)?.use { c ->
            val idx = ColumnIndex(c)
            while (c.moveToNext()) out.add(c.toItem(idx))
        }
        return out
    }

    fun count(type: MediaType, bucketId: String?): Int {
        val selection = bucketId?.let { "${MediaStore.MediaColumns.BUCKET_ID} = ?" }
        val args = bucketId?.let { arrayOf(it) }
        return resolver.query(uriFor(type), arrayOf(MediaStore.MediaColumns._ID), selection, args, null)
            ?.use { it.count } ?: 0
    }

    fun buckets(type: MediaType): List<MediaBucket> {
        val projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
        )
        val counts = LinkedHashMap<String, Int>()
        val names = HashMap<String, String>()
        val covers = HashMap<String, String>()
        resolver.query(uriFor(type), projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")
            ?.use { c ->
                val bId = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bName = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val data = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    val id = if (bId >= 0) c.getString(bId) ?: continue else continue
                    counts[id] = (counts[id] ?: 0) + 1
                    if (bName >= 0) names.getOrPut(id) { c.getString(bName) ?: "" }
                    if (data >= 0) covers.getOrPut(id) { c.getString(data) ?: "" }
                }
            }
        return counts.map { (id, n) -> MediaBucket(id, names[id] ?: "", n, covers[id] ?: "") }
    }

    // ---- internals ----

    private fun uriFor(type: MediaType): Uri = when (type) {
        MediaType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    }

    private fun projection(type: MediaType): Array<String> {
        val base = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        if (type != MediaType.IMAGE) base.add(MediaStore.MediaColumns.DURATION)
        if (type != MediaType.AUDIO) {
            base.add(MediaStore.MediaColumns.WIDTH)
            base.add(MediaStore.MediaColumns.HEIGHT)
        }
        return base.toTypedArray()
    }

    private fun pagedQuery(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        limit: Int,
        offset: Int,
    ): Cursor? {
        val sortCol = MediaStore.MediaColumns.DATE_MODIFIED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
                args?.let { putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortCol))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(uri, projection, bundle, null)
        } else {
            @Suppress("DEPRECATION")
            resolver.query(uri, projection, selection, args, "$sortCol DESC LIMIT $limit OFFSET $offset")
        }
    }

    private class ColumnIndex(c: Cursor) {
        val id = c.getColumnIndex(MediaStore.MediaColumns._ID)
        val data = c.getColumnIndex(MediaStore.MediaColumns.DATA)
        val name = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val date = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val duration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val width = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val height = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val bucketId = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        val bucketName = c.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
    }

    private fun Cursor.toItem(i: ColumnIndex): DMediaItem = DMediaItem(
        id = if (i.id >= 0) getLong(i.id).toString() else "",
        path = if (i.data >= 0) getString(i.data) ?: "" else "",
        title = if (i.name >= 0) getString(i.name) ?: "" else "",
        mimeType = if (i.mime >= 0) getString(i.mime) ?: "" else "",
        size = if (i.size >= 0) getLong(i.size) else 0,
        dateModified = if (i.date >= 0) getLong(i.date) * 1000 else 0, // MediaStore stores seconds
        duration = if (i.duration >= 0) getLong(i.duration) else 0,
        width = if (i.width >= 0) getInt(i.width) else 0,
        height = if (i.height >= 0) getInt(i.height) else 0,
        bucketId = if (i.bucketId >= 0) getString(i.bucketId) ?: "" else "",
        bucketName = if (i.bucketName >= 0) getString(i.bucketName) ?: "" else "",
    )
}
