package com.ismartcoding.plain.web.security

/**
 * SSRF guard for the outbound proxy (`/proxyfs`, spec §5). The proxy must only ever reach hosts on
 * the user's private network — never loopback, link-local, cloud metadata, or public addresses.
 *
 * Policy:
 *  - Strip any `user@` userinfo and IPv6 `%zone` id before evaluating (defense against parser
 *    confusion).
 *  - Block loopback, link-local (169.254/16, fe80::/10), the cloud metadata IPs, unspecified, and
 *    multicast.
 *  - Allow only RFC1918 IPv4 (10/8, 172.16/12, 192.168/16) plus the CGNAT range 100.64/10 that some
 *    LANs use. Public IPv4 and all other hosts are blocked.
 */
object SsrfGuard {

    private val METADATA_IPS = setOf("169.254.169.254", "100.100.100.200", "fd00:ec2::254")

    /** Remove `user[:pass]@` and any IPv6 zone id `%eth0`. Lower-cased. */
    fun sanitizeHost(host: String): String {
        var h = host.trim().lowercase()
        val at = h.lastIndexOf('@')
        if (at >= 0) h = h.substring(at + 1)
        // Strip brackets from IPv6 literals and any zone id.
        h = h.removePrefix("[").removeSuffix("]")
        val pct = h.indexOf('%')
        if (pct >= 0) h = h.substring(0, pct)
        return h
    }

    /** True if the host must NOT be reached by the proxy. */
    fun isBlocked(host: String): Boolean {
        val h = sanitizeHost(host)
        if (h.isEmpty()) return true
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (h in METADATA_IPS) return true

        val v4 = parseIpv4(h)
        if (v4 != null) return !isAllowedIpv4(v4)

        // Any IPv6 literal: block loopback/link-local/unspecified; otherwise conservatively block
        // (the proxy target policy is IPv4-RFC1918-only).
        if (h.contains(':')) {
            if (h == "::1" || h == "::" ) return true
            if (h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd")) return true
            return true
        }

        // A DNS name (not an IP literal) — cannot be classified here without resolution; block by
        // default. The caller resolves and re-checks the resolved IP.
        return true
    }

    /** True if the proxy is permitted to reach [host]. */
    fun isAllowed(host: String): Boolean = !isBlocked(host)

    /** RFC1918 + CGNAT, excluding loopback/link-local/broadcast/multicast/metadata. */
    fun isAllowedIpv4(octets: IntArray): Boolean {
        val (a, b, c, d) = arrayOf(octets[0], octets[1], octets[2], octets[3])
        // Loopback 127/8, unspecified 0/8, link-local 169.254/16, multicast 224/4, reserved 240/4.
        if (a == 127 || a == 0) return false
        if (a == 169 && b == 254) return false
        if (a in 224..255) return false
        if (a == 255 && b == 255 && c == 255 && d == 255) return false
        // Allowed private ranges.
        val rfc1918 = (a == 10) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
        val cgnat = (a == 100 && b in 64..127)
        return rfc1918 || cgnat
    }

    /** Parse a dotted-quad IPv4 literal, or null if [s] is not one. */
    fun parseIpv4(s: String): IntArray? {
        val parts = s.split('.')
        if (parts.size != 4) return null
        val out = IntArray(4)
        for (i in 0 until 4) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all { it in '0'..'9' }) return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v
        }
        return out
    }
}
