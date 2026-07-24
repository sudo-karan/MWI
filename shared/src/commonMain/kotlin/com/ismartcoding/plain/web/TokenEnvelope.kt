package com.ismartcoding.plain.web

/**
 * Token-mode request envelope (spec §5): a decrypted body is `TIMESTAMP|NONCE|{json}`. The JSON may
 * itself contain `|`, so only the first two separators are significant.
 */
object TokenEnvelope {

    data class Parsed(val timestampMs: Long, val nonce: String, val json: String)

    fun format(timestampMs: Long, nonce: String, json: String): String = "$timestampMs|$nonce|$json"

    fun parse(raw: String): Parsed? {
        val i1 = raw.indexOf('|')
        if (i1 <= 0) return null
        val i2 = raw.indexOf('|', i1 + 1)
        if (i2 < 0) return null
        val ts = raw.substring(0, i1).toLongOrNull() ?: return null
        val nonce = raw.substring(i1 + 1, i2)
        if (nonce.isEmpty()) return null
        val json = raw.substring(i2 + 1)
        return Parsed(ts, nonce, json)
    }
}
