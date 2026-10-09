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

import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings

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
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities
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
    private val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val warnings = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    private var previousSink: ((LogLevel, String, String, Throwable?) -> Unit)? = null

    @Before fun setUp() {
        previousSink = Log.sinkOverride
        Log.sinkOverride = { level, tag, msg, tr ->
            if (level == LogLevel.ERROR) errors += "$tag: $msg ${tr ?: ""}"
            if (level == LogLevel.WARN) warnings += "$tag: $msg"
        }
        val launcher = OrderedLauncher(appScope)
        control = BookmarkControl(mock(WindowControl::class.java), testAppSettings(), testCoreStrings(), launcher)
        actions = BookmarkJsActions(launcher, control)
    }

    @After fun tearDown() {
        try {
            appScope.cancel()
            resetDatabase()
        } finally {
            Log.sinkOverride = previousSink
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

    /** Fix round 1, finding 2: "save note, then open StudyPad/MyNotes" must open on the saved note. */
    @Test fun `an open after a slow note save of the same window runs only after the save landed`() {
        val b = bookmark()
        val gate = CompletableDeferred<Unit>()
        val save = actions.launch("w") { gate.await(); control.saveBibleBookmarkNote(b.id, "saved") }
        val seen = CompletableDeferred<String?>()
        val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val open = actions.afterQueuedWrites("w", callerScope, Dispatchers.Default) {
                seen.complete(dao.bibleBookmarkById(b.id)?.notes)
            }
            runBlocking {
                delay(300) // an open that does not wait for the queue has run by now
                assertFalse("the open ran before the queued save", seen.isCompleted)
                gate.complete(Unit)
                withTimeout(10_000) { save.join(); open.join() }
            }
            assertEquals("saved", runBlocking { seen.await() })
        } finally {
            callerScope.cancel()
        }
    }

    /** The open runs OUTSIDE the window's queue: it may queue (and wait for) writes of its own. */
    @Test fun `an open that itself waits for a write of the same window does not deadlock`() {
        val b = bookmark()
        val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val open = actions.afterQueuedWrites("w", callerScope, Dispatchers.Default) {
                actions.saveBibleBookmarkNote("w", b.id, "inner").join()
            }
            runBlocking { withTimeout(10_000) { open.join() } }
            assertEquals("inner", note(b))
        } finally {
            callerScope.cancel()
        }
    }

    /** Minor (a): a write naming an entry deleted meanwhile is skipped with a warning, not an ERROR. */
    @Test fun `a study pad entry after a vanished entry is skipped with a warning`() {
        val label = runBlocking { control.insertOrUpdateLabel(BookmarkEntities.Label(new = true).apply { name = "Pad" }) }
        val missing = IdType()
        runBlocking { withTimeout(10_000) { actions.createStudyPadEntry("w", label.id, "journal", missing).join() } }
        assertTrue("no ERROR expected, got $errors", errors.isEmpty())
        assertTrue(warnings.toString(), warnings.any { it.startsWith("BookmarkJsActions:") && it.contains(missing.toString()) })
    }
}
