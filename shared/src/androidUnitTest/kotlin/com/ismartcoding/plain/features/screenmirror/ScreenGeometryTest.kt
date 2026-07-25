package com.ismartcoding.plain.features.screenmirror

import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenGeometryTest {
    @Test
    fun toPixel_scalesAndClamps() {
        assertEquals(0, ScreenGeometry.toPixel(0f, 1080))
        assertEquals(540, ScreenGeometry.toPixel(0.5f, 1080))
        assertEquals(1079, ScreenGeometry.toPixel(1f, 1080))     // last pixel, not out of bounds
        assertEquals(1079, ScreenGeometry.toPixel(2f, 1080))     // over-range clamps
        assertEquals(0, ScreenGeometry.toPixel(-1f, 1080))       // under-range clamps
        assertEquals(0, ScreenGeometry.toPixel(0.5f, 0))         // zero size is safe
    }
}
