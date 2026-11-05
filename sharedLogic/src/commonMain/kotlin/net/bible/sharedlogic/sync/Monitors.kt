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
 * On iOS every target identity owns an independent recursive mutex and condition. The short-lived registry
 * lock is never held while entering a target lock or running its block. Idle targets consume no registry entries.
 *
 * Prefer an explicitly owned atomicfu lock for new code. Existing monitor sites are audited in
 * `docs/monitor-usage-allowlist.txt`; the source guard rejects unreviewed additions. Keep critical sections
 * limited to their owner's state. Never hold shared locks across preferences/Keychain/WebKit/Swift callbacks
 * or unrelated file reads and rendering; snapshot, call outside the lock, then publish. SQLite connections
 * still require their own per-connection exclusion, including queries and close.
 */
expect inline fun <T> withMonitor(target: Any, block: () -> T): T

/**
 * `Object.wait(timeoutMs)`; `0` waits without timeout. Must be called inside [withMonitor] on [target].
 * Releases every recursive acquisition of that target while waiting and restores the depth on return.
 */
expect fun monitorWait(target: Any, timeoutMs: Long = 0)

/** `Object.notifyAll()`. Must be called inside [withMonitor] on [target]. */
expect fun monitorNotifyAll(target: Any)

/**
 * The monitor jsword's `BookName` code locks on. JVM and Android: `BookName::class.java` itself, so converted
 * sites keep excluding against the files that still use `synchronized(BookName::class.java)`.
 */
expect val bookNameMonitor: Any
