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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.search.BibleSearchSettings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Bible search settings form, lifted out of `SearchScreen`'s settings sheet so the reading view
 * can show the same form in `SearchSettingsSheet`.
 *
 * Captured **directly**, never inside `SearchSettingsSheet`: forcing a `ModalBottomSheet` open in a
 * capture hangs Roborazzi, and it is the form — not the sheet chrome — that needs proving. The
 * wrapping `Column` stands in for the sheet's own `Column`, which is all the shell contributes to
 * layout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BibleSearchSettingsGoldenTest {
    private val translations = listOf("esv" to "ESV", "kjv" to "KJV")

    private fun settings(
        searchType: SearchType = SearchType.ALL_WORDS,
        bibleSection: SearchBibleSection = SearchBibleSection.ALL,
        availableTranslations: List<Pair<String, String>> = translations,
        selectedTranslationIds: List<String> = listOf("esv"),
        currentBookName: String = "",
    ): @Composable () -> Unit = {
        Column {
            BibleSearchSettings(
                searchType = searchType,
                bibleSection = bibleSection,
                availableTranslations = availableTranslations,
                selectedTranslationIds = selectedTranslationIds,
                currentBookName = currentBookName,
                onSearchType = {},
                onBibleSection = {},
                onTranslations = {},
            )
        }
    }

    @Test fun default() = captureMatrix("BibleSearchSettings", "default", content = settings())

    // F6-B4's second half: the CURRENT_BOOK option must show the open book's name, as classic did.
    // This is the only capture that proves a book name reaches the control.
    @Test
    fun currentBookNamed() =
        captureGolden(
            "BibleSearchSettings", "currentBookNamed", EDGE_MODE,
            content = settings(
                bibleSection = SearchBibleSection.CURRENT_BOOK,
                currentBookName = "Luke",
            ),
        )

    @Test
    @Config(sdk = [TEST_SDK], application = android.app.Application::class, qualifiers = "ar")
    fun default_rtl() = captureRtl("BibleSearchSettings", "default", content = settings())

    // A non-default selection in both segmented rows, so the selected-segment styling is proved for
    // a section other than "All" and a word mode other than "All words".
    @Test fun phraseAndOldTestament() =
        captureGolden(
            "BibleSearchSettings", "phraseAndOldTestament", EDGE_MODE,
            content = settings(
                searchType = SearchType.PHRASE,
                bibleSection = SearchBibleSection.OLD_TESTAMENT,
                selectedTranslationIds = listOf("esv", "kjv"),
            ),
        )

    // Nothing selected: the translations line falls back to the "all" label.
    @Test fun noTranslationsSelected() =
        captureGolden(
            "BibleSearchSettings", "noTranslationsSelected", EDGE_MODE,
            content = settings(selectedTranslationIds = emptyList()),
        )
}
