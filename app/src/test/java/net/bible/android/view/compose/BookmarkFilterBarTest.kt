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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.bookmark.BookmarkFilterBar
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarkFilterBarTest {
    @get:Rule val compose = createComposeRule()

    private val labels = listOf(
        BookmarkFilterLabel(0, "All"),
        BookmarkFilterLabel(1, "Unlabeled"),
        BookmarkFilterLabel(2, "Grace"),
    )

    private fun setBar(selectedIndex: Int = 0, onSelect: (Int) -> Unit = {}) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    BookmarkFilterBar(
                        filterLabels = labels,
                        selectedFilterIndex = selectedIndex,
                        onSelectFilter = onSelect,
                    )
                }
            }
        }
    }

    @Test fun the_chip_shows_the_current_filter() {
        setBar(selectedIndex = 2)
        compose.onNodeWithText("Grace").assertIsDisplayed()
    }

    @Test fun tapping_the_chip_opens_the_sheet_and_a_choice_is_reported() {
        var picked = -1
        setBar(selectedIndex = 0, onSelect = { picked = it })
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Unlabeled").performClick()
        assertEquals(1, picked)
    }

    @Test fun an_out_of_range_index_falls_back_to_the_first_label_instead_of_rendering_blank() {
        setBar(selectedIndex = 99)
        compose.onNodeWithText("All").assertIsDisplayed()
    }
}
