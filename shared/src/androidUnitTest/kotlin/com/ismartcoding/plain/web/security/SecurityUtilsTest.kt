package com.ismartcoding.plain.web.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RateLimiterTest {

    @Test
    fun perSource_capIsFivePerMinute() {
        var now = 0L
        val rl = RateLimiter(perSourcePerMinute = 5, globalPerMinute = 100, now = { now })
        repeat(5) { assertTrue(rl.tryAcquire("1.2.3.4"), "attempt $it should pass") }
        assertFalse(rl.tryAcquire("1.2.3.4"), "6th attempt must be throttled")
        // A different source is unaffected.
        assertTrue(rl.tryAcquire("5.6.7.8"))
    }

    @Test
    fun global_capIsTwentyPerMinute() {
        var now = 0L
        val rl = RateLimiter(perSourcePerMinute = 100, globalPerMinute = 20, now = { now })
        var admitted = 0
        // 25 distinct sources, one attempt each — global cap should stop at 20.
        for (i in 0 until 25) if (rl.tryAcquire("src-$i")) admitted++
        assertEquals(20, admitted)
    }

    @Test
    fun window_slides() {
        var now = 1_000_000L
        val rl = RateLimiter(perSourcePerMinute = 2, globalPerMinute = 100, windowMs = 60_000, now = { now })
        assertTrue(rl.tryAcquire("a"))
        assertTrue(rl.tryAcquire("a"))
        assertFalse(rl.tryAcquire("a"))
        now += 60_001 // slide past the window
        assertTrue(rl.tryAcquire("a"))
        assertEquals(1, rl.remaining("a"))
    }
}

class PathSandboxTest {

    @Test
    fun normalize_resolvesDotAndDotDot() {
        assertEquals("/a/c", PathSandbox.normalize("/a/b/../c"))
        assertEquals("/a/b", PathSandbox.normalize("/a/./b"))
        assertEquals("/x", PathSandbox.normalize("//x//"))
        assertEquals("/", PathSandbox.normalize("/a/.."))
        assertEquals("/data", PathSandbox.normalize("/sdcard/../data")) // cannot escape then re-enter safely
    }

    @Test
    fun deniedRoots_areBlocked() {
        for (root in PathSandbox.DENIED_ROOTS) {
            assertFalse(PathSandbox.isAllowed(root), "$root must be denied")
            assertFalse(PathSandbox.isAllowed("$root/anything/deep"), "$root/... must be denied")
        }
    }

    @Test
    fun userStorage_isAllowed() {
        assertTrue(PathSandbox.isAllowed("/storage/emulated/0/DCIM/x.jpg"))
        assertTrue(PathSandbox.isAllowed("/sdcard/Download/file.pdf"))
    }

    @Test
    fun traversal_intoDeniedRoot_isBlocked() {
        assertFalse(PathSandbox.isAllowed("/sdcard/../data/data/com.evil/secrets"))
        // Not fooled by a denied prefix that is only a name prefix, not a path boundary.
        assertTrue(PathSandbox.isAllowed("/systemctl-notes/readme.txt"))
    }
}

class SsrfGuardTest {

    @Test
    fun loopbackLinkLocalMetadata_blocked() {
        for (h in listOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "169.254.169.254",
                "169.254.1.2", "100.100.100.200")) {
            assertTrue(SsrfGuard.isBlocked(h), "$h must be blocked")
        }
    }

    @Test
    fun rfc1918AndCgnat_allowed() {
        for (h in listOf("10.0.0.5", "192.168.1.10", "172.16.0.1", "172.31.255.254", "100.64.0.1")) {
            assertTrue(SsrfGuard.isAllowed(h), "$h must be allowed")
        }
    }

    @Test
    fun publicIpv4_blocked() {
        for (h in listOf("8.8.8.8", "1.1.1.1", "172.32.0.1", "192.169.0.1", "100.128.0.1")) {
            assertTrue(SsrfGuard.isBlocked(h), "$h must be blocked")
        }
    }

    @Test
    fun userinfoIsStripped_beforeClassification() {
        assertTrue(SsrfGuard.isBlocked("admin@169.254.169.254"))
        assertTrue(SsrfGuard.isAllowed("user:pass@192.168.1.5"))
        assertTrue(SsrfGuard.isBlocked("evil@8.8.8.8"))
    }

    @Test
    fun dnsNames_blockedByDefault() {
        // Names cannot be classified without resolution; the guard blocks and the caller re-checks
        // the resolved IP.
        assertTrue(SsrfGuard.isBlocked("example.com"))
        assertTrue(SsrfGuard.isBlocked("internal.local"))
    }

    @Test
    fun parseIpv4_edgeCases() {
        assertNull(SsrfGuard.parseIpv4("1.2.3"))
        assertNull(SsrfGuard.parseIpv4("1.2.3.4.5"))
        assertNull(SsrfGuard.parseIpv4("256.0.0.1"))
        assertNull(SsrfGuard.parseIpv4("a.b.c.d"))
        assertNull(SsrfGuard.parseIpv4("1.2.3."))
        assertEquals(listOf(192, 168, 0, 1), SsrfGuard.parseIpv4("192.168.0.1")?.toList())
    }
}
