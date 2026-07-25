package com.ismartcoding.plain.features.contact

/** Phone-number normalization for dedup/compare (keeps digits, plus a single leading `+`). */
object PhoneNumbers {
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        var first = true
        for (c in raw) {
            when {
                c == '+' && first -> sb.append('+')
                c in '0'..'9' -> sb.append(c)
                // ignore spaces, dashes, parens, and any stray '+' after the first char
            }
            first = false
        }
        return sb.toString()
    }
}
