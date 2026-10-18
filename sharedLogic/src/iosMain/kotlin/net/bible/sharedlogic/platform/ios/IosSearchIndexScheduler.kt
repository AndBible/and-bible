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
package net.bible.sharedlogic.platform.ios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.service.sword.index.AndroidIndexPolicy
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.search.IosSearchIndexingRunner
import net.bible.sharedlogic.platform.SearchIndexScheduler
import net.bible.sharedlogic.sync.withMonitor
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.index.IndexManagerFactory
import org.crosswire.jsword.index.IndexStatus

private const val TAG = "IosSearchIndexScheduler"

/**
 * iOS [SearchIndexScheduler]. Mirrors the queue semantics of Android's `SearchIndexingService`: builds run one at a
 * time in request order (a single worker coroutine drains a FIFO map), a request for a book that is queued or being built is ignored, a
 * failed build leaves the book `UNDONE` and the queue goes on, and [dequeue] drops a build that has not started
 * (status `UNDONE`). Each build runs under [runner] so UIKit grants background time and an expiry cancels the
 * `CREATE_INDEX-<initials>` job the same way a user cancel does. Deviation: there is no notification host.
 *
 * [build] and [cancelBuild] default to the jsword FTS index manager exactly as `SearchIndexingService.builder`
 * uses it (`AndroidIndexPolicy`, `scheduleIndexCreation`, which blocks until the build ends); they are
 * parameters so tests can observe the queue without a real index.
 */
class IosSearchIndexScheduler(
    private val runner: IosSearchIndexingRunner,
    private val scope: AppCoroutineScope,
    private val build: (Book) -> Unit = { book ->
        IndexManagerFactory.getIndexManager().apply {
            indexPolicy = AndroidIndexPolicy()
            scheduleIndexCreation(book)
        }
    },
    private val cancelBuild: (Book) -> Unit = { book ->
        JobManager.findJob(Progress.CREATE_INDEX.replace("%s", book.initials))?.cancel()
    },
) : SearchIndexScheduler {
    private val lock = Any()
    private val queued = LinkedHashMap<String, Book>()
    private var runningInitials: String? = null
    private var draining = false

    override fun enqueue(book: Book) {
        val initials = book.initials
        val startWorker = withMonitor(lock) {
            if (initials in queued || initials == runningInitials) return
            queued[initials] = book
            (!draining).also { if (it) draining = true }
        }
        // One worker drains the queue, so builds run strictly one after another in enqueue order (as the Android queue's
        // single worker thread does); launching one coroutine per book would let them race for the order.
        if (startWorker) scope.launch { drain() }
    }

    private suspend fun drain() {
        var finished = false
        try {
            while (true) {
                val next = withMonitor(lock) {
                    val first = queued.entries.firstOrNull()
                    if (first == null) {
                        draining = false
                        finished = true
                        null
                    } else {
                        queued.remove(first.key)
                        runningInitials = first.key
                        first.value
                    }
                } ?: return
                try {
                    runner.run(cancelBuild = { cancelBuild(next) }) { withContext(Dispatchers.Default) { build(next) } }
                } catch (e: CancellationException) {
                    next.indexStatus = IndexStatus.UNDONE
                    currentCoroutineContext().ensureActive() // our own coroutine cancelled: propagate
                    Log.w(TAG, "Index build of ${next.initials} cancelled (background time expired)")
                } catch (e: Exception) {
                    Log.e(TAG, "Index creation failed for ${next.initials}", e)
                    next.indexStatus = IndexStatus.UNDONE
                } finally {
                    withMonitor(lock) { runningInitials = null }
                }
            }
        } finally {
            if (!finished) withMonitor(lock) { draining = false } // abnormal exit (scope cancelled): allow a new worker
        }
    }

    override fun dequeue(initials: String) {
        val book = withMonitor(lock) { queued.remove(initials) } ?: return
        book.indexStatus = IndexStatus.UNDONE
    }
}
