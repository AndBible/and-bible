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

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.EpubResultRow
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedui.search.EpubSearchSettings
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.search.epubResultRows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the reading view's search sheet holds for an EPUB: `SearchSheetContent` with an EMPTY header
 * actions slot (an EPUB search targets one document, so there is no translation chip, no scripture
 * toggle and no open-in-window) and EPUB result rows in its list.
 *
 * Captured directly, never inside a `BottomSheetScaffold` — same rule as `SearchSheetGoldenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class EpubSearchSheetGoldenTest {

    private fun preview(before: String, hit: String, after: String) = StyledText(
        listOf(StyledRun(before), StyledRun(hit, highlight = true), StyledRun(after)),
    )

    private val rows = listOf(
        EpubResultRow("Book:frag1", 11, "Chapter 1", preview("It was the age of ", "wisdom", ", it was…")),
        EpubResultRow("Book:frag2", 12, "Chapter 4", preview("…a matter of ", "wisdom", " and patience.")),
        EpubResultRow("Book:frag3", 13, "Appendix", preview("On ", "wisdom", " literature.")),
    )

    private fun sheet(
        countLabel: String = "3 results in EPUB",
        loading: Boolean = false,
        error: String? = null,
        empty: Boolean = false,
        rows: List<EpubResultRow> = this.rows,
    ): @Composable () -> Unit = {
        SearchSheetContent(
            countLabel = countLabel,
            loading = loading,
            error = error,
            empty = empty,
            listState = rememberLazyListState(),
            onDismissError = {},
            actions = {},
        ) {
            epubResultRows(rows = rows, onSelect = { _, _ -> })
        }
    }

    @Test fun results() = captureMatrix("EpubSearchSheet", "results", heightDp = 400, content = sheet())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun results_rtl() = captureRtl("EpubSearchSheet", "results", heightDp = 400, content = sheet())

    @Test fun loading() =
        captureGolden("EpubSearchSheet", "loading", EDGE_MODE, content = sheet(countLabel = "", loading = true))

    @Test fun empty() =
        captureGolden("EpubSearchSheet", "empty", EDGE_MODE, content = sheet(empty = true, rows = emptyList()))

    /**
     * The EPUB error dialog — the batch's one genuinely NEW EPUB rendering. `EpubSearchResultsController`
     * carries only a Boolean, so `ComposeReadingViewHost` maps it to `R.string.error_executing_search`
     * itself; the standalone Activity toasted and finished, so nothing has ever rendered an EPUB search
     * failure over a sheet that stays open. Mirrors `SearchSheetGoldenTest.error`.
     */
    @Test fun error() =
        captureGolden("EpubSearchSheet", "error", EDGE_MODE, content = sheet(error = "Error executing search"))

    @Test fun settings() = captureMatrix("EpubSearchSheet", "settings", content = {
        EpubSearchSettings(mode = EpubSearchMode.PHRASE, onMode = {})
    })
}
