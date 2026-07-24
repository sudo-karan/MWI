package com.ismartcoding.plain.web.media

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaQueryTest {

    @Test
    fun clampLimit_bounds() {
        assertEquals(MediaQuery.DEFAULT_LIMIT, MediaQuery.clampLimit(null))
        assertEquals(MediaQuery.DEFAULT_LIMIT, MediaQuery.clampLimit(0))
        assertEquals(MediaQuery.DEFAULT_LIMIT, MediaQuery.clampLimit(-5))
        assertEquals(10, MediaQuery.clampLimit(10))
        assertEquals(MediaQuery.MAX_LIMIT, MediaQuery.clampLimit(10_000))
    }

    @Test
    fun clampOffset_bounds() {
        assertEquals(0, MediaQuery.clampOffset(null))
        assertEquals(0, MediaQuery.clampOffset(-1))
        assertEquals(42, MediaQuery.clampOffset(42))
    }
}
