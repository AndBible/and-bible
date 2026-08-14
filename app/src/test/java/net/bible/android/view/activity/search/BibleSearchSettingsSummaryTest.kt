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

package net.bible.android.view.activity.search

import androidx.test.core.app.ApplicationProvider
import net.bible.android.TEST_SDK
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchType
import net.bible.sharedui.search.bibleSearchSettingsSummary
import net.bible.sharedui.strings.AndroidStrings
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A/B F6-B4, second half. Classic overwrote the "Current book" radio button's text with the OPEN
 * BOOK'S NAME at runtime (`Search.kt:168-179`); the Compose form showed the static label, because
 * `currentBookName` reached the Lucene query and never `:sharedUi`. This pins the resolution rule for
 * the summary; the dropdown's own labels are proved by the new golden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class BibleSearchSettingsSummaryTest {
    private val strings = AndroidStrings(ApplicationProvider.getApplicationContext())
    private val translations = listOf("esv" to "ESV")

    private fun summary(
        bibleSection: SearchBibleSection,
        currentBookName: String,
    ) = bibleSearchSettingsSummary(
        strings = strings,
        searchType = SearchType.ALL_WORDS,
        bibleSection = bibleSection,
        availableTranslations = translations,
        selectedTranslationIds = listOf("esv"),
        currentBookName = currentBookName,
    )

    @Test
    fun theCurrentBookSectionIsNamedAfterTheOpenBook() {
        val s = summary(SearchBibleSection.CURRENT_BOOK, "Luke")
        assertThat(s, containsString("Luke"))
        assertThat(s, not(containsString(strings.searchCurrentBook)))
    }

    // `SearchScreen`'s `currentBookName` parameter is defaulted to "", so any caller that omits it
    // reaches here with no book to name. The static label must remain the fallback rather than
    // leaving the row blank.
    @Test
    fun aBlankBookNameFallsBackToTheStaticLabel() {
        assertThat(summary(SearchBibleSection.CURRENT_BOOK, ""), containsString(strings.searchCurrentBook))
    }

    // The other three sections must be untouched by the book name.
    @Test
    fun theOtherSectionsIgnoreTheBookName() {
        assertThat(summary(SearchBibleSection.ALL, "Luke"), containsString(strings.searchAllBible))
        assertThat(summary(SearchBibleSection.OLD_TESTAMENT, "Luke"), containsString(strings.searchOldTestament))
        assertThat(summary(SearchBibleSection.NEW_TESTAMENT, "Luke"), containsString(strings.searchNewTestament))
    }
}
