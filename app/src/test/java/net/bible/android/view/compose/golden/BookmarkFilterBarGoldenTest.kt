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

import net.bible.android.TEST_SDK
import net.bible.sharedcore.bookmark.BookmarkFilterLabel
import net.bible.sharedui.bookmark.BookmarkFilterBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the [BookmarkFilterBar] chip row only -- NEVER the live [net.bible.sharedui.components.AbSearchableOptionSheet]
 * its tap opens: that wraps a ModalBottomSheet, which is a popup, and popups hang Roborazzi
 * captures. The sheet's content is [net.bible.sharedui.components.AbSearchableOptionSheetContent],
 * already captured in `DocumentFilterBarGoldenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BookmarkFilterBarGoldenTest {

    /** The bar itself. Only the light mode: the chip is a stock M3 AssistChip whose colours are
     *  already captured across all four modes by DocumentFilterBar's goldens, and the sheet
     *  content is AbSearchableOptionSheetContent, captured there too. A live ModalBottomSheet is
     *  never captured -- popups hang Roborazzi. */
    @Test fun chip() =
        captureGolden("BookmarkFilterBar", "chip", EDGE_MODE) {
            BookmarkFilterBar(
                filterLabels = listOf(BookmarkFilterLabel(0, "All"), BookmarkFilterLabel(2, "Grace")),
                selectedFilterIndex = 2,
                onSelectFilter = {},
            )
        }
}
