/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.search.SearchIndexPanel
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The create-index prompt explains the engine change only for a document that still has a legacy index (K6 task 16). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SearchIndexPanelEngineChangedTest {
    @get:Rule val compose = createComposeRule()

    private fun show(engineChanged: Boolean) = compose.setContent {
        ProvideAppLocals {
            AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                SearchIndexPanel(
                    documentName = "ESV", isRebuild = false, indexing = false, jobs = emptyList(), error = null,
                    onCreate = {}, onCancel = {}, onDismissError = {}, engineChanged = engineChanged,
                )
            }
        }
    }

    @Test fun engineChangedAppendsTheExplanation() {
        show(engineChanged = true)
        compose.onNodeWithText("The search engine has been updated.", substring = true).assertExists()
    }

    @Test fun noExplanationWithoutALegacyIndex() {
        show(engineChanged = false)
        compose.onNodeWithText("The search engine has been updated.", substring = true).assertDoesNotExist()
    }
}
