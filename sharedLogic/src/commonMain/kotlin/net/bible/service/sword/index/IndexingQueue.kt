/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.service.sword.index

import androidx.annotation.VisibleForTesting
import net.bible.sharedcore.log.Log
import net.bible.sharedlogic.sync.monitorNotifyAll
import net.bible.sharedlogic.sync.monitorWait
import net.bible.sharedlogic.sync.withMonitor
import net.bible.sharedlogic.util.currentTimeMillis
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.IndexStatus

private const val TAG = "SearchIndexingService"

/** What the foreground service hears from the [IndexingQueue] worker. */
@VisibleForTesting interface IndexingObserver {
    fun onBookStarted(book: Book)
    fun onIdle()
}

/**
 * The FIFO of book initials to index and the single worker thread that drains it. [build] runs one book at a time and
 * completes it before returning; an exception from it leaves the book `UNDONE` and the next book is built.
 */
@VisibleForTesting class IndexingQueue(
    private val resolve: (String) -> Book?,
    private val build: (Book) -> Unit,
    private val startWorker: (name: String, body: () -> Unit) -> Unit,
) {
    private val lock = Any()
    private val pending = LinkedHashMap<String, Book?>()
    private var current: Book? = null
    private var currentInitials: String? = null
    private var running = false
    private var observer: IndexingObserver? = null

    val isBusy: Boolean get() = withMonitor(lock) { running }

    /** False when [initials] is already queued or being built. Starts the worker if it is not running. */
    fun offer(initials: String, instance: Book? = null): Boolean = withMonitor(lock) {
        if (initials == currentInitials || pending.containsKey(initials)) return false
        pending[initials] = instance
        if (!running) {
            running = true
            startWorker("search-indexing", ::drain)
        }
        true
    }

    /** Removes a queued build that has not started; its instance (if known) ends `UNDONE`. */
    fun remove(initials: String) {
        val instance = withMonitor(lock) { if (pending.containsKey(initials)) pending.remove(initials) else return }
        instance?.indexStatus = IndexStatus.UNDONE
    }

    /** Runs [block] under the queue lock if no work is queued or running (nothing can be enqueued meanwhile). */
    fun runIfIdle(block: () -> Unit) = withMonitor(lock) { if (!running) block() }

    fun currentBook(): Book? = withMonitor(lock) { current }

    /** Registers [o]; returns false (and registers nothing) when there is no work left, so the caller stops itself. */
    fun attach(o: IndexingObserver): Boolean = withMonitor(lock) {
        if (!running) return false
        observer = o
        current?.let { o.onBookStarted(it) }
        true
    }

    fun detach(o: IndexingObserver? = null) = withMonitor(lock) {
        if (o == null || observer === o) observer = null
    }

    fun awaitIdle(timeoutMs: Long): Boolean = withMonitor(lock) {
        val end = currentTimeMillis() + timeoutMs
        while (running) {
            val left = end - currentTimeMillis()
            if (left <= 0) return false
            monitorWait(lock, left)
        }
        true
    }

    private fun drain() {
        while (true) {
            val initials = withMonitor(lock) {
                val next = pending.keys.firstOrNull()
                if (next == null) {
                    running = false
                    current = null
                    currentInitials = null
                    val o = observer
                    observer = null
                    // Told under the lock (it is re-entrant): anyone who sees the queue idle (awaitIdle, runIfIdle)
                    // also sees the observer already stopped, and no enqueue can slip in between.
                    o?.let { runCatching { it.onIdle() } }
                    monitorNotifyAll(lock)
                } else {
                    val instance = pending.remove(next)
                    currentInitials = next
                    current = resolve(next) ?: instance
                }
                next
            }
            if (initials == null) return
            buildOne(initials)
        }
    }

    private fun buildOne(initials: String) {
        val book = withMonitor(lock) { current }
        if (book == null) {
            // Not installed and no instance: nothing reachable to reset (an instance-carrying request never gets here).
            Log.w(TAG, "Skipping $initials: not installed")
            return
        }
        val o = withMonitor(lock) { observer }
        runCatching { o?.onBookStarted(book) }
        try {
            build(book)
        } catch (e: Exception) {
            Log.e(TAG, "Index creation failed for $initials", e)
            book.indexStatus = IndexStatus.UNDONE
        }
    }
}
