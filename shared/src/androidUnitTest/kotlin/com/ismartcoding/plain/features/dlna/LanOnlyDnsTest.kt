package com.ismartcoding.plain.features.dlna

import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The DLNA outbound guard must only ever connect to LAN IPv4 addresses. IP-literal hostnames resolve
 * without a network round trip, so these run offline.
 */
class LanOnlyDnsTest {

    @Test
    fun allows_lan_ipv4_literals() {
        for (ip in listOf("10.0.0.5", "192.168.1.10", "172.16.0.1", "172.31.255.254", "100.64.0.1")) {
            assertEquals(ip, LanOnlyDns.lookup(ip).single().hostAddress, "$ip should resolve to itself")
        }
    }

    @Test
    fun blocks_loopback_linklocal_metadata_and_public() {
        for (ip in listOf("127.0.0.1", "0.0.0.0", "169.254.169.254", "169.254.1.2",
                "8.8.8.8", "1.1.1.1", "172.32.0.1", "192.169.0.1", "100.128.0.1")) {
            assertFailsWith<UnknownHostException>("$ip must be blocked by the SSRF policy") { LanOnlyDns.lookup(ip) }
        }
    }

    @Test
    fun blocks_all_ipv6_literals() {
        for (ip in listOf("::1", "fe80::1", "2001:4860:4860::8888")) {
            assertFailsWith<UnknownHostException>("$ip must be blocked (IPv6 is out of policy)") { LanOnlyDns.lookup(ip) }
        }
    }
}
