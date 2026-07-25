package com.ismartcoding.plain.ui.screens

/** Human-readable byte size, shared across the standalone screens. */
internal fun fmtBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return "${(value * 10).toLong() / 10.0} ${units[i]}"
}
