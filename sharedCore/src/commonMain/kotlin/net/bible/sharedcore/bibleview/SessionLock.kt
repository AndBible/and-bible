package net.bible.sharedcore.bibleview

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * A reentrant lock for common code that cannot see atomicfu itself (`:sharedUi` has no dependency on it).
 * Non-inline on purpose: callers `return@withLock` from the block and no atomicfu symbol is inlined into them.
 */
class SessionLock {
    private val lock = SynchronizedObject()

    fun <T> withLock(block: () -> T): T = synchronized(lock) { block() }
}
