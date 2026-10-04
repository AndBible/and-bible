/*
 * Copyright (c) 2020-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.android.view.activity.settings

import net.bible.service.common.CommonUtils

/**
 * The settings-reset contract, lifted out of `SettingsActivity`'s companion object when the
 * classic settings screen was deleted (Batch Z-late phase 1, slice S12). Classic's own reset path
 * is gone with it; [SettingsComposeActivity.resetSettings] is now the SOLE caller of
 * [performReset], and `SettingsResetTest` is the only other reader of [RESET_KEYS]. Kept as its
 * own object rather than folded back into `SettingsComposeActivity` because a standalone list is
 * simpler to test than a companion-object one.
 */
object SettingsReset {
    /**
     * The hardcoded key list cleared by [SettingsComposeActivity.resetSettings] — the classic
     * `SettingsActivity.reset` this list used to also serve is deleted (Z-late slice S12). Does
     * not include the `realSharedPreferences`-routed keys (`locale_pref`/`calculator_pin`/
     * `show_calculator`/`discrete_mode`), which [performReset] clears separately, matching the
     * classic split.
     */
    val RESET_KEYS = listOf(
        "strongs_greek_dictionary",
        "strongs_hebrew_dictionary",
        "robinson_greek_morphology",
        "disabled_word_lookup_dictionaries",
        "navigate_to_verse_pref",
        "open_links_in_special_window_pref",
        "screen_keep_on_pref",
        "auto_fullscreen_pref",
        "full_screen_hide_buttons_pref",
        "hide_window_buttons",
        "hide_bible_reference_overlay",
        "show_active_window_indicator",
        "toolbar_button_actions",
        "disable_two_step_bookmarking",
        "double_tap_to_fullscreen",
        "night_mode_pref3",
        "request_sdcard_permission_pref",
        "show_errorbox",
        "show_calculator",
        "calculator_pin",
        "google_drive_sync",
        "disable_bible_bookmark_modal_buttons",
        "disable_gen_bookmark_modal_buttons",
        "display_color_mode",
        "disable_animations",
        "disable_click_to_edit",
        "font_size_multiplier",
        "bible_view_swipe_mode",
        "experimental_features",
        "notes_content_type"
    )

    /** Clears [RESET_KEYS] from [CommonUtils.settings] plus the realShared-routed keys. Does NOT recreate — callers do that themselves. */
    fun performReset() {
        val editor = CommonUtils.settings
        for(key in RESET_KEYS) {
            editor.removeString(key)
            editor.removeBoolean(key)
            editor.removeLong(key)
            editor.removeDouble(key)
        }
        CommonUtils.realSharedPreferences.edit().remove("locale_pref").apply()
        CommonUtils.realSharedPreferences.edit().remove("calculator_pin").apply()
        CommonUtils.realSharedPreferences.edit().remove("show_calculator").apply()
        CommonUtils.realSharedPreferences.edit().remove("discrete_mode").apply()
    }
}
