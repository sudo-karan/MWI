package com.ismartcoding.plain.features.nearby

import kotlinx.serialization.Serializable

/** A device discovered on the LAN via mDNS/DNS-SD (spec §7 Nearby devices). */
@Serializable
data class NearbyDevice(
    val name: String,
    val host: String = "",
    val port: Int = 0,
    val resolved: Boolean = false,
)
