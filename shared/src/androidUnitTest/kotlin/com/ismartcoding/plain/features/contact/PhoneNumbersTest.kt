package com.ismartcoding.plain.features.contact

import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneNumbersTest {

    @Test
    fun normalize_keepsDigitsAndLeadingPlus() {
        assertEquals("+14155550123", PhoneNumbers.normalize("+1 (415) 555-0123"))
        assertEquals("5550123", PhoneNumbers.normalize("555-0123"))
        assertEquals("+441632960000", PhoneNumbers.normalize("+44 1632 960000"))
        // A stray '+' after the first character is dropped.
        assertEquals("001234", PhoneNumbers.normalize("00+12+34"))
        assertEquals("", PhoneNumbers.normalize("no digits here"))
    }
}
