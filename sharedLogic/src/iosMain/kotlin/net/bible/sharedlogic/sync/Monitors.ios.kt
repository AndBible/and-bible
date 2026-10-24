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

package net.bible.sharedlogic.sync

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.memScoped
import platform.posix.CLOCK_REALTIME
import platform.posix.PTHREAD_MUTEX_RECURSIVE
import platform.posix.clock_gettime
import platform.posix.pthread_cond_init
import platform.posix.pthread_cond_t
import platform.posix.pthread_cond_timedwait
import platform.posix.pthread_cond_wait
import platform.posix.pthread_cond_broadcast
import platform.posix.pthread_mutex_lock
import platform.posix.pthread_mutex_t
import platform.posix.pthread_mutex_unlock
import platform.posix.pthread_mutex_init
import platform.posix.pthread_mutexattr_init
import platform.posix.pthread_mutexattr_settype
import platform.posix.pthread_mutexattr_t
import platform.posix.timespec

/**
 * One process-wide recursive mutex plus one condition variable. Every monitor shares them: coarse, but correct
 * (waiters re-check their own predicate; [monitorNotifyAll] broadcasts).
 */
@OptIn(ExperimentalForeignApi::class)
private object GlobalMonitor {
    val mutex = nativeHeap.alloc<pthread_mutex_t>()
    val cond = nativeHeap.alloc<pthread_cond_t>()

    /** Lock depth of the owning thread; only read or written while holding [mutex]. */
    var depth = 0

    init {
        val attr = nativeHeap.alloc<pthread_mutexattr_t>()
        pthread_mutexattr_init(attr.ptr)
        pthread_mutexattr_settype(attr.ptr, PTHREAD_MUTEX_RECURSIVE)
        pthread_mutex_init(mutex.ptr, attr.ptr)
        pthread_cond_init(cond.ptr, null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun <T> withMonitor(target: Any, block: () -> T): T {
    iosMonitorEnter()
    try {
        return block()
    } finally {
        iosMonitorExit()
    }
}

@PublishedApi
@OptIn(ExperimentalForeignApi::class)
internal fun iosMonitorEnter() {
    pthread_mutex_lock(GlobalMonitor.mutex.ptr)
    GlobalMonitor.depth++
}

@PublishedApi
@OptIn(ExperimentalForeignApi::class)
internal fun iosMonitorExit() {
    GlobalMonitor.depth--
    pthread_mutex_unlock(GlobalMonitor.mutex.ptr)
}

@OptIn(ExperimentalForeignApi::class)
actual fun monitorWait(target: Any, timeoutMs: Long) {
    check(GlobalMonitor.depth == 1) { "monitorWait requires holding the monitor at lock depth 1, was ${GlobalMonitor.depth}" }
    GlobalMonitor.depth = 0
    try {
        if (timeoutMs <= 0) {
            pthread_cond_wait(GlobalMonitor.cond.ptr, GlobalMonitor.mutex.ptr)
        } else {
            memScoped {
                val ts = alloc<timespec>()
                clock_gettime(CLOCK_REALTIME.toUInt(), ts.ptr)
                val totalNs: Long = ts.tv_nsec + (timeoutMs % 1000) * 1_000_000L
                ts.tv_sec += timeoutMs / 1000 + totalNs / 1_000_000_000L
                ts.tv_nsec = totalNs % 1_000_000_000L
                pthread_cond_timedwait(GlobalMonitor.cond.ptr, GlobalMonitor.mutex.ptr, ts.ptr)
            }
        }
    } finally {
        GlobalMonitor.depth = 1
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun monitorNotifyAll(target: Any) {
    check(GlobalMonitor.depth >= 1) { "monitorNotifyAll requires holding the monitor" }
    pthread_cond_broadcast(GlobalMonitor.cond.ptr)
}

actual val bookNameMonitor: Any = Any()
