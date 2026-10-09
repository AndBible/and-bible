/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.control.versification

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/** Review Focus #5: install/uninstall events for one book must hit the backup db in event order. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookInstallWatcherTest {
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val starter = object : DocumentSyncStarter {
        override fun pushDocuments(initials: List<String>) {}
    }

    @After
    fun tearDown() { resetDatabase() }

    /**
     * Real threads (Dispatchers.Default). The FIRST backup-db write is held on a latch (via the watcher's
     * [BookInstallWatcher.beforeWrite] seam) while the second event is submitted and given time to run; a naive
     * per-callback launch would let the second write overtake the held first one, the ordered launcher must not.
     */
    private fun runEvents(vararg events: String) = runBlocking {
        val appScope = AppCoroutineScope()
        val launcher = OrderedLauncher(appScope)
        val watcher = BookInstallWatcher(launcher, starter)
        val firstWriteHeld = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        watcher.beforeWrite = {
            if (writes.incrementAndGet() == 1) {
                firstWriteHeld.complete(Unit)
                release.await()
            }
        }
        val book = Books.installed().books.first()
        val ev = BooksEvent(Books.installed(), book, true)
        try {
            events.forEachIndexed { i, it ->
                if (it == "add") watcher.listener.bookAdded(ev) else watcher.listener.bookRemoved(ev)
                if (i == 0) withTimeout(30_000) { firstWriteHeld.await() }
            }
            // Give an (incorrectly) unordered second write ample time to overtake the held first one.
            delay(300)
            release.complete(Unit)
            withTimeout(30_000) { appScope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
        } finally {
            release.complete(Unit)
            appScope.coroutineContext[Job]!!.cancel()
        }
        book.initials
    }

    @Test
    fun `add then remove leaves the book out of the backup db`() {
        repeat(3) {
            val initials = runEvents("add", "remove")
            assertNull(runBlocking { docDao.getBook(initials) })
        }
    }

    @Test
    fun `remove then add leaves the book in the backup db`() {
        repeat(3) {
            val initials = runEvents("remove", "add")
            val row = runBlocking { docDao.getBook(initials) }
            assertNotNull(row)
            assertEquals(initials, row!!.initials)
        }
    }
}
