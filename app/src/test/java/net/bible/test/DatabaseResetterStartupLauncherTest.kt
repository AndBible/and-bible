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

package net.bible.test

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.versification.BookInstallWatcher
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Koin starts once per JVM, and `BibleApplication.onCreate` resolves the [BookInstallWatcher] before the fixture
 * installs its own launcher, so the watcher's writes run on the startup launcher that no fixture scope owns. A reset
 * that closed the databases under such a write crashed the fork JVM (SIGSEGV in `syncJournal`, a module restore
 * test's `bookRemoved` write racing `closeDatabasesAfterFlush`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class DatabaseResetterStartupLauncherTest {
    @Test
    fun resetLetsAnInFlightBookInstallWatcherWriteFinishBeforeClosing() {
        val watcher = KoinJavaComponent.get<BookInstallWatcher>(BookInstallWatcher::class.java)
        val savedBeforeWrite = watcher.beforeWrite
        val writing = CompletableDeferred<Job>()
        // Long enough to outlast the reset's own fixed waits, finite so a fixed reset only waits for it.
        watcher.beforeWrite = { writing.complete(currentCoroutineContext()[Job]!!); delay(2_000) }
        var writeFinishedAtClose: Boolean? = null
        try {
            val book = Books.installed().getBooks().first()
            watcher.listener.bookRemoved(BooksEvent(Books.installed(), book, false))
            val write = runBlocking { withTimeout(10_000) { writing.await() } }
            DatabaseResetter.beforeCloseObserver = { if (writeFinishedAtClose == null) writeFinishedAtClose = write.isCompleted }
            DatabaseResetter.resetDatabase()
            assertEquals("the reset closed the databases under an in-flight BookInstallWatcher write", true, writeFinishedAtClose)
        } finally {
            DatabaseResetter.beforeCloseObserver = null
            watcher.beforeWrite = savedBeforeWrite
        }
    }
}
