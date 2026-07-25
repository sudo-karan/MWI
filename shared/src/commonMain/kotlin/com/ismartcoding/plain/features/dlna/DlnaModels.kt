package com.ismartcoding.plain.features.dlna

import kotlinx.serialization.Serializable

/** A DLNA/UPnP MediaRenderer discovered on the LAN (spec §7 TV Cast). */
@Serializable
data class DlnaRenderer(
    val udn: String,
    val name: String,
    val location: String,
    val controlUrl: String,
)
