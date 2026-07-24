package com.ismartcoding.plain.features.device

import kotlinx.serialization.Serializable

/** Device summary (spec §6 `deviceInfo`). */
@Serializable
data class DeviceInfo(
    val deviceName: String,
    val model: String,
    val manufacturer: String,
    val product: String,
    val device: String,
    val board: String,
    val osVersion: String,
    val sdkInt: Int,
    val abis: List<String>,
    val totalStorage: Long,
    val availableStorage: Long,
    val totalMemory: Long,
    val availableMemory: Long,
)

/** Battery snapshot (spec §6 `battery`). */
@Serializable
data class BatteryInfo(
    val level: Int,          // 0..100
    val charging: Boolean,
    val temperatureC: Float,
    val voltageMv: Int,
    val health: String,
    val technology: String,
)
