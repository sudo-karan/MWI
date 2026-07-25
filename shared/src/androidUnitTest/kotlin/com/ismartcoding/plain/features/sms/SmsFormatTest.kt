package com.ismartcoding.plain.features.sms

import kotlin.test.Test
import kotlin.test.assertEquals

class SmsFormatTest {
    @Test
    fun typeName_mapsKnownTypes() {
        assertEquals("inbox", SmsFormat.typeName(1))
        assertEquals("sent", SmsFormat.typeName(2))
        assertEquals("draft", SmsFormat.typeName(3))
        assertEquals("outbox", SmsFormat.typeName(4))
        assertEquals("failed", SmsFormat.typeName(5))
        assertEquals("queued", SmsFormat.typeName(6))
        assertEquals("unknown", SmsFormat.typeName(0))
        assertEquals("unknown", SmsFormat.typeName(99))
    }
}
