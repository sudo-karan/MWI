package com.ismartcoding.plain.features.sms

/**
 * Maps the Telephony SMS `type` column to a stable string. The numeric values match
 * `Telephony.Sms.MESSAGE_TYPE_*`; kept here (not referencing android) so it's unit-testable.
 */
object SmsFormat {
    fun typeName(type: Int): String = when (type) {
        1 -> "inbox"
        2 -> "sent"
        3 -> "draft"
        4 -> "outbox"
        5 -> "failed"
        6 -> "queued"
        else -> "unknown"
    }
}
