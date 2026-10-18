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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * time in request order (a [Mutex] is FIFO), a request for a book that is queued or being built is ignored, a
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
    private val running = HashSet<String>()
    private val serial = Mutex()

    override fun enqueue(book: Book) {
        val initials = book.initials
        val accepted = withMonitor(lock) {
            if (initials in queued || initials in running) false else { queued[initials] = book; true }
        }
        if (!accepted) return
        scope.launch {
            serial.withLock {
                val next = withMonitor(lock) {
                    queued.remove(initials)?.also { running.add(initials) }
                } ?: return@withLock // dequeued while waiting
                try {
                    runner.run(cancelBuild = { cancelBuild(next) }) { withContext(Dispatchers.Default) { build(next) } }
                } catch (e: CancellationException) {
                    next.indexStatus = IndexStatus.UNDONE
                    currentCoroutineContext().ensureActive() // our own coroutine cancelled: propagate
                    Log.w(TAG, "Index build of $initials cancelled (background time expired)")
                } catch (e: Exception) {
                    Log.e(TAG, "Index creation failed for $initials", e)
                    next.indexStatus = IndexStatus.UNDONE
                } finally {
                    withMonitor(lock) { running.remove(initials) }
                }
            }
        }
    }

    override fun dequeue(initials: String) {
        val book = withMonitor(lock) { queued.remove(initials) } ?: return
        book.indexStatus = IndexStatus.UNDONE
    }
}
