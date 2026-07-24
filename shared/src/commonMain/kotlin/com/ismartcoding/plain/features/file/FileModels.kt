package com.ismartcoding.plain.features.file

import kotlinx.serialization.Serializable

/** A storage root the browser can browse (spec §6 `mounts`). */
@Serializable
data class Mount(
    val name: String,
    val path: String,
    val totalBytes: Long,
    val availableBytes: Long,
    val removable: Boolean,
)

/** A file or directory entry (spec §6 `files`, `fileInfo`). */
@Serializable
data class DFile(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val updatedAt: Long,
    val childCount: Int = 0,
)
