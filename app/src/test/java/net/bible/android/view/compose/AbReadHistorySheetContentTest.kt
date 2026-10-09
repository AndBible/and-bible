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

package net.bible.android.view.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.view.activity.page.screen.readHistoryApplyDeletes
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.progress.AbReadHistorySheetContent
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Task 27 (platform-dialog removal, run 3) fix round 2: [AbReadHistorySheetContent] tested
 * directly here rather than through [net.bible.sharedui.progress.AbReadHistorySheet] — an open
 * `ModalBottomSheet` hangs a Robolectric idle forever and must never be captured or driven in a
 * test, per this run's "Hang protection" rule and that file's own kdoc (same reason
 * `ShareVersesSheetContentTest` tests `ShareVersesSheetContent` rather than `ShareVersesSheet`).
 *
 * [show]'s `close()` reproduces only `AbReadHistorySheet.commit()`'s ordering
 * (`onApplyDeletes(pendingDelete.toList()); onDismiss()`) — `onApplyDeletes` itself is the REAL
 * production [readHistoryApplyDeletes] function `ComposeReadingViewHost` calls verbatim, not a
 * test-harness copy of its `ids.isNotEmpty()` guard (round 1's gap, named again in review round 2:
 * a harness-side copy of the guard proves the harness matches itself, not that the production
 * lambda behaves correctly). So "the delete callback fires with exactly these ids, and not at all
 * when nothing was toggled" here exercises the real guard, via a fake [ReadingProgressService]
 * (mockk was removed from this repo; no mocking framework is used). The per-row toggle itself
 * (checked via a stable [net.bible.sharedui.components] test tag, since the icon swaps between ✕
 * and ↩ rather than carrying one fixed `contentDescription`) is real `AbReadHistorySheetContent`
 * behaviour, not test-harness code either.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbReadHistorySheetContentTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        ReadHistoryRow(id = "h1", primary = "12 Aug 2026 10:15", secondary = "KJV"),
        ReadHistoryRow(id = "h2", primary = "11 Aug 2026 09:00", secondary = "ESV"),
    )

    /** Same one-member-per-line fake style as `:sharedCore`'s `ReadingProgressControllerTest.Fake`
     *  and `ComposeReadingViewHostTest`'s own `ReadHistoryApplyDeletesTest.RecordingReadingProgressService`. */
    private class RecordingReadingProgressService : ReadingProgressService {
        val deleteCalls = mutableListOf<Pair<List<String>, Int>>()

        override suspend fun currentCycle() = 0
        override suspend fun latestCycle() = 0
        override suspend fun setActiveCycle(cycle: Int) {}
        override suspend fun startNewCycle() = 0
        override suspend fun readingSummary(cycle: Int) = error("not needed")
        override suspend fun bookReadProgress(cycle: Int) = error("not needed")
        override suspend fun chapterReadCounts(bookId: String, cycle: Int) = error("not needed")
        override suspend fun readingCalendarSkeleton() = error("not needed")
        override suspend fun dailyReadCounts(cycle: Int) = error("not needed")
        override suspend fun readHistoryForBook(bookId: String, cycle: Int) = error("not needed")
        override suspend fun readHistoryForChapter(bookId: String, chapter: Int, cycle: Int) = error("not needed")
        override suspend fun readHistoryForDay(dayTimestamp: Long, cycle: Int) = error("not needed")
        override suspend fun deleteReadHistoryEntries(ids: List<String>, cycle: Int) {
            deleteCalls.add(ids to cycle)
        }
        override fun dayTitle(dayTimestamp: Long) = error("not needed")
        override fun formatEntryDate(readAt: Long) = error("not needed")
        override fun formatEntryTime(readAt: Long) = error("not needed")
        override fun bookShortName(bookId: String) = error("not needed")
        override fun bookLongName(bookId: String) = error("not needed")
        override suspend fun memorizeSummary() = error("not needed")
        override suspend fun bookMemorizationProgress() = error("not needed")
        override suspend fun chapterMemorizationProgress(bookId: String) = error("not needed")
        override suspend fun dailyMemorizationCounts() = error("not needed")
        override suspend fun memorizedPassages() = error("not needed")
        override suspend fun memorizeTargets() = error("not needed")
        override suspend fun unmarkMemorized(startOrdinal: Int, endOrdinal: Int) = error("not needed")
        override suspend fun removeMemorizationTarget(id: String) = error("not needed")
    }

    private val service = RecordingReadingProgressService()
    private val testCycle = 7

    // Dispatchers.Unconfined (a real, production-usable dispatcher, not a kotlinx-coroutines-test
    // one): readHistoryApplyDeletes's coroutineScope.launch { ... } then runs eagerly on the
    // calling thread, so the fake's call is recorded before performClick returns -- no idling
    // needed, which matters because this test must never idle the looper (see class kdoc).
    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val applyDeletes = readHistoryApplyDeletes(testScope, service, testCycle)

    private var dismissed = false

    private fun show() {
        compose.setContent {
            var pendingDelete by remember { mutableStateOf(setOf<String>()) }

            fun close() {
                // AbReadHistorySheet.commit(): apply, THEN dismiss, on every dismissal path.
                applyDeletes(pendingDelete.toList())
                dismissed = true
            }

            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    AbReadHistorySheetContent(
                        title = "Reading history",
                        rows = rows,
                        pendingDelete = pendingDelete,
                        onTogglePending = { id ->
                            pendingDelete = if (id in pendingDelete) pendingDelete - id else pendingDelete + id
                        },
                        onClose = ::close,
                    )
                }
            }
        }
    }

    @Test fun closingWithNothingToggledNeverCallsTheDeleteCallback() {
        show()
        compose.onNodeWithContentDescription("Close").performClick()
        assertTrue(service.deleteCalls.isEmpty())
        assertTrue(dismissed)
    }

    @Test fun closingAfterTogglingOneRowCallsTheDeleteCallbackWithExactlyThatIdAndTheCycle() {
        show()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf(listOf("h1") to testCycle), service.deleteCalls)
        assertTrue(dismissed)
    }

    @Test fun closingAfterTogglingBothRowsCallsTheDeleteCallbackWithBothIds() {
        show()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick()
        compose.onNodeWithTag("ab-read-history-toggle-h2").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf(listOf("h1", "h2") to testCycle), service.deleteCalls)
    }

    @Test fun togglingThenUndoingBeforeClosingCallsTheDeleteCallbackZeroTimes() {
        // The classic dialog's own restore-before-dismiss case: tap the ✕, change your mind, tap
        // the same row's ↩ before dismissing -- nothing gets deleted.
        show()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick() // same tag, now the undo icon
        compose.onNodeWithContentDescription("Close").performClick()
        assertTrue(service.deleteCalls.isEmpty())
        assertTrue(dismissed)
    }
}
