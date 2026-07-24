package com.ismartcoding.plain.platform

import java.util.concurrent.locks.ReentrantLock

actual class Lock actual constructor() {
    private val delegate = ReentrantLock()

    actual fun <T> withLock(action: () -> T): T {
        delegate.lock()
        try {
            return action()
        } finally {
            delegate.unlock()
        }
    }
}
