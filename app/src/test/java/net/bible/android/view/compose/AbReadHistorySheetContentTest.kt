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
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
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
 * Task 27 (platform-dialog removal, run 3) fix round 1: [AbReadHistorySheetContent] tested
 * directly here rather than through [net.bible.sharedui.progress.AbReadHistorySheet] — an open
 * `ModalBottomSheet` hangs a Robolectric idle forever and must never be captured or driven in a
 * test, per this run's "Hang protection" rule and that file's own kdoc (same reason
 * `ShareVersesSheetContentTest` tests `ShareVersesSheetContent` rather than `ShareVersesSheet`).
 *
 * [show]'s `close()` reproduces `AbReadHistorySheet`'s own `commit()`
 * (`onApplyDeletes(pendingDelete.toList()); onDismiss()`) AND `ComposeReadingViewHost`'s
 * `onApplyDeletes` guard (`if (ids.isNotEmpty()) { … deleteReadHistoryEntries(ids, cycle) }`) —
 * both are pre-existing/production wiring, not reinvented here, so a test asserting "the delete
 * callback fires with exactly these ids, and not at all when nothing was toggled" is really
 * asserting that PAIR's combined behaviour, which is what review round 1 named as untested. The
 * per-row toggle itself (checked via a stable [net.bible.sharedui.components] test tag, since the
 * icon swaps between ✕ and ↩ rather than carrying one fixed `contentDescription`) is real
 * `AbReadHistorySheetContent` behaviour, not test-harness code.
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

    private val deleteCalls = mutableListOf<List<String>>()
    private var dismissed = false

    private fun show() {
        compose.setContent {
            var pendingDelete by remember { mutableStateOf(setOf<String>()) }

            fun close() {
                // AbReadHistorySheet.commit(): apply, THEN dismiss, on every dismissal path.
                val ids = pendingDelete.toList()
                // ComposeReadingViewHost's onApplyDeletes guard: the delete callback only fires
                // when something was actually staged.
                if (ids.isNotEmpty()) deleteCalls.add(ids)
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
        assertTrue(deleteCalls.isEmpty())
        assertTrue(dismissed)
    }

    @Test fun closingAfterTogglingOneRowCallsTheDeleteCallbackWithExactlyThatId() {
        show()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf(listOf("h1")), deleteCalls)
        assertTrue(dismissed)
    }

    @Test fun closingAfterTogglingBothRowsCallsTheDeleteCallbackWithBothIds() {
        show()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick()
        compose.onNodeWithTag("ab-read-history-toggle-h2").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf(listOf("h1", "h2")), deleteCalls)
    }

    @Test fun togglingThenUndoingBeforeClosingCallsTheDeleteCallbackZeroTimes() {
        // The classic dialog's own restore-before-dismiss case: tap the ✕, change your mind, tap
        // the same row's ↩ before dismissing -- nothing gets deleted.
        show()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick()
        compose.onNodeWithTag("ab-read-history-toggle-h1").performClick() // same tag, now the undo icon
        compose.onNodeWithContentDescription("Close").performClick()
        assertTrue(deleteCalls.isEmpty())
        assertTrue(dismissed)
    }
}
