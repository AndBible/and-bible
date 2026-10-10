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

import net.bible.sharedui.theme.isPureMonochrome
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        val color = if (isPureMonochrome()) MaterialTheme.colorScheme.background else when (id) {
            "A" -> MaterialTheme.colorScheme.primaryContainer
            "B" -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
        Box(Modifier.fillMaxSize().background(color), Alignment.Center) { Text(id) }
    }

    // Calls SplitContent directly (not ReadingViewScreen) so these split-geometry goldens stay
    // toolbar-free — ReadingViewScreen now also draws a ReadingToolbar row above the split.
    private fun screen(
        layout: WindowLayoutState,
        paneOverlay: (@Composable BoxScope.(String) -> Unit)? = null,
    ): @Composable () -> Unit = {
        SplitContent(layout, {}, { _, _, _, _ -> }, pane, paneOverlay = paneOverlay)
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

    // Covers the paneOverlay slot (Batch 12b-followon-B Task 2): the floating ☰ window button
    // (Task 5, host-composed) anchors here via Modifier.align — this stub proves an overlay drawn
    // per-pane sits top-end over BOTH panes without disturbing the split geometry underneath.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withOverlay() =
        captureGolden(
            "ReadingSplit", "withOverlay", EDGE_MODE,
            content = screen(
                state(win("A", 2f), win("B", 1f)),
                paneOverlay = { _ ->
                    Box(
                        Modifier.align(Alignment.TopEnd).size(40.dp).background(MaterialTheme.colorScheme.tertiary),
                    ) { Text("☰") }
                },
            ),
        )

    // A/B batch 4a whole-batch review I2: golden evidence for F5 (the pane paints the reader
    // background colour, closing the new-window white flash) -- no golden covered `paneBackground`
    // before this, which is how C1 (paneBackgroundArgbFor returning null for exactly the brand-new
    // window F5 was written to fix) slipped past task review. `pane` here is an EMPTY Box (no fill
    // of its own), so a painted colour in the capture can only come from SplitContent's own
    // `Modifier.background(paneBackground(w.id))` -- proving the background is actually applied to
    // the pane, not merely computed and discarded. Two windows with two distinct, clearly
    // identifiable colours (magenta/cyan) also prove `paneBackground` is looked up per-window-id,
    // not a single colour smeared across the whole split.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withPaneBackground() =
        captureGolden(
            "ReadingSplit", "withPaneBackground", EDGE_MODE,
            content = seededPaneContent(),
        )

    private fun seededPaneContent(): @androidx.compose.runtime.Composable () -> Unit = {
                SplitContent(
                    state(win("A", 1f), win("B", 1f)),
                    {},
                    { _, _, _, _ -> },
                    pane = {},
                    paneBackground = { id -> if (id == "A") Color(0xFFFF00FF) else Color(0xFF00FFFF) },
                )
            }

    @Test
    fun single_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingSplit", "single", mode, content = screen(state(win("A", 1f)))) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun twoHorizontal_mono() {
        MONO_MODES.forEach { mode -> captureGolden("ReadingSplit", "twoHorizontal", mode, content = screen(state(win("A", 2f), win("B", 1f)))) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun twoVertical_mono() {
        MONO_MODES.forEach { mode -> captureGolden(
            "ReadingSplit", "twoVertical", mode,
            content = screen(state(win("A", 1f), win("B", 1f), reverse = true)),
        ) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun minimizedHidden_mono() {
        MONO_MODES.forEach { mode -> captureGolden(
            "ReadingSplit", "minimizedHidden", mode,
            content = screen(
                state(
                    win("A", 1f),
                    win("B", 1f).copy(state = WindowStateValue.MINIMISED, isVisible = false),
                    win("C", 1f),
                ),
            ),
        ) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withOverlay_mono() {
        MONO_MODES.forEach { mode -> captureGolden(
            "ReadingSplit", "withOverlay", mode,
            content = screen(
                state(win("A", 2f), win("B", 1f)),
                paneOverlay = { _ ->
                    Box(
                        Modifier.align(Alignment.TopEnd).size(40.dp).background(MaterialTheme.colorScheme.tertiary),
                    ) { Text("☰") }
                },
            ),
        ) }
    }

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun withPaneBackground_mono() {
        MONO_MODES.forEach { mode -> captureGolden(
            "ReadingSplit", "withPaneBackground", mode,
            content = seededPaneContent(),
        ) }
    }
}
