package com.ismartcoding.plain.crypto

import com.ismartcoding.plain.platform.Lock
import com.ismartcoding.plain.platform.epochMillis

/**
 * Per-request replay defense for token-mode API calls (spec §5).
 *
 * A decrypted request body carries `TIMESTAMP|NONCE|{json}`. [check] accepts a request only if
 *  1. its timestamp is within ±[windowMs] of now (default 30s), and
 *  2. its nonce has not been seen inside that window.
 *
 * Nonces are retained for the width of the window and pruned lazily, so memory is bounded by the
 * request rate. Thread-safe via a coarse lock (LAN request volume is modest).
 */
class ReplayGuard(
    private val windowMs: Long = 30_000L,
    private val now: () -> Long = ::epochMillis,
) {
    private val seen = HashMap<String, Long>()
    private val lock = Lock()

    sealed interface Result {
        data object Ok : Result
        data object Stale : Result       // outside the timestamp window
        data object Replay : Result      // nonce already used within the window
    }

    fun check(timestampMs: Long, nonce: String): Result = lock.withLock {
        val current = now()
        if (kotlin.math.abs(current - timestampMs) > windowMs) return@withLock Result.Stale

        prune(current)
        if (seen.containsKey(nonce)) return@withLock Result.Replay

        seen[nonce] = current
        Result.Ok
    }

    private fun prune(current: Long) {
        if (seen.isEmpty()) return
        val cutoff = current - windowMs
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value < cutoff) it.remove()
        }
    }

    /** Test/diagnostic hook: number of nonces currently retained. */
    fun size(): Int = lock.withLock { seen.size }
}
