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

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbSearchableOptionSheetContent
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
class AbSearchableOptionSheetContentTest {
    @get:Rule val compose = createComposeRule()

    private val options = listOf("Alpha", "Beta", "Gamma")

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    content()
                }
            }
        }
    }

    @Test fun no_search_field_is_rendered_when_placeholder_is_null() {
        setContent {
            AbSearchableOptionSheetContent(
                options = options, selected = "Beta", optionLabel = { it },
                onSelect = {}, searchPlaceholder = null,
            )
        }
        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("find me").assertDoesNotExist()
    }

    @Test fun search_field_filters_the_options_when_a_placeholder_is_given() {
        setContent {
            AbSearchableOptionSheetContent(
                options = options, selected = "Beta", optionLabel = { it },
                onSelect = {}, searchPlaceholder = "find me",
            )
        }
        compose.onNodeWithText("find me").performTextInput("Gam")
        compose.onNodeWithText("Gamma").assertIsDisplayed()
        compose.onNodeWithText("Alpha").assertDoesNotExist()
    }

    @Test fun selecting_an_option_reports_it() {
        var picked: String? = null
        setContent {
            AbSearchableOptionSheetContent(
                options = options, selected = "Beta", optionLabel = { it },
                onSelect = { picked = it }, searchPlaceholder = null,
            )
        }
        compose.onNodeWithText("Gamma").performClick()
        assertEquals("Gamma", picked)
    }

    @Test fun duplicate_labels_do_not_crash_the_list() {
        // Regression guard for the "Key ... was already used" crash: two options with the same
        // display name must render, which is only true while the LazyColumn uses positional keys.
        setContent {
            AbSearchableOptionSheetContent(
                options = listOf("Same", "Same"), selected = "Same", optionLabel = { it },
                onSelect = {}, searchPlaceholder = null,
            )
        }
        compose.onAllNodesWithText("Same").assertCountEquals(2)
    }
}
