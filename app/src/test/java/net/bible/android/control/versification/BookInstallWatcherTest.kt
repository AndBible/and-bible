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

/** Review Focus #5: install/uninstall events for one book must hit the backup db in event order. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookInstallWatcherTest {
    private val docDao get() = DatabaseContainer.instance.repoDb.swordDocumentInfoDao()
    private val pushes = mutableListOf<List<String>>()
    private val starter = object : DocumentSyncStarter {
        override fun pushDocuments(initials: List<String>) { pushes.add(initials) }
    }

    @After
    fun tearDown() { resetDatabase() }

    /** Real threads (Dispatchers.Default) so an unordered launch really races; waits for every launched child. */
    private fun runEvents(vararg events: String) = runBlocking {
        val appScope = AppCoroutineScope()
        val launcher = OrderedLauncher(appScope)
        val gate = CompletableDeferred<Unit>()
        // Hold the key so both events are queued before either write may start.
        launcher.launch("book-install") { gate.await() }
        val watcher = BookInstallWatcher(launcher, starter)
        val book = Books.installed().books.first()
        val ev = BooksEvent(Books.installed(), book, true)
        try {
            events.forEach { if (it == "add") watcher.listener.bookAdded(ev) else watcher.listener.bookRemoved(ev) }
            gate.complete(Unit)
            withTimeout(30_000) { appScope.coroutineContext[Job]!!.children.toList().forEach { it.join() } }
        } finally {
            appScope.coroutineContext[Job]!!.cancel()
        }
        book.initials
    }

    @Test
    fun `add then remove leaves the book out of the backup db`() {
        repeat(5) {
            val initials = runEvents("add", "remove")
            assertNull(runBlocking { docDao.getBook(initials) })
        }
    }

    @Test
    fun `remove then add leaves the book in the backup db`() {
        repeat(5) {
            val initials = runEvents("remove", "add")
            val row = runBlocking { docDao.getBook(initials) }
            assertNotNull(row)
            assertEquals(initials, row!!.initials)
        }
    }
}
