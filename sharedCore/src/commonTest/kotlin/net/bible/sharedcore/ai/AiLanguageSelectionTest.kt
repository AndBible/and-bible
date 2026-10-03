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

package net.bible.sharedcore.ai

import net.bible.sharedcore.settings.SettingsItem
import kotlin.test.Test
import kotlin.test.assertEquals

class AiLanguageSelectionTest {

    private val customValue = " custom"

    private val choices = listOf(
        SettingsItem.Choice("", "App language (English)"),
        SettingsItem.Choice("en", "English"),
        SettingsItem.Choice("fi", "Finnish"),
        SettingsItem.Choice(customValue, "Custom..."),
    )

    /** A saved language present in `choices` (an ordinary configured language): the picker selects
     *  it directly and the custom page has nothing to prefill. */
    @Test fun savedLanguagePresentInChoicesSelectsItAndLeavesCustomPageBlank() {
        val result = aiLanguageSelection(savedLanguage = "fi", choices = choices, customValue = customValue)
        assertEquals(AiLanguageSelection(pickerSelectedValue = "fi", customLanguageInitial = ""), result)
    }

    /** A saved language ABSENT from `choices` is a previously-saved custom language: the picker
     *  must show "Custom..." selected (not a stray/blank radio state), and the custom page must
     *  prefill the saved value so re-opening it doesn't lose it. This is the exact case a swapped
     *  branch would get backwards. */
    @Test fun savedLanguageAbsentFromChoicesSelectsCustomAndPrefillsTheSavedValue() {
        val result = aiLanguageSelection(savedLanguage = "xx-Klingon", choices = choices, customValue = customValue)
        assertEquals(
            AiLanguageSelection(pickerSelectedValue = customValue, customLanguageInitial = "xx-Klingon"),
            result,
        )
    }

    /** An empty saved language is the "app default" convention (see AiSettingsService/
     *  AiConnectionSettingsComposeActivity.buildLanguageChoices, which always adds a `value = ""`
     *  entry) -- so in the REALISTIC case where `choices` includes that entry, "" is a KNOWN choice,
     *  not a custom one: the picker selects "" (the app-default row), and the custom page prefills
     *  blank. Same branch as the "present in choices" case above, by construction. */
    @Test fun emptySavedLanguageWithAppDefaultChoicePresentSelectsAppDefault() {
        val result = aiLanguageSelection(savedLanguage = "", choices = choices, customValue = customValue)
        assertEquals(AiLanguageSelection(pickerSelectedValue = "", customLanguageInitial = ""), result)
    }

    /** Empty `choices` must not crash: every saved language (even "") is then simply never
     *  "known", so it falls into the same custom-language branch as any other unrecognized value. */
    @Test fun emptyChoicesFallsBackToCustomWithoutCrashing() {
        val result = aiLanguageSelection(savedLanguage = "", choices = emptyList(), customValue = customValue)
        assertEquals(AiLanguageSelection(pickerSelectedValue = customValue, customLanguageInitial = ""), result)
    }
}
