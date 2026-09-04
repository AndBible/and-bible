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
import net.bible.sharedui.components.AbFilterChip
import net.bible.sharedui.components.AbFilterChipBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bar's two layout branches. Goldens are always 320dp wide, so "short labels" is the one-row
 * branch and "long labels" is the branch where the result count drops below the chips.
 *
 * Ask what these would look like if the code were WRONG: a broken one-row branch would show the
 * count overlapping the Tune button or a truncated chip; a broken two-row branch would show the
 * count clipped off the right edge instead of on its own line.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class FilterChipBarGoldenTest {
    private fun chip(label: String, weight: Float) =
        AbFilterChip(label = label, contentDescription = null, leadingIcon = null,
            shortfallWeight = weight, onClick = {})

    @Test fun oneRow() {
        captureGolden("FilterChipBar", "oneRow", EDGE_MODE) {
            AbFilterChipBar(
                chips = listOf(chip("All", 2f / 3f), chip("Bible", 1f / 3f)),
                resultCount = "12",
                onMoreFilters = {},
                moreFiltersActive = false,
            )
        }
    }

    @Test fun twoRowsWithActiveDot() {
        captureGolden("FilterChipBar", "twoRows", EDGE_MODE) {
            AbFilterChipBar(
                chips = listOf(chip("Portuguese (Brazil)", 2f / 3f), chip("General books", 1f / 3f)),
                resultCount = "1234 documents",
                onMoreFilters = {},
                moreFiltersActive = true,
            )
        }
    }
}
