package com.ismartcoding.plain.features.call

/** Maps the CallLog `type` column to a stable string (values match `CallLog.Calls.*_TYPE`). */
object CallFormat {
    fun typeName(type: Int): String = when (type) {
        1 -> "incoming"
        2 -> "outgoing"
        3 -> "missed"
        4 -> "voicemail"
        5 -> "rejected"
        6 -> "blocked"
        7 -> "answered_externally"
        else -> "unknown"
    }
}
