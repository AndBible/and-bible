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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.service.common.DisplayColorMode
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.components.AbChoiceSheetContent
import net.bible.sharedui.components.AbListChoiceContent
import net.bible.sharedui.theme.AbTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class AbListChoicePaddingTest {
    @get:Rule val compose = createComposeRule()

    private val choices = listOf(
        SettingsItem.Choice("hidden", "Hidden Links"),
        SettingsItem.Choice("links", "Links"),
        SettingsItem.Choice("text", "Text and Links"),
    )

    private fun setBareContent(direction: LayoutDirection, onSelect: (String) -> Unit = {}) {
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        androidx.compose.foundation.layout.Box(Modifier.width(320.dp)) {
                            AbListChoiceContent(choices, "links", onSelect, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    private fun labelLeadingInsetPx(label: String, isRtl: Boolean): Float {
        val bounds = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val widthPx = with(compose.density) { 320.dp.toPx() }
        return if (isRtl) widthPx - bounds.right else bounds.left
    }

    @Test fun bareRowsInsetLabelsFromTheLeadingEdgeAndPreserveSelectionInLtr() {
        setBareContent(LayoutDirection.Ltr)

        compose.onNodeWithText("Hidden Links").assertHasClickAction()
        compose.onNodeWithText("Links").assertIsSelected()
        val inset = labelLeadingInsetPx("Links", isRtl = false)
        val expectedInset = with(compose.density) { 56.dp.toPx() }
        assertEquals("Radio label should include row padding, radio and gap", expectedInset, inset, 1f)
    }

    @Test fun rowLeadingEdgeRemainsClickable() {
        val selections = mutableListOf<String>()
        setBareContent(LayoutDirection.Ltr) { selections += it }

        compose.onNodeWithText("Hidden Links").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(2f, centerY))
        }
        compose.runOnIdle { assertEquals(listOf("hidden"), selections) }
    }

    @Test fun bareRowsInsetLabelsFromTheLeadingEdgeInRtl() {
        setBareContent(LayoutDirection.Rtl)
        val inset = labelLeadingInsetPx("Links", isRtl = true)
        val expectedInset = with(compose.density) { 56.dp.toPx() }
        assertEquals("RTL radio label should be inset from the right edge", expectedInset, inset, 1f)
    }

    @Test fun choiceSheetDoesNotAddASecondHorizontalInset() {
        val bareChoices = listOf(SettingsItem.Choice("bare", "Bare option"))
        val sheetChoices = listOf(SettingsItem.Choice("sheet", "Sheet option"))
        compose.setContent {
            ProvideAppLocals {
                AbTheme(darkTheme = false, colorMode = DisplayColorMode.NORMAL, disableAnimations = true) {
                    Column(Modifier.width(320.dp)) {
                        AbListChoiceContent(bareChoices, "bare", {}, Modifier.fillMaxWidth())
                        AbChoiceSheetContent("Choose", sheetChoices, "sheet", {}, {})
                    }
                }
            }
        }
        val bareStart = compose.onNodeWithText("Bare option", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        val sheetStart = compose.onNodeWithText("Sheet option", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        assertEquals("Sheet and bare choice labels should have the same inset", bareStart, sheetStart, 1f)
    }
}
