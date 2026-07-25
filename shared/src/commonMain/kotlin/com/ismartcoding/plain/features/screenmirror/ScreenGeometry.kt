package com.ismartcoding.plain.features.screenmirror

/** Maps normalized [0,1] control coordinates to on-screen pixels (spec §8). */
object ScreenGeometry {
    /** Clamp [norm] to [0,1] and scale to [sizePx], rounding to the nearest pixel. */
    fun toPixel(norm: Float, sizePx: Int): Int {
        val clamped = when {
            norm < 0f -> 0f
            norm > 1f -> 1f
            else -> norm
        }
        return (clamped * sizePx).toInt().coerceIn(0, if (sizePx > 0) sizePx - 1 else 0)
    }
}
