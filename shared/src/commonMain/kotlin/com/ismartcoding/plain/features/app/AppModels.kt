package com.ismartcoding.plain.features.app

import kotlinx.serialization.Serializable

/** An installed package (spec §6 `packages`, `app`). */
@Serializable
data class DPackage(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val system: Boolean,
    val enabled: Boolean,
    val firstInstall: Long,
    val lastUpdate: Long,
)
