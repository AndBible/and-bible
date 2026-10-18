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
package net.bible.sharedui.reading.host.slots

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import net.bible.sharedcore.search.SearchDocumentCategory
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings

/**
 * The search-settings slot's two branches (EPUB word-mode row vs the Bible form). The dismiss order
 * (sheet closed, then `onClosed`) is not driven here: the modal popup's dismiss request cannot be
 * triggered from a desktop compose test (Escape and scrim clicks do not reach it).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class SearchSettingsSlotTest {
    private val strings = iosStrings("en")

    private fun epubInfo(isEpub: Boolean) =
        SearchDocumentInfo("DOC", SearchDocumentCategory.BIBLE, isEpub = isEpub, indexDone = true)

    private fun androidx.compose.ui.test.ComposeUiTest.show(f: SearchFixture, onClosed: () -> Unit = {}) {
        f.state.searchSettingsOpen.value = true
        setContent {
            CompositionLocalProvider(LocalStrings provides strings) {
                MaterialTheme {
                    SearchSettingsSlotContent(
                        f.state, f.state::searchSettingsForEpub, f.state::currentBookName, onClosed,
                    )
                }
            }
        }
    }

    @Test
    fun anEpubTargetShowsTheWordModeRowAndNotTheBibleForm() = runComposeUiTest {
        val f = SearchFixture().apply { platform.info = epubInfo(true) }
        show(f)
        onAllNodesWithText(strings.searchTypePrompt).assertCountEquals(1)
        onAllNodesWithText(strings.searchWhere).assertCountEquals(0)
    }

    @Test
    fun aBibleTargetShowsTheBibleFormAndNotTheEpubWordModeRow() = runComposeUiTest {
        val f = SearchFixture().apply { platform.info = epubInfo(false) }
        show(f)
        onAllNodesWithText(strings.searchWhere).assertCountEquals(1)
        onAllNodesWithText(strings.searchTypePrompt).assertCountEquals(0)
    }
}
