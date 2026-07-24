package com.ismartcoding.plain.web.security

import com.ismartcoding.plain.platform.Lock
import com.ismartcoding.plain.platform.epochMillis

/**
 * Login rate limiter (spec §5): **5/min per source + 20/min global**, keyed on the *real* socket
 * peer. `ForwardedHeaders` is deliberately NOT installed server-side, so a client cannot spoof its
 * source to escape the per-source bucket.
 *
 * Sliding 60s window. Both buckets must have headroom for a request to be admitted; an admitted
 * request consumes one slot in each. Thread-safe.
 */
class RateLimiter(
    private val perSourcePerMinute: Int = 5,
    private val globalPerMinute: Int = 20,
    private val windowMs: Long = 60_000L,
    private val now: () -> Long = ::epochMillis,
) {
    private val perSource = HashMap<String, ArrayDeque<Long>>()
    private val global = ArrayDeque<Long>()
    private val lock = Lock()

    /** Returns true if the attempt from [source] is admitted (and records it); false if throttled. */
    fun tryAcquire(source: String): Boolean = lock.withLock {
        val current = now()
        val cutoff = current - windowMs

        pruneGlobal(cutoff)
        val bucket = perSource.getOrPut(source) { ArrayDeque() }
        prune(bucket, cutoff)

        if (bucket.size >= perSourcePerMinute) return@withLock false
        if (global.size >= globalPerMinute) return@withLock false

        bucket.addLast(current)
        global.addLast(current)
        true
    }

    /** Remaining per-source allowance right now (diagnostic/testing). */
    fun remaining(source: String): Int = lock.withLock {
        val cutoff = now() - windowMs
        val bucket = perSource[source] ?: return@withLock perSourcePerMinute
        prune(bucket, cutoff)
        (perSourcePerMinute - bucket.size).coerceAtLeast(0)
    }

    private fun pruneGlobal(cutoff: Long) {
        while (global.isNotEmpty() && global.first() < cutoff) global.removeFirst()
    }

    private fun prune(bucket: ArrayDeque<Long>, cutoff: Long) {
        while (bucket.isNotEmpty() && bucket.first() < cutoff) bucket.removeFirst()
    }
}
