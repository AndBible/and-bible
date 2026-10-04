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

/**
 * What the AI-language picker page should show as selected, and what the custom-language text page
 * should pre-fill, for one [aiLanguageSelection] computation.
 */
data class AiLanguageSelection(
    /** `AbListChoiceContent.selectedValue` for the picker page: the saved language itself when it
     *  is one of the picker's [SettingsItem.Choice]s, otherwise the "Custom…" sentinel. */
    val pickerSelectedValue: String,
    /** `AbTextInputContent.initial` for the custom-language text page: blank when the saved
     *  language is a known choice (nothing custom to prefill), otherwise the saved language. */
    val customLanguageInitial: String,
)

/**
 * Pure extraction of `AiConnectionSettingsScreen`'s AI-language selection logic (Task 7 fix round
 * 1), so a unit test can reach it: the sheet page itself cannot be golden-captured, because
 * capturing an open `ModalBottomSheet` hangs Roborazzi (see `AiConnectionSettingsGoldenTest`'s
 * `languageSheet_matrix`/`customLanguageSheet_matrix`, which capture the page bodies directly with
 * hardcoded fixture values instead).
 *
 * A [savedLanguage] absent from [choices] is a previously-saved CUSTOM language (the user typed a
 * free-form value the host's locale list doesn't otherwise offer): the picker then shows the
 * "Custom…" row ([customValue]) selected, and the custom-language page pre-fills with the saved
 * value so re-opening it doesn't lose it. A [savedLanguage] present in [choices] (including the ""
 * "app default" convention, see [AiSettingsService] — the real host's `buildLanguageChoices()`
 * always includes a `value = ""` entry, so an unset/default language is a KNOWN choice, not a
 * custom one) selects it directly in the picker, and the custom page pre-fills blank. Empty
 * [choices] does not crash: [savedLanguage] is then simply never "known", so the picker selects
 * [customValue] and the custom page pre-fills [savedLanguage].
 */
fun aiLanguageSelection(
    savedLanguage: String,
    choices: List<SettingsItem.Choice>,
    customValue: String,
): AiLanguageSelection {
    val isKnownLanguage = choices.any { it.value == savedLanguage }
    return AiLanguageSelection(
        pickerSelectedValue = if (isKnownLanguage) savedLanguage else customValue,
        customLanguageInitial = if (isKnownLanguage) "" else savedLanguage,
    )
}
