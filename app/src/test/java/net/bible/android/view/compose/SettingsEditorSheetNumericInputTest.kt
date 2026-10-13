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

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.settings.TextInputRowEditorContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Pins the Apply enabled state and the applied value of a text-input row's editor content, driven
 * directly through [TextInputRowEditorContent] without a modal sheet around it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SettingsEditorSheetNumericInputTest {
    @get:Rule val compose = createComposeRule()

    private val applied = mutableListOf<Pair<String, String>>()

    private fun showContent(row: SettingsItem.TextInputRow) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    Column {
                        TextInputRowEditorContent(
                            row = row,
                            onApply = { applied += row.key to it },
                            onCancel = {},
                        )
                    }
                }
            }
        }
    }

    private fun limitRow(value: String) = SettingsItem.TextInputRow(
        key = "agent_max_iterations", title = "Max iterations", value = value,
        numeric = true, nonNegativeIntOnly = true,
    )

    /** CR-9: an empty limit cannot be applied, so the sheet never "accepts" a value nobody saves. */
    @Test fun applyIsDisabledForAnEmptyLimit() {
        showContent(limitRow("10"))
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
    }

    @Test fun applyIsDisabledForANegativeLimit() {
        showContent(limitRow("10"))
        compose.onNode(hasSetTextAction()).performTextReplacement("-1")
        compose.onNodeWithText("Apply").assertIsNotEnabled()
    }

    /** Review focus: surrounding spaces are not an error; the trimmed number is what is applied. */
    @Test fun aPaddedValidLimitIsAppliedTrimmed() {
        showContent(limitRow("10"))
        compose.onNode(hasSetTextAction()).performTextReplacement(" 25 ")
        compose.onNodeWithText("Apply").assertIsEnabled().performClick()
        assertEquals(listOf("agent_max_iterations" to "25"), applied)
    }

    /** 0 means unlimited and is a legitimate value. */
    @Test fun zeroIsALegitimateLimit() {
        showContent(limitRow("10"))
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText("Apply").assertIsEnabled()
    }

    /** Rows without the flag keep today's behaviour: anything can be applied, untrimmed. */
    @Test fun aPlainTextRowStillAppliesAnything() {
        showContent(SettingsItem.TextInputRow(key = "calculator_pin", title = "PIN", value = "", numeric = true))
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText("Apply").assertIsEnabled().performClick()
        assertEquals(listOf("calculator_pin" to ""), applied)
    }
}
