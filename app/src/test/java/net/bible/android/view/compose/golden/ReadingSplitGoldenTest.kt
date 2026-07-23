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

package net.bible.android.view.compose.golden

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.SplitContent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class ReadingSplitGoldenTest {
    private fun win(id: String, w: Float) = WindowSnapshot(
        id = id, state = WindowStateValue.VISIBLE, weight = w, isVisible = true,
        isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
    )

    private fun state(vararg windows: WindowSnapshot, reverse: Boolean = false) = WindowLayoutState(
        windows = windows.toList(), activeWindowId = windows.firstOrNull()?.id ?: "",
        maximizedWindowId = null, reverseSplitMode = reverse, restoreButtonsVisible = true,
    )

    // A distinct background color per window id makes the pane split geometry (which pane is
    // where, and how wide/tall each is relative to the others) visible in the captured PNG.
    private val pane: @Composable (String) -> Unit = { id ->
        val color = when (id) {
            "A" -> MaterialTheme.colorScheme.primaryContainer
            "B" -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Box(Modifier.fillMaxSize().background(color), Alignment.Center) { Text(id) }
    }

    // Calls SplitContent directly (not ReadingViewScreen) so these split-geometry goldens stay
    // toolbar-free — ReadingViewScreen now also draws a ReadingToolbar row above the split.
    private fun screen(layout: WindowLayoutState): @Composable () -> Unit = {
        SplitContent(layout, {}, { _, _, _, _ -> }, pane)
    }

    @Test fun singlePane() =
        captureMatrix("ReadingSplit", "single", content = screen(state(win("A", 1f))))

    // The default Robolectric golden viewport is portrait (320x470 — narrower than tall), so a
    // landscape qualifier is needed to exercise the `maxWidth > maxHeight` (side-by-side) branch of
    // SplitContent's orientation formula without `reverseSplitMode`; twoPaneVerticalViaReverse then
    // demonstrates that `reverseSplitMode` flips that same landscape viewport to stacked panes.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun twoPaneHorizontal() =
        captureGolden("ReadingSplit", "twoHorizontal", EDGE_MODE, content = screen(state(win("A", 2f), win("B", 1f))))

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun twoPaneVerticalViaReverse() =
        captureGolden(
            "ReadingSplit", "twoVertical", EDGE_MODE,
            content = screen(state(win("A", 1f), win("B", 1f), reverse = true)),
        )

    // Regression for the isVisible filter: layout.windows is the FULL snapshot (classic
    // sortedWindows), which can include minimised/closed windows alongside visible ones — SplitContent
    // must lay out only the visible subset. Middle window "B" is MINIMISED/isVisible=false, so the
    // capture must show exactly two side-by-side panes ("A" and "C"), never three, proving the
    // minimised window is excluded from layout rather than merely hidden/collapsed within it.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun `ReadingSplit_minimizedHidden`() =
        captureGolden(
            "ReadingSplit", "minimizedHidden", EDGE_MODE,
            content = screen(
                state(
                    win("A", 1f),
                    win("B", 1f).copy(state = WindowStateValue.MINIMISED, isVisible = false),
                    win("C", 1f),
                ),
            ),
        )
}
