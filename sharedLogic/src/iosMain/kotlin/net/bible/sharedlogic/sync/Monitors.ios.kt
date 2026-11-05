/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)

package net.bible.sharedlogic.sync

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawPtr
import kotlin.native.identityHashCode
import platform.posix.*

/**
 * Only active monitor acquisitions live in this registry. A reservation includes threads waiting to enter,
 * so an unlock/enter race cannot replace a monitor while an old waiter still uses it. Neither acquiring a
 * target mutex, waiting on its condition nor destroying it happens while holding the registry lock.
 * Identity hashing never calls user-defined equals/hashCode. Strong target references last only as long as
 * acquisitions do; no weak-reference sweep, GC timing dependency or permanent native allocation is needed.
 */
private object MonitorRegistry {
    private val lock = SynchronizedObject()
    private val buckets = mutableMapOf<Int, MutableList<Entry>>()

    private class Entry(val target: Any, val monitor: NativeMonitor, var users: Int = 1)

    fun reserve(target: Any): NativeMonitor {
        val hash = target.identityHashCode()
        synchronized(lock) {
            buckets[hash]?.firstOrNull { it.target === target }?.let {
                it.users++
                return it.monitor
            }
        }
        // Native resource allocation is outside the registry lock too. A racing reservation may win.
        val candidate = NativeMonitor()
        val selected = synchronized(lock) {
            val bucket = buckets.getOrPut(hash) { mutableListOf() }
            val existing = bucket.firstOrNull { it.target === target }
            if (existing != null) {
                existing.users++
                existing.monitor
            } else {
                bucket.add(Entry(target, candidate))
                candidate
            }
        }
        if (selected !== candidate) candidate.dispose()
        return selected
    }

    fun release(target: Any, monitor: NativeMonitor) {
        val hash = target.identityHashCode()
        val dispose = synchronized(lock) {
            val bucket = buckets.getValue(hash)
            val entry = bucket.first { it.target === target }
            check(entry.monitor === monitor)
            if (--entry.users == 0) {
                bucket.remove(entry)
                if (bucket.isEmpty()) buckets.remove(hash)
                true
            } else false
        }
        if (dispose) monitor.dispose()
    }

    fun size(): Int = synchronized(lock) { buckets.values.sumOf { it.size } }
}

/** Per-target recursive mutex and condition; its last acquisition owns destruction. */
@PublishedApi
internal class NativeMonitor {
    private val mutex = nativeHeap.alloc<pthread_mutex_t>()
    private val condition = nativeHeap.alloc<pthread_cond_t>()
    private var depth = 0
    private var owner: pthread_t? = null

    init {
        memScoped {
            val attr = alloc<pthread_mutexattr_t>()
            check(pthread_mutexattr_init(attr.ptr) == 0)
            try {
                check(pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE) == 0)
                check(pthread_mutex_init(mutex.ptr, attr.ptr) == 0)
            } finally {
                pthread_mutexattr_destroy(attr.ptr)
            }
        }
        check(pthread_cond_init(condition.ptr, null) == 0)
    }

    fun enter() {
        check(pthread_mutex_lock(mutex.ptr) == 0)
        owner = pthread_self()
        depth++
    }

    private fun checkOwner() {
        // trylock validates ownership without racing reads against a different owning thread.
        if (pthread_mutex_trylock(mutex.ptr) != 0) {
            throw IllegalStateException("Current thread does not own this monitor")
        }
        try {
            val owningThread = owner
            if (owningThread == null || pthread_equal(owningThread, pthread_self()) == 0 || depth == 0) {
                throw IllegalStateException("Current thread does not own this monitor")
            }
        } finally {
            pthread_mutex_unlock(mutex.ptr)
        }
    }

    fun exit() {
        checkOwner()
        if (--depth == 0) owner = null
        check(pthread_mutex_unlock(mutex.ptr) == 0)
    }

    fun await(timeoutMs: Long) {
        require(timeoutMs >= 0) { "timeoutMs must not be negative" }
        checkOwner()
        val savedDepth = depth
        // pthread_cond_wait releases only ONE recursive acquisition. Release the others explicitly,
        // keeping the final one until cond_wait atomically registers this waiter and unlocks it.
        repeat(savedDepth - 1) { check(pthread_mutex_unlock(mutex.ptr) == 0) }
        depth = 0
        owner = null
        try {
            if (timeoutMs == 0L) {
                check(pthread_cond_wait(condition.ptr, mutex.ptr) == 0)
            } else {
                memScoped {
                    val ts = alloc<timespec>()
                    clock_gettime(CLOCK_REALTIME.toUInt(), ts.ptr)
                    val totalNs = ts.tv_nsec + (timeoutMs % 1000) * 1_000_000L
                    ts.tv_sec += timeoutMs / 1000 + totalNs / 1_000_000_000L
                    ts.tv_nsec = totalNs % 1_000_000_000L
                    val result = pthread_cond_timedwait(condition.ptr, mutex.ptr, ts.ptr)
                    check(result == 0 || result == ETIMEDOUT)
                }
            }
        } finally {
            repeat(savedDepth - 1) { check(pthread_mutex_lock(mutex.ptr) == 0) }
            owner = pthread_self()
            depth = savedDepth
        }
    }

    fun notifyAllWaiters() {
        checkOwner()
        check(pthread_cond_broadcast(condition.ptr) == 0)
    }

    fun dispose() {
        check(pthread_cond_destroy(condition.ptr) == 0)
        check(pthread_mutex_destroy(mutex.ptr) == 0)
        nativeHeap.free(condition.rawPtr)
        nativeHeap.free(mutex.rawPtr)
    }
}

actual inline fun <T> withMonitor(target: Any, block: () -> T): T {
    val monitor = iosMonitorEnter(target)
    try {
        return block()
    } finally {
        iosMonitorExit(target, monitor)
    }
}

@PublishedApi
internal fun iosMonitorEnter(target: Any): NativeMonitor = MonitorRegistry.reserve(target).also { it.enter() }

@PublishedApi
internal fun iosMonitorExit(target: Any, monitor: NativeMonitor) {
    monitor.exit()
    MonitorRegistry.release(target, monitor)
}

actual fun monitorWait(target: Any, timeoutMs: Long) {
    val monitor = MonitorRegistry.reserve(target)
    try { monitor.await(timeoutMs) } finally { MonitorRegistry.release(target, monitor) }
}

actual fun monitorNotifyAll(target: Any) {
    val monitor = MonitorRegistry.reserve(target)
    try { monitor.notifyAllWaiters() } finally { MonitorRegistry.release(target, monitor) }
}

/** Visible to Native tests: idle monitor targets must not accumulate in the registry. */
internal fun activeNativeMonitorCount(): Int = MonitorRegistry.size()

actual val bookNameMonitor: Any = Any()
