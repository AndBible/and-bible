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
package net.bible.android

import net.bible.android.control.versification.BookInstallWatcher
import net.bible.sharedcore.cloud.DocumentSyncStarter
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.service.sword.SwordEnvironmentInitialisation
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.BooksListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: [BibleApplication.onCreate] runs initialiseJSwordFolders BEFORE startKoin. The Koin-resolved
 * [BookInstallWatcher] must therefore be started after Koin, or the resolution throws inside the swallow-all
 * catch and no install listener is ever registered. Reproduces the real order by stopping Koin and re-running
 * onCreate with JSword uninitialised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookInstallWatcherStartupTest {
    @Suppress("UNCHECKED_CAST")
    private fun listeners(): MutableList<BooksListener> {
        val f = Class.forName("org.crosswire.jsword.book.AbstractBookList").getDeclaredField("listeners")
        f.isAccessible = true
        return f.get(Books.installed()) as MutableList<BooksListener>
    }

    private fun watcherListeners() = listeners().filter { it.javaClass.name.startsWith(BookInstallWatcher::class.java.name) }

    @Test
    fun watcherListensAfterColdStartWhereKoinStartsAfterSwordInit() {
        val sword = SwordEnvironmentInitialisation::class.java.getDeclaredField("isSwordLoaded")
        sword.isAccessible = true
        val wasLoaded = sword.getBoolean(SwordEnvironmentInitialisation)
        try {
            watcherListeners().forEach { listeners().remove(it) }
            // cold start: JSword not yet initialised, no Koin
            sword.setBoolean(SwordEnvironmentInitialisation, false)
            GlobalContext.stopKoin()

            BibleApplication.application.onCreate()

            assertEquals("exactly one BookInstallWatcher listener registered", 1, watcherListeners().size)
        } finally {
            sword.setBoolean(SwordEnvironmentInitialisation, wasLoaded)
        }
    }

    @Test
    fun startBookInstallWatcherIsIdempotent() {
        // stale watchers from earlier Koin restarts in this JVM must not skew the count
        watcherListeners().forEach { listeners().remove(it) }
        SwordEnvironmentInitialisation.startBookInstallWatcher()
        SwordEnvironmentInitialisation.startBookInstallWatcher()
        assertEquals(1, watcherListeners().size)
    }

    /** A Koin restart builds a new watcher; the old instance's listener must not stay on the static JSword list. */
    @Test
    fun separateWatcherInstancesLeaveExactlyOneListener() {
        val starter = object : DocumentSyncStarter {
            override fun pushDocuments(initials: List<String>) {}
        }
        val scope = AppCoroutineScope()
        watcherListeners().forEach { listeners().remove(it) }
        val first = BookInstallWatcher(OrderedLauncher(scope), starter)
        val second = BookInstallWatcher(OrderedLauncher(scope), starter)
        try {
            first.startListening()
            second.startListening()
            assertEquals(1, watcherListeners().size)
            assertTrue("latest instance stays registered", listeners().contains(second.listener))
            assertFalse("old instance removed", listeners().contains(first.listener))
        } finally {
            watcherListeners().forEach { listeners().remove(it) }
        }
    }
}
