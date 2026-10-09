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

package net.bible.android.control.bookmark

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review Focus 1: JS bookmark writes for one window apply in call order even when an earlier one
 * suspends, and different windows do not wait for each other. A gated block is queued ahead of the
 * real write so that an implementation that merely launches each call fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // note writes use UPSERT, which SDK 33's SQLite lacks
class BookmarkJsActionsTest {
    private val appScope = AppCoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var control: BookmarkControl
    private lateinit var actions: BookmarkJsActions
    private val errors = mutableListOf<String>()
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    @Before fun setUp() {
        Log.sinkOverride = { level, tag, msg, tr -> if (level == LogLevel.ERROR) errors += "$tag: $msg ${tr ?: ""}" }
        val launcher = OrderedLauncher(appScope)
        control = BookmarkControl(mock(WindowControl::class.java), mock(AndroidResourceProvider::class.java), launcher)
        actions = BookmarkJsActions(launcher, control)
    }

    @After fun tearDown() {
        try {
            appScope.cancel()
            resetDatabase()
        } finally {
            Log.sinkOverride = null
        }
    }

    private fun bookmark(): BibleBookmarkWithNotes = runBlocking {
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), "Ps 119:1"),
            null, true, null,
        ))
    }

    private fun note(b: BibleBookmarkWithNotes) = runBlocking { dao.bibleBookmarkById(b.id) }?.notes

    @Test fun `later note save for the same window wins even when the earlier one is slow`() {
        val b = bookmark()
        val gate = CompletableDeferred<Unit>()
        val first = actions.launch("w") { gate.await(); control.saveBibleBookmarkNote(b.id, "a") }
        val second = actions.saveBibleBookmarkNote("w", b.id, "b")
        runBlocking {
            delay(300) // a launch-per-call implementation has applied "b" by now
            gate.complete(Unit)
            withTimeout(10_000) { first.join(); second.join() }
        }
        assertEquals("b", note(b))
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test fun `delete after a slow note save for the same window leaves the bookmark gone without errors`() {
        val b = bookmark()
        val gate = CompletableDeferred<Unit>()
        val first = actions.launch("w") { gate.await(); control.saveBibleBookmarkNote(b.id, "a") }
        val second = actions.deleteBibleBookmarks("w", listOf(b.id))
        runBlocking {
            delay(300)
            gate.complete(Unit)
            withTimeout(10_000) { first.join(); second.join() }
        }
        assertNull(runBlocking { dao.bibleBookmarkById(b.id) })
        assertTrue("no ERROR expected, got $errors", errors.isEmpty())
    }

    @Test fun `a different window does not wait for a stuck one`() {
        val b = bookmark()
        val gate = CompletableDeferred<Unit>()
        val stuck = actions.launch("w") { gate.await() }
        val other = actions.saveBibleBookmarkNote("w2", b.id, "x")
        runBlocking { withTimeout(10_000) { other.join() } }
        assertFalse("window w is still gated", stuck.isCompleted)
        assertEquals("x", note(b))
        gate.complete(Unit)
        runBlocking { withTimeout(10_000) { stuck.join() } }
    }
}
