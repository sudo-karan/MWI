package com.ismartcoding.plain.web.media

/** Defensive pagination clamping for media queries (bounds an untrusted `limit`/`offset`). */
object MediaQuery {
    const val DEFAULT_LIMIT = 50
    const val MAX_LIMIT = 1000

    fun clampLimit(requested: Int?): Int = when {
        requested == null || requested <= 0 -> DEFAULT_LIMIT
        requested > MAX_LIMIT -> MAX_LIMIT
        else -> requested
    }

    fun clampOffset(requested: Int?): Int = if (requested == null || requested < 0) 0 else requested
}
