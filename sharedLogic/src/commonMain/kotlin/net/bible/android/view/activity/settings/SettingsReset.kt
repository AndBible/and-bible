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

import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import org.koin.mp.KoinPlatform

/** Shared Android/iOS reset contract. Device-local night mode is intentionally retained. */
object SettingsReset {
    val DEVICE_LOCAL_RESET_KEYS = listOf("locale_pref", "calculator_pin", "show_calculator", "discrete_mode")

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
        "theme_palette_style",
        "disable_animations",
        "disable_click_to_edit",
        "font_size_multiplier",
        "bible_view_swipe_mode",
        "experimental_features",
        "notes_content_type"
    )

    /** Clears the original typed DB keys and device-local keys; callers own UI refresh. */
    fun performReset(
        editor: AppSettings = KoinPlatform.getKoin().get(),
        devicePrefs: DeviceLocalPrefs = KoinPlatform.getKoin().get(),
    ) {
        for(key in RESET_KEYS) {
            editor.removeString(key)
            editor.removeBoolean(key)
            editor.removeLong(key)
            editor.removeDouble(key)
        }
        for (key in DEVICE_LOCAL_RESET_KEYS) devicePrefs.remove(key)
    }
}
