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

import android.net.Uri
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.test.DatabaseResetter.resetDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Task 19 Step 5: `BookmarkControl.importFromUri`'s CSV-import error summary (class A) moves onto
 * the app-wide [AppDialogController], via a direct `post(Message(...))` (kept the original title,
 * which `Dialogs.showErrorMsg` cannot -- it always posts `title = null`).
 *
 * [BookmarkControl.importFromUri] does `withContext(Dispatchers.IO) { ... withContext(Dispatchers.Main)
 * { ... } }`; `Dispatchers.setMain(StandardTestDispatcher(testScheduler))` binds the inner hop to this
 * `runTest`'s own scheduler (the same pattern `ErrorReportControlTest.runOnTestMain` uses), so
 * `advanceUntilIdle()` actually drains it instead of dispatching onto a Robolectric main Looper
 * nothing pumps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarkControlImportFromUriDialogTest {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    private lateinit var bookmarkControl: BookmarkControl
    // Shared across the whole test method, and kept as the Main dispatcher until @After -- see the
    // matching field in ErrorReportControlTest for why resetting Main inside the test body's own
    // `finally` (the old shape here too) can deadlock a test that leaves a still-cancelling child
    // behind: it races that child off the test scheduler and onto the real, blocked Robolectric main
    // thread. (Neither test below currently leaves such a child, but the shape is now uniform with
    // the other three test classes that share this trap.)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val mockedWindowControl = Mockito.mock(WindowControl::class.java)
        bookmarkControl = BookmarkControl(mockedWindowControl, Mockito.mock(AndroidResourceProvider::class.java))
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        dialogs.cancelAll()
        resetDatabase()
        Dispatchers.resetMain()
    }

    private fun csvUri(content: String): Uri {
        val file = File.createTempFile("bookmarks-import", ".csv")
        file.deleteOnExit()
        file.writeText(content)
        return Uri.fromFile(file)
    }

    @Test
    fun invalidRowsPostAMessageWithTheOriginalTitleAndErrorSummary() = runTest(testDispatcher, timeout = 30.seconds) {
        val context = RuntimeEnvironment.getApplication()
        // "osisRef" header, one non-blank but unparseable data row -- getVerseRange() fails every
        // branch (empty ordinals, unparseable osisRef, no book/chapter/verse, no bible ref) and
        // parseCsvRowToBookmark() catches its own IllegalArgumentException and returns null, so
        // importBookmarksFromCsv() records "Record 2: Invalid bookmark data" without throwing.
        val uri = csvUri("osisRef\ngarbage\n")

        bookmarkControl.importFromUri(context, uri)
        advanceUntilIdle()

        val request = dialogs.pending.value!!.request as AppDialogRequest.Message
        assertEquals(
            "the title Dialogs.showErrorMsg cannot carry (it always posts title = null)",
            context.getString(R.string.import_items, "CSV"), request.title,
        )
        assertTrue(request.message.contains("Invalid bookmark data"))
        // I2: the plain "\n\n" between the summary and the per-record lines must survive as an
        // explicit <br><br> -- AppDialogRequest.Message is always parsed as HTML, which
        // otherwise collapses every newline into a single space.
        assertTrue("line breaks must survive HTML parsing (I2)", request.message.contains("<br><br>"))
        assertEquals(context.getString(R.string.okay), request.confirmText)
        assertTrue("cancellable, as the old AlertDialog (default cancelable, no setCancelable(false))", request.cancellable)
    }

    @Test
    fun aCleanImportPostsNothing() = runTest(testDispatcher, timeout = 30.seconds) {
        val context = RuntimeEnvironment.getApplication()
        // Header only, no data rows at all -- created = updated = errors = 0.
        val uri = csvUri("osisRef\n")

        bookmarkControl.importFromUri(context, uri)
        advanceUntilIdle()

        assertEquals(null, dialogs.pending.value)
    }
}
