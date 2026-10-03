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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.SplitContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F64's structural half, proven BEHAVIOURALLY rather than textually. `SplitContentOneCallSiteGuardTest`
 * counts occurrences of `pane(w.id)`/`key(w.id)` in the SOURCE TEXT, which stays at exactly one each
 * even when that one call site sits behind an `if (isHorizontal) Row { .. } else Column { .. }` --
 * two call POSITIONS in the composition tree, invisible to a source-text count. Composing the SAME
 * text from two different structural parents still disposes-then-recreates whatever is inside: on a
 * real device that is the panes' `AndroidView`-hosted `BibleView`, which `BibleViewFactory` hands
 * back as the SAME cached instance -- so the device symptom is a detach-then-reattach of one
 * `BibleView`, which drops its window token and hides the IME (fix round 1 review finding).
 *
 * Flips orientation via `reverseSplitMode`, not window size: `splitIsHorizontal` (`SplitOrientation.kt`)
 * computes `(window is wider than tall) != reverseSplitMode`, so toggling
 * `reverseSplitMode` flips `isHorizontal` while every other input -- including the Robolectric
 * viewport's fixed width/height -- stays constant. This is the same lever
 * `ReadingSplitGoldenTest.twoPaneVerticalViaReverse` already uses to reach the vertical branch, so it
 * is a real, already-relied-upon input, not a test-only backdoor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
class SplitContentOrientationFlipMountGuardTest {
    @get:Rule val compose = createComposeRule()

    private fun win(id: String) = WindowSnapshot(
        id = id, state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
        isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
    )

    private fun state(reverse: Boolean) = WindowLayoutState(
        windows = listOf(win("A"), win("B")), activeWindowId = "A",
        maximizedWindowId = null, reverseSplitMode = reverse, restoreButtonsVisible = true,
    )

    /** Per-window-id create/dispose counts and the last `remember`ed identity, filled by [trackedPane]. */
    private class PaneLifecycle {
        val created = mutableMapOf<String, Int>()
        val disposed = mutableMapOf<String, Int>()
        val identity = mutableMapOf<String, Any>()
    }

    @Test
    fun anOrientationFlipMovesEveryPaneInsteadOfRecreatingIt() {
        val lifecycle = PaneLifecycle()
        val reverse = mutableStateOf(false)

        // A fresh Any() is only produced when THIS composable slot is (re-)entered from scratch --
        // if the pane subtree is disposed and a brand new one composed in the other branch, `marker`
        // changes and `onDispose` fires for the old one; if it is truly MOVED, neither happens.
        val trackedPane: @Composable (String) -> Unit = { id ->
            val marker = remember { Any() }
            DisposableEffect(Unit) {
                lifecycle.created[id] = (lifecycle.created[id] ?: 0) + 1
                lifecycle.identity[id] = marker
                onDispose { lifecycle.disposed[id] = (lifecycle.disposed[id] ?: 0) + 1 }
            }
        }

        compose.setContent {
            SplitContent(
                layout = state(reverse.value),
                onWindowActivated = {},
                onSeparatorCommitted = { _, _, _, _ -> },
                pane = trackedPane,
            )
        }
        compose.waitForIdle()

        assertEquals("pane A must mount exactly once initially", 1, lifecycle.created["A"])
        assertEquals("pane B must mount exactly once initially", 1, lifecycle.created["B"])
        assertEquals("no pane should have been disposed on initial composition", 0, lifecycle.disposed.values.sum())
        val beforeA = lifecycle.identity.getValue("A")
        val beforeB = lifecycle.identity.getValue("B")

        reverse.value = true
        compose.waitForIdle()

        assertEquals(
            "the orientation flip must not dispose pane A -- a dispose here is exactly the detach " +
                "that drops the WebView's window token and hides the IME (F64)",
            0,
            lifecycle.disposed["A"] ?: 0,
        )
        assertEquals(
            "the orientation flip must not dispose pane B, for the same reason",
            0,
            lifecycle.disposed["B"] ?: 0,
        )
        assertEquals("pane A must not be recomposed from scratch a second time", 1, lifecycle.created["A"])
        assertEquals("pane B must not be recomposed from scratch a second time", 1, lifecycle.created["B"])
        assertEquals("pane A must keep its identity across the flip", beforeA, lifecycle.identity.getValue("A"))
        assertEquals("pane B must keep its identity across the flip", beforeB, lifecycle.identity.getValue("B"))
    }
}
