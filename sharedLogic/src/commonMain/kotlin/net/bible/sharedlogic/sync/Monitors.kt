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

/**
 * Common monitor primitive. On JVM and Android this is the very same object monitor as `synchronized(target)`,
 * so a block converted to it still excludes against plain `synchronized` blocks (and jsword) on the same object.
 * On iOS it is one process-wide reentrant lock (coarse but correct): all monitors share it, so a [withMonitor]
 * block must not block waiting for another thread that itself takes any monitor.
 */
expect inline fun <T> withMonitor(target: Any, block: () -> T): T

/**
 * `Object.wait(timeoutMs)`; `0` waits without timeout. Must be called inside [withMonitor] on [target]. On iOS the
 * shared lock must be held exactly once (no nested [withMonitor]), or the wait cannot release it.
 */
expect fun monitorWait(target: Any, timeoutMs: Long = 0)

/** `Object.notifyAll()`. Must be called inside [withMonitor] on [target]. */
expect fun monitorNotifyAll(target: Any)

/**
 * The monitor jsword's `BookName` code locks on. JVM and Android: `BookName::class.java` itself, so converted
 * sites keep excluding against the files that still use `synchronized(BookName::class.java)`.
 */
expect val bookNameMonitor: Any
