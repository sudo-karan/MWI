package com.ismartcoding.plain.platform

/**
 * A minimal reentrant mutual-exclusion lock for non-suspend common code
 * (`synchronized` is JVM-only and unavailable in `commonMain`). Backed by
 * `ReentrantLock` on Android/JVM.
 */
expect class Lock() {
    fun <T> withLock(action: () -> T): T
}
