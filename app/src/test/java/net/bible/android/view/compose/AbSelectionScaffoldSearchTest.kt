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
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbSelectionScaffold
import net.bible.sharedui.components.AbTopBarSearchCallbacks
import net.bible.sharedui.components.AbTopBarSearchState
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Mandatory wrapping (see GoldenHarness.kt:72-105 / AbSearchableOptionSheetContentTest.kt):
// AbSelectionScaffold's normal-mode bar reads LocalStrings.current, and its search-mode bar (via
// AbTopAppBar) does too, both staticCompositionLocalOf with no default, so a bare setContent
// crashes at render time.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbSelectionScaffoldSearchTest {
    @get:Rule val rule = createComposeRule()

    @Composable
    private fun Wrapped(content: @Composable () -> Unit) {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                content()
            }
        }
    }

    @Test
    fun selectionModeWinsOverSearchMode() {
        rule.setContent {
            Wrapped {
                AbSelectionScaffold(
                    title = "Documents",
                    selectionMode = true,
                    selectedCount = 3,
                    onNavigateUp = {},
                    onExitSelection = {},
                    search = AbTopBarSearchState(query = "Genesis"),
                    searchCallbacks = AbTopBarSearchCallbacks(
                        onQueryChange = {}, onClose = {}, onImeRequestHandled = {},
                    ),
                ) {}
            }
        }
        rule.onNodeWithText("3").assertIsDisplayed()
        rule.onNodeWithText("Genesis").assertDoesNotExist()
    }

    @Test
    fun searchModeReplacesTheNormalBarWhenNotSelecting() {
        rule.setContent {
            Wrapped {
                AbSelectionScaffold(
                    title = "Documents",
                    selectionMode = false,
                    selectedCount = 0,
                    onNavigateUp = {},
                    onExitSelection = {},
                    search = AbTopBarSearchState(query = "Genesis"),
                    searchCallbacks = AbTopBarSearchCallbacks(
                        onQueryChange = {}, onClose = {}, onImeRequestHandled = {},
                    ),
                ) {}
            }
        }
        rule.onNodeWithText("Genesis").assertIsDisplayed()
        rule.onNodeWithText("Documents").assertDoesNotExist()
    }

    @Test
    fun theNormalBarShowsWhenOnlyOneSearchParameterIsGiven() {
        rule.setContent {
            Wrapped {
                AbSelectionScaffold(
                    title = "Documents",
                    selectionMode = false,
                    selectedCount = 0,
                    onNavigateUp = {},
                    onExitSelection = {},
                    search = AbTopBarSearchState(query = "Genesis"),
                    searchCallbacks = null,
                ) {}
            }
        }
        rule.onNodeWithText("Documents").assertIsDisplayed()
    }
}
