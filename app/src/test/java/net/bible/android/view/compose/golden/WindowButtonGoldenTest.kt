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
 * A single [Row] renders 7 representative states side by side so one capture shows every visual
 * distinction at once: active vs. inactive tint, the minimised look (dimmed + dashed outline), the
 * pin dot, the links glyph, a sync-group badge, and the Pane-mode "☰" button.
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
            // rail-pinned: pin dot badge.
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
        }
    }

    // 7 buttons at 40dp + 8dp spacing/padding need more width than the default portrait viewport
    // (320dp) comfortably provides, so this is captured in `land` (same technique as
    // ReadingToolbarGoldenTest/ReadingViewScreenGoldenTest) to keep every button fully visible,
    // uncropped, in one row.
    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "land")
    fun states_matrix() = captureMatrix("WindowButton", "states", content = { states() })
}
