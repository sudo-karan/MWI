package com.ismartcoding.plain.features.call

import kotlin.test.Test
import kotlin.test.assertEquals

class CallFormatTest {
    @Test
    fun typeName_mapsKnownTypes() {
        assertEquals("incoming", CallFormat.typeName(1))
        assertEquals("outgoing", CallFormat.typeName(2))
        assertEquals("missed", CallFormat.typeName(3))
        assertEquals("voicemail", CallFormat.typeName(4))
        assertEquals("rejected", CallFormat.typeName(5))
        assertEquals("blocked", CallFormat.typeName(6))
        assertEquals("answered_externally", CallFormat.typeName(7))
        assertEquals("unknown", CallFormat.typeName(0))
    }
}
