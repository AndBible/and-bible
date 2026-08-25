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

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import net.bible.android.TEST_SDK
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedui.components.AbSliderRow
import net.bible.sharedui.settings.AbSettingsContent
import net.bible.sharedui.settings.LocalSettingsIcon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The slider was the one settings row type that dropped `iconKey`, so `SettingsItem.SliderRow.iconKey`
 * was a field nothing rendered. These tests assert the seam is CONSULTED for the row's key — the part
 * that can actually regress — rather than inspecting pixels: `LocalSettingsIcon` here records its keys
 * and returns null, so nothing is drawn and the assertions stay independent of drawable art.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SettingsRowIconSeamTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test fun sliderRowConsultsTheIconSeamForItsKey() {
        val seen = mutableListOf<String>()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsIcon provides { key -> seen.add(key); null }) {
                AbSliderRow(
                    label = "Font size",
                    value = 150,
                    onValueChange = {},
                    valueRange = 10f..500f,
                    valueLabel = "150 %",
                    iconKey = "font_size_multiplier",
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("font_size_multiplier"), seen.distinct())
    }

    @Test fun settingsContentForwardsSliderIconKey() {
        val seen = mutableListOf<String>()
        val state = SettingsScreenState(
            title = "Settings",
            items = listOf(
                SettingsItem.SliderRow(
                    key = "font_size_multiplier",
                    title = "Font size",
                    value = 150,
                    min = 10,
                    max = 500,
                    valueLabel = "150 %",
                    iconKey = "font_size_multiplier",
                ),
            ),
        )
        composeTestRule.setContent {
            CompositionLocalProvider(LocalSettingsIcon provides { key -> seen.add(key); null }) {
                AbSettingsContent(
                    state = state,
                    onSwitch = { _, _ -> },
                    onListChoice = { _, _ -> },
                    onTextInput = { _, _ -> },
                    onNavigate = {},
                    onOpenEditor = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(listOf("font_size_multiplier"), seen.distinct())
    }
}
