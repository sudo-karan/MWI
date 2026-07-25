package com.ismartcoding.plain.features.dlna

import com.ismartcoding.plain.web.security.SsrfGuard
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * An OkHttp [Dns] that only resolves to LAN (RFC1918 / CGNAT) IPv4 addresses, blocking loopback,
 * link-local, cloud-metadata, and public targets via [SsrfGuard]. All IPv6 is blocked (the DLNA
 * policy is IPv4-LAN-only).
 *
 * DLNA control URLs and SSDP `LOCATION` values are attacker/user-influenced strings that the phone
 * would otherwise fetch verbatim (a blind-SSRF pivot). Validating here — where OkHttp connects to
 * exactly the addresses returned — pins the connection to a checked IP and closes the DNS-rebinding
 * TOCTOU for every outbound DLNA request.
 */
object LanOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val resolved = try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: UnknownHostException) {
            throw e
        }
        val safe = resolved.filter { addr ->
            val ip = addr.hostAddress ?: return@filter false
            val v4 = SsrfGuard.parseIpv4(ip)
            v4 != null && SsrfGuard.isAllowedIpv4(v4)
        }
        if (safe.isEmpty()) throw UnknownHostException("blocked by SSRF policy: $hostname")
        return safe
    }
}
