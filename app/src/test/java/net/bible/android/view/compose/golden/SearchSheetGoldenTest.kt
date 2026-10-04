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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.BibleOption
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import net.bible.sharedcore.search.SwordResultRow
import net.bible.sharedcore.search.TranslationMatchVd
import net.bible.sharedui.search.BibleResultsActions
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.search.bibleResultRows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the reading view's search sheet holds: `SearchSheetContent` with the Bible actions in its
 * header slot and Bible result rows in its list.
 *
 * Captured **directly**, never inside a `BottomSheetScaffold`: the sheet chrome (handle, peek, drag)
 * belongs to the host and forcing a sheet open in a capture is unreliable. Neither the translation
 * chooser nor the overflow menu is opened here either — a popup in a capture hangs Roborazzi, and
 * `SearchResultsGoldenTest.translation_chooser` already covers the chooser.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchSheetGoldenTest {

    /** A preview with a highlighted query term in the middle, so highlight styling shows. */
    private fun preview(before: String, hit: String, after: String) = StyledText(
        listOf(
            StyledRun(before),
            StyledRun(hit, highlight = true),
            StyledRun(after),
        ),
    )

    private val rows = listOf(
        SwordResultRow(
            referenceName = "John 3:16",
            matches = listOf(
                TranslationMatchVd("esv", "ESV", preview("For God so ", "loved", " the world…")),
                TranslationMatchVd("kjv", "KJV", preview("For God so ", "loved", " the world, that…")),
            ),
            primaryPreview = preview("For God so ", "loved", " the world…"),
        ),
        SwordResultRow(
            referenceName = "1 Corinthians 13:4",
            matches = listOf(
                TranslationMatchVd("esv", "ESV", preview("Love is patient and ", "kind", "…")),
                TranslationMatchVd("kjv", "KJV", preview("Charity suffereth long, and is ", "kind", "…")),
            ),
            primaryPreview = preview("Love is patient and ", "kind", "…"),
        ),
        SwordResultRow(
            referenceName = "Psalm 23:1",
            matches = listOf(
                TranslationMatchVd("esv", "ESV", preview("The LORD is my ", "shepherd", "; I shall not want.")),
            ),
            primaryPreview = preview("The LORD is my ", "shepherd", "; I shall not want."),
        ),
    )

    private val candidates = listOf(
        BibleOption("KJV", "KJV", hasStrongs = true),
        BibleOption("ESV", "ESV", hasStrongs = false),
        BibleOption("NIV", "NIV", hasStrongs = false),
    )

    private fun sheet(
        countLabel: String = "3 results",
        loading: Boolean = false,
        error: String? = null,
        empty: Boolean = false,
        rows: List<SwordResultRow> = this.rows,
    ): @Composable () -> Unit = {
        val expanded = remember { mutableStateMapOf<String, Boolean>() }
        SearchSheetContent(
            countLabel = countLabel,
            loading = loading,
            error = error,
            empty = empty,
            listState = rememberLazyListState(),
            onDismissError = {},
            actions = {
                BibleResultsActions(
                    candidates = candidates,
                    selectedIds = listOf("KJV", "ESV"),
                    selectedAbbreviations = "KJV, ESV",
                    scriptureToggleVisible = true,
                    scriptureShown = true,
                    onToggleScripture = {},
                    onOpenInWindow = {},
                    onSelectTranslations = {},
                )
            },
        ) {
            bibleResultRows(
                rows = rows,
                expanded = expanded,
                labelSingleMatchTranslation = true,
                onSelect = { _, _ -> },
            )
        }
    }

    // heightDp = 400 so all three rows render instead of clipping at the default viewport: the point
    // of this golden is the header + list together.
    @Test fun results() = captureMatrix("SearchSheet", "results", heightDp = 400, content = sheet())

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun results_rtl() = captureRtl("SearchSheet", "results", heightDp = 400, content = sheet())

    @Test fun loading() =
        captureGolden("SearchSheet", "loading", EDGE_MODE, content = sheet(countLabel = "", loading = true))

    @Test fun empty() =
        captureGolden("SearchSheet", "empty", EDGE_MODE, content = sheet(countLabel = "0 results", empty = true, rows = emptyList()))

    // The error dialog replaces the old activity's toast-then-finish: the sheet stays open behind it.
    @Test fun error() =
        captureGolden("SearchSheet", "error", EDGE_MODE, content = sheet(error = "Search failed: index is corrupt"))
}
