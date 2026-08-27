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

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.BookmarkStyleSample
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The marker glyph must sit where the READER puts it: after the last element of the SELECTION for a
 * partial bookmark (`bibleview-js/src/composables/bookmarks.ts:768`), and after the whole text for a
 * whole-verse one (`:735`). Before round 15a both sample renderers excluded MARKER from the partial
 * split, so the glyph always landed after the whole sample — the reported bug. A golden shows this
 * too, but only this test states the rule in a form that fails loudly if someone re-adds the
 * exclusion.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarkStyleMarkerPositionTest {
    @get:Rule val compose = createComposeRule()

    private val sample = "For God so loved the world"

    private fun setSample(decoratePartially: Boolean) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    BookmarkStyleSample(
                        style = BookmarkDisplayStyle.MARKER,
                        colorArgb = 0xFF4CAF50.toInt(),
                        text = sample,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        decoratePartially = decoratePartially,
                    ) {
                        Box(Modifier.testTag("marker"))
                    }
                }
            }
        }
    }

    @Test fun partial_marker_sits_between_the_two_halves() {
        setSample(decoratePartially = true)
        val first = compose.onNodeWithText("For God so").getUnclippedBoundsInRoot()
        val marker = compose.onNodeWithTag("marker").getUnclippedBoundsInRoot()
        val rest = compose.onNodeWithText(" loved the world").getUnclippedBoundsInRoot()
        assertTrue(marker.left >= first.right, "marker starts after the selected half")
        assertTrue(marker.right <= rest.left, "marker ends before the unselected tail")
    }

    @Test fun full_marker_sits_after_the_whole_text() {
        setSample(decoratePartially = false)
        val whole = compose.onNodeWithText(sample).getUnclippedBoundsInRoot()
        val marker = compose.onNodeWithTag("marker").getUnclippedBoundsInRoot()
        assertTrue(marker.left >= whole.right, "marker starts after the whole sample")
    }
}
