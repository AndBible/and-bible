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
package net.bible.android.view.activity.bookmark

import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings

import net.bible.test.testOrderedLauncher
import net.bible.android.AppDialogControllerResetRule
import android.net.Uri
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.bible.android.TEST_SDK
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowControl
import net.bible.sharedcore.ui.dialog.AppDialogController
import net.bible.sharedcore.ui.dialog.AppDialogRequest
import net.bible.test.DatabaseResetter.resetDatabase
import org.junit.After
import org.junit.Rule
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
 * Task 19 Step 5: the CSV import's error summary ([BookmarkCsvFlow.importFromUri], moved there from
 * `BookmarkControl` in L1a Task 11) (class A) moves onto
 * the app-wide [AppDialogController], via a direct `post(Message(...))` (kept the original title,
 * which `Dialogs.showErrorMsg` cannot -- it always posts `title = null`).
 *
 * [BookmarkCsvFlow.importFromUri] does `withContext(Dispatchers.IO) { ... withContext(Dispatchers.Main)
 * { ... } }`; `Dispatchers.setMain(StandardTestDispatcher(testScheduler))` binds the inner hop to this
 * `runTest`'s own scheduler (the same pattern `ErrorReportControlTest.runOnTestMain` uses), so
 * `advanceUntilIdle()` actually drains it instead of dispatching onto a Robolectric main Looper
 * nothing pumps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BookmarkCsvFlowTest {
    private val dialogs: AppDialogController get() = KoinJavaComponent.get(AppDialogController::class.java)
    @get:Rule val dialogReset = AppDialogControllerResetRule()
    private lateinit var bookmarkControl: BookmarkControl
    private lateinit var flow: BookmarkCsvFlow
    // Keep Main on the exact scheduler through runTest's child completion. @After checks that
    // boundary before restoring the real Looper dispatcher required by blocking reset preparation.
    private val testDispatcher = StandardTestDispatcher()
    private val bodyJobs = mutableListOf<Job>()

    @Before
    fun setUp() {
        val mockedWindowControl = Mockito.mock(WindowControl::class.java)
        bookmarkControl = BookmarkControl(mockedWindowControl, testAppSettings(), testCoreStrings(), testOrderedLauncher(), net.bible.android.platform.androidSaxTextPolicy())
        flow = BookmarkCsvFlow(bookmarkControl)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        finishCsvFixture(bodyJobs) {
            dialogs.cancelAll()
            resetDatabase()
        }
    }

    private fun csvUri(content: String): Uri {
        val file = File.createTempFile("bookmarks-import", ".csv")
        file.deleteOnExit()
        file.writeText(content)
        return Uri.fromFile(file)
    }

    @Test
    fun invalidRowsPostAMessageWithTheOriginalTitleAndErrorSummary() = runTest(testDispatcher, timeout = 30.seconds) {
        bodyJobs += checkNotNull(currentCoroutineContext()[Job])
        val context = RuntimeEnvironment.getApplication()
        // "osisRef" header, one non-blank but unparseable data row -- getVerseRange() fails every
        // branch (empty ordinals, unparseable osisRef, no book/chapter/verse, no bible ref) and
        // parseCsvRowToBookmark() catches its own IllegalArgumentException and returns null, so
        // importBookmarksFromCsv() records "Record 2: Invalid bookmark data" without throwing.
        val uri = csvUri("osisRef\ngarbage\n")

        flow.importFromUri(context, uri)
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
        bodyJobs += checkNotNull(currentCoroutineContext()[Job])
        val context = RuntimeEnvironment.getApplication()
        // Header only, no data rows at all -- created = updated = errors = 0.
        val uri = csvUri("osisRef\n")

        flow.importFromUri(context, uri)
        advanceUntilIdle()

        assertEquals(null, dialogs.pending.value)
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun finishCsvFixture(bodyJobs: List<Job>, reset: () -> Unit) {
    check(bodyJobs.all { it.isCompleted }) { "CSV test children must complete before Main restoration" }
    Dispatchers.resetMain()
    reset()
}
