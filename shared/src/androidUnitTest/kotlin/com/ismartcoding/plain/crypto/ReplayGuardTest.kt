package com.ismartcoding.plain.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReplayGuardTest {

    @Test
    fun freshNonce_ok_repeat_isReplay() {
        var now = 1_000_000L
        val guard = ReplayGuard(windowMs = 30_000L, now = { now })
        assertTrue(guard.check(now, "nonce-1") is ReplayGuard.Result.Ok)
        assertTrue(guard.check(now, "nonce-1") is ReplayGuard.Result.Replay)
        assertTrue(guard.check(now, "nonce-2") is ReplayGuard.Result.Ok)
    }

    @Test
    fun timestampOutsideWindow_isStale() {
        var now = 1_000_000L
        val guard = ReplayGuard(windowMs = 30_000L, now = { now })
        assertTrue(guard.check(now - 31_000L, "old") is ReplayGuard.Result.Stale)
        assertTrue(guard.check(now + 31_000L, "future") is ReplayGuard.Result.Stale)
        assertTrue(guard.check(now - 29_000L, "edge") is ReplayGuard.Result.Ok)
    }

    @Test
    fun nonces_arePrunedAfterWindow() {
        var now = 1_000_000L
        val guard = ReplayGuard(windowMs = 30_000L, now = { now })
        assertTrue(guard.check(now, "n") is ReplayGuard.Result.Ok)
        assertEquals(1, guard.size())
        // Advance well past the window; the next check should prune the stale nonce...
        now += 60_000L
        assertTrue(guard.check(now, "m") is ReplayGuard.Result.Ok)
        assertEquals(1, guard.size())
        // ...and the old nonce is usable again (a fresh request at the new time).
        assertTrue(guard.check(now, "n") is ReplayGuard.Result.Ok)
    }
}
