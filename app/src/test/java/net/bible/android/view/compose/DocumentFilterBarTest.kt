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

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.LangOption
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.navigation.DocumentFilterBar
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

// Mandatory wrapping (see GoldenHarness.kt:72-105 / AbSearchableOptionSheetContentTest.kt):
// DocumentFilterBar reads LocalStrings.current and LocalCategoryIcon.current, both
// staticCompositionLocalOf with no default, so a bare setContent crashes at render time.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class DocumentFilterBarTest {
    @get:Rule val compose = createComposeRule()

    private val finnish = LangOption("fi", "Finnish", "fi")
    private val english = LangOption("en", "English", "en")
    private val typeFilters = listOf(
        DocTypeFilter.ALL to "All types",
        DocTypeFilter.BIBLE to "Bible",
        DocTypeFilter.MAPS to "Map",
    )

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun bar(
        selectedLanguage: LangOption? = finnish,
        selectedType: DocTypeFilter = DocTypeFilter.ALL,
        onLanguage: (LangOption?) -> Unit = {},
        onType: (DocTypeFilter) -> Unit = {},
    ) = DocumentFilterBar(
        languages = listOf(english, finnish),
        selectedLanguage = selectedLanguage,
        onLanguageChange = onLanguage,
        typeFilters = typeFilters,
        selectedTypeFilter = selectedType,
        onTypeFilterChange = onType,
        resultCount = "12 documents",
    )

    @Test fun both_chips_show_the_current_value_and_the_count_is_visible() {
        setContent { bar() }
        compose.onNodeWithText("Finnish").assertIsDisplayed()
        compose.onNodeWithText("All types").assertIsDisplayed()
        compose.onNodeWithText("12 documents").assertIsDisplayed()
    }

    @Test fun the_language_chip_falls_back_to_the_all_label_when_no_language_is_selected() {
        setContent { bar(selectedLanguage = null) }
        // strings.all, i.e. R.string.all — resolve it the same way the bar does rather than
        // hardcoding the English text, so a copy change does not break this test.
        compose.onNodeWithText(
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getString(net.bible.android.activity.R.string.all),
        ).assertIsDisplayed()
    }

    @Test fun tapping_the_type_chip_opens_the_type_sheet_and_a_choice_is_reported() {
        var picked: DocTypeFilter? = null
        setContent { bar(onType = { picked = it }) }
        compose.onNodeWithText("All types").performClick()
        compose.onNodeWithText("Map").performClick()
        assertEquals(DocTypeFilter.MAPS, picked)
    }

    @Test fun tapping_the_language_chip_opens_the_language_sheet_and_a_choice_is_reported() {
        var picked: LangOption? = finnish
        setContent { bar(onLanguage = { picked = it }) }
        compose.onNodeWithText("Finnish").performClick()
        compose.onNodeWithText("English").performClick()
        assertEquals(english, picked)
    }
}
