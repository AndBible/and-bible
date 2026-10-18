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

package net.bible.android.view.activity.page

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.bible.android.TestBibleApplication
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.bookmark.BookmarkJsActions
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.bibleview.BibleViewJsChannel
import net.bible.sharedcore.bibleview.JsHost
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.OrderedLauncher
import net.bible.sharedui.reading.bridge.ReadingBridge
import net.bible.sharedui.reading.bridge.ReadingNavigator
import net.bible.sharedui.reading.bridge.ReadingWindowSurface
import net.bible.sharedui.reading.session.BibleWindowSession
import net.bible.sharedui.reading.session.BookmarkCreation
import net.bible.test.DatabaseResetter
import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bookmark, label and StudyPad family of [ReadingBridge] and [BookmarkCreation], driven on a bare
 * [BibleWindowSession] (no WebView): the writes must keep the per-window ordered queue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [35]) // bookmark writes use UPSERT, which SDK 33's SQLite lacks
class ReadingBridgeBookmarkFamilyTest {
    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var originalRepo: WindowRepository
    private lateinit var navigator: ReadingNavigator
    private lateinit var hostScope: CoroutineScope
    private lateinit var session: BibleWindowSession
    private lateinit var bridge: ReadingBridge
    // Own scope, launcher and control: the suite's shared application scope is cancelled by the database reset.
    private val appScope = AppCoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var control: BookmarkControl
    private lateinit var jsActions: BookmarkJsActions
    private val dao get() = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

    @Before fun setUp() {
        windowControl = WindowControl()
        repo = net.bible.test.testWindowRepository()
        windowControl.windowRepository = repo
        originalRepo = CommonUtils.windowControl.windowRepository
        CommonUtils.windowControl.windowRepository = repo
        repo.initializeAtEdge()
        val launcher = OrderedLauncher(appScope)
        control = BookmarkControl(windowControl, testAppSettings(), testCoreStrings(), launcher,
            net.bible.android.platform.androidSaxTextPolicy())
        jsActions = BookmarkJsActions(launcher, control)
        navigator = mock()
        hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        session = BibleWindowSession(
            repo.activeWindow, BibleViewJsChannel(mock<JsHost>()) {}, { hostScope },
            mock<ReadingWindowSurface>(), navigator,
            windowControlProvider = { windowControl }, bookmarkControlProvider = { control },
        )
        bridge = ReadingBridge(session) { jsActions }
    }

    @After fun tearDown() {
        try {
            session.dispose()
            appScope.cancel()
            hostScope.cancel()
            DatabaseResetter.resetDatabase(repo.scope)
        } finally {
            CommonUtils.windowControl.windowRepository = originalRepo
        }
    }

    /** Waits until every write already queued for the session's window has run. */
    private fun drain() = runBlocking { withTimeout(10_000) { jsActions.launch(session.window.id) {}.join() } }

    private fun label(name: String): Label = runBlocking { control.insertOrUpdateLabel(Label(name = name, new = true)) }

    private fun bookmark(ref: String): BibleBookmarkWithNotes = runBlocking {
        control.addOrUpdateBibleBookmark(BibleBookmarkWithNotes(
            VerseRangeFactory.fromString(Versifications.instance().getVersification("KJV"), ref),
            null, true, null,
        ))
    }

    private fun onlyBookmark(): BibleBookmarkWithNotes = runBlocking { control.allBibleBookmarks() }.single()

    @Test fun addBookmarkAppliesAutoAssignLabelsAndPrimaryLabel() {
        val l1 = label("L1")
        session.workspaceSettings.autoAssignLabels = mutableSetOf(l1.id)
        session.workspaceSettings.autoAssignPrimaryLabel = l1.id
        bridge.addBookmark("KJV", 1000, -1, false)
        drain()
        val created = onlyBookmark()
        assertEquals(l1.id, created.primaryLabelId)
        assertEquals(listOf(l1.id), runBlocking { control.labelsForBookmark(created) }.map { it.id })
    }

    @Test fun addParagraphBreakBookmarkUsesTheParagraphBreakLabel() {
        bridge.addParagraphBreakBookmark("KJV", 1000, -1)
        drain()
        val created = onlyBookmark()
        val breakLabel = runBlocking { control.paragraphBreakLabel() }
        assertEquals(breakLabel.id, created.primaryLabelId)
        assertEquals(listOf(breakLabel.id), runBlocking { control.labelsForBookmark(created) }.map { it.id })
    }

    @Test fun createWholePageBookmarkForUnknownBookDoesNothing() {
        bridge.createWholePageBookmark("NoSuchBook", "Some.key")
        drain()
        assertTrue(runBlocking { control.allBibleBookmarks() }.isEmpty())
        assertTrue(runBlocking { dao.allGenericBookmarks() }.isEmpty())
    }

    @Test fun writesForOneWindowKeepCallOrder() {
        val removed = bookmark("Ps 119:1")
        val renoted = bookmark("Ps 119:2")
        val gate = CompletableDeferred<Unit>()
        val stuck = jsActions.launch(session.window.id) { gate.await() }
        try {
            bridge.saveBookmarkNote(removed.id.toString(), "note")
            bridge.removeBookmark(removed.id.toString())
            bridge.saveBookmarkNote(renoted.id.toString(), "first")
            bridge.saveBookmarkNote(renoted.id.toString(), "second")
            runBlocking { delay(200) }
            assertEquals("writes must wait behind the window's queue", removed.id,
                runBlocking { dao.bibleBookmarkById(removed.id) }?.id)
        } finally {
            gate.complete(Unit)
        }
        runBlocking { withTimeout(10_000) { stuck.join() } }
        drain()
        assertNull("save note, then remove: no bookmark row", runBlocking { dao.bibleBookmarkById(removed.id) })
        assertEquals("second", runBlocking { dao.bibleBookmarkById(renoted.id) }?.notes)
    }

    @Test fun assignLabelsCancelledLeavesLabelsUnchanged() {
        val l1 = label("L1")
        val b = runBlocking { control.addOrUpdateBibleBookmark(bookmark("Ps 119:1"), setOf(l1.id)) }
        navigator.stub { onBlocking { assignLabels(any(), any()) } doReturn null }
        runBlocking { BookmarkCreation(session, control).assignLabels(b) }
        assertEquals(listOf(l1.id), runBlocking { control.labelsForBookmark(b) }.map { it.id })
    }

    @Test fun assignLabelsAppliesTheChosenLabels() {
        val l1 = label("L1")
        val l2 = label("L2")
        val b = bookmark("Ps 119:1")
        navigator.stub { onBlocking { assignLabels(any(), any()) } doReturn listOf(l1.id, l2.id) }
        runBlocking { BookmarkCreation(session, control).assignLabels(b) }
        assertEquals(setOf(l1.id, l2.id), runBlocking { control.labelsForBookmark(b) }.map { it.id }.toSet())
    }
}
