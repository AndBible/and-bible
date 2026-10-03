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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.sharedui.reading.WindowButton
import net.bible.sharedui.reading.WindowButtonMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Covers Batch 12b-followon-A Task 3's [WindowButton] — the shared button used by both the restore
 * rail (Plan A Task 5) and the floating per-pane ☰ button (Plan B Task 5), mirroring classic
 * `WindowButtonWidget` (`app/src/main/java/net/bible/android/view/util/widget/WindowButtonWidget.kt`).
 *
 * A single [Row] renders 10 representative states side by side so one capture shows every visual
 * distinction at once: active vs. inactive tint, the minimised look (dimmed + dashed outline), the
 * links glyph, a sync-group badge, the Pane-mode "☰" button (plain and pinned — fix-round-1: the pin
 * indicator is Pane-only, positioned under the sync badge, matching classic's `pinMode.visibility`
 * requiring `!isRestoreButton`), and classic's two-row rail geometry (page title over the
 * abbreviation, with and without a top label — neither draws a pin indicator even when pinned).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class WindowButtonGoldenTest {

    @Composable
    private fun states() {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // rail-active: active tint, doc-type leading icon (Bible).
            WindowButton(
                label = "K",
                isActive = true,
                isMinimised = false,
                isPinned = false,
                isLinks = false,
                syncGroup = 0,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
                leadingIcon = painterResource(R.drawable.ic_bible_24dp),
            )
            // rail-inactive: surfaceVariant tint, a different doc-type leading icon (Commentary).
            WindowButton(
                label = "C",
                isActive = false,
                isMinimised = false,
                isPinned = false,
                isLinks = false,
                syncGroup = 0,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
                leadingIcon = painterResource(R.drawable.ic_commentary),
            )
            // rail-minimised: dimmed (0.62 alpha) + dashed outline.
            WindowButton(
                label = "M",
                isActive = false,
                isMinimised = true,
                isPinned = false,
                isLinks = false,
                syncGroup = 0,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
            )
            // rail-pinned: fix-round-1 — isPinned=true renders NO indicator on a Rail button (classic's
            // pinMode is Pane-only, WindowButtonWidget.kt:85-96); this case exists to prove that
            // absence, so it must look identical to an unpinned rail button.
            WindowButton(
                label = "P",
                isActive = false,
                isMinimised = false,
                isPinned = true,
                isLinks = false,
                syncGroup = 0,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
            )
            // rail-links: link glyph badge (overrides any leadingIcon).
            WindowButton(
                label = "L",
                isActive = false,
                isMinimised = false,
                isPinned = false,
                isLinks = true,
                syncGroup = 0,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
            )
            // rail-synced group=2: sync-group badge showing "2".
            WindowButton(
                label = "S",
                isActive = false,
                isMinimised = false,
                isPinned = false,
                isLinks = false,
                syncGroup = 2,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
            )
            // pane("☰"): the floating per-pane button, active, plain state.
            WindowButton(
                label = "☰",
                isActive = true,
                isMinimised = false,
                isPinned = false,
                isLinks = false,
                syncGroup = 0,
                mode = WindowButtonMode.Pane,
                onClick = {},
                onLongPress = {},
            )
            // pane-pinned: fix-round-1 — the pin indicator's classic-accurate position, start edge
            // directly under the sync badge, and syncGroup=1 is set alongside it so the capture also
            // proves the two badges don't collide (window_button.xml:97-107 Top_toBottomOf=synchronize -- that layout was deleted by the Z-late epilogue; the citation is provenance).
            WindowButton(
                label = "☰",
                isActive = false,
                isMinimised = false,
                isPinned = true,
                isLinks = false,
                syncGroup = 1,
                mode = WindowButtonMode.Pane,
                onClick = {},
                onLongPress = {},
            )
            // rail-twoRow: classic's two-row rail geometry — page title above the abbreviation,
            // doc-type icon top-end, sync badge on the start edge. isPinned=false (fix-round-1: a
            // pinned RAIL case asserts nothing, since Rail never draws the indicator — see rail-pinned above).
            WindowButton(
                label = "KJV",
                isActive = false,
                isMinimised = false,
                isPinned = false,
                isLinks = false,
                syncGroup = 1,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
                leadingIcon = painterResource(R.drawable.ic_bible_24dp),
                topLabel = "Gen 1",
            )
            // rail-noTopLabel: no current document title -> single bottom-start label row.
            WindowButton(
                label = "ESV",
                isActive = false,
                isMinimised = false,
                isPinned = false,
                isLinks = false,
                syncGroup = 0,
                mode = WindowButtonMode.Rail,
                onClick = {},
                onLongPress = {},
                topLabel = null,
            )
        }
    }

    // 10 buttons at 40dp + 8dp spacing/padding (≈488dp) outgrew even the `land` qualifier (~470dp
    // on the default device) — the last case (`rail-noTopLabel`, "ESV") was clipped mid-button and
    // rendered "E…" instead of "ESV". `w720dp-land` forces a wider device width (Robolectric maps
    // `w<N>dp` onto the screen width) while keeping the landscape height, giving the row enough
    // room for all 10 buttons with margin, fully uncropped (same technique as
    // ReadingToolbarGoldenTest/ReadingViewScreenGoldenTest, widened further for this wider row).
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "w720dp-land")
    fun states_matrix() = captureMatrix("WindowButton", "states", content = { states() })
}
