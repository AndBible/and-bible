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
package net.bible.sharedui

import androidx.annotation.DrawableRes
import net.bible.android.activity.R

/**
 * Maps a [net.bible.sharedcore.settings.SettingsItem] `iconKey` to its classic drawable resource, if
 * any. Backs the `LocalSettingsIcon` seam ([ProvideAppLocals]) so moved Compose settings screens can
 * show the classic per-row leading icon. Unknown/unmapped keys return `null` — the row then renders
 * with no leading icon (same as an item with `iconKey == null`), never a crash or a placeholder box.
 *
 * F29 (Batch B): AI connection-settings row keys, mirroring classic `res/xml/ai_connection_settings.xml`
 * `android:icon` values verbatim. The seam's Painter is always rendered via Compose `Icon` (not `Image`)
 * by [net.bible.sharedui.settings.AbSettingsScreen], which tints every pixel to the current content
 * colour — so a colour-coded classic icon (e.g. the red warning triangle) still renders correctly
 * grayscale in monochrome/e-ink mode; only the shape carries meaning here, not the drawable's baked-in
 * colour. `ask_model_before_run`/`auto_hide_agent_log_on_completion` are mapped for completeness
 * (classic parity); [net.bible.sharedui.components.AbSwitchRow] does have a leading-icon slot, these
 * two rows simply don't set an `iconKey`, so they render with no icon.
 *
 * Round 14b: three more groups (sync settings, application preferences, reading progress) mirror
 * their classic preference XML `android:icon` values verbatim, pinned by `SettingsIconParityTest`.
 * These rows also render at the plain Material 24.dp leading-icon size, deliberately not
 * reproducing classic `SettingsActivity`'s `CommonUtils.makeLarger(icon, 1.5f)` enlargement, which
 * classic itself applies only on the Application-preferences screen (Sync and Reading progress use
 * classic's plain size too).
 */
@DrawableRes
fun settingsDrawableRes(key: String): Int? = when (key) {
    "ai_disclaimer_warning" -> R.drawable.ic_warning_red_24dp
    "ai_getting_started" -> R.drawable.ic_baseline_cloud_24
    "ai_providers_shortcut" -> R.drawable.ic_baseline_cloud_24
    "ai_models_shortcut" -> R.drawable.icon_robot
    "ai_language" -> R.drawable.ic_baseline_description_gray_24
    "agent_permission_mode" -> R.drawable.ic_baseline_security_24
    "manage_tool_permissions" -> R.drawable.ic_baseline_security_24
    "manage_ai_documents" -> R.drawable.ic_baseline_description_gray_24
    "commentary_max_response_chars" -> R.drawable.ic_baseline_description_gray_24
    "agent_max_iterations" -> R.drawable.ic_baseline_description_gray_24
    "ask_model_before_run" -> R.drawable.ic_baseline_description_gray_24
    "auto_hide_agent_log_on_completion" -> R.drawable.ic_baseline_description_gray_24
    "custom_agent_system_prompt" -> R.drawable.ic_baseline_description_gray_24
    "custom_text_transform_system_prompt" -> R.drawable.ic_baseline_description_gray_24
    "llm_usage_summary" -> R.drawable.ic_baseline_description_gray_24
    "llm_reset_usage" -> R.drawable.ic_baseline_refresh_gray_24
    "raw_log_history" -> R.drawable.ic_baseline_description_gray_24
    "raw_log_retention" -> R.drawable.ic_delete_24dp
    // Round 14b — sync settings, mirroring res/xml/sync_settings.xml `android:icon` verbatim.
    // `sync_enable_readingplans` is intentionally absent: classic hides that row at runtime and the
    // Compose screen never builds it (SyncCategoryKeys.DISPLAY).
    "sync_adapter" -> R.drawable.ic_syncdb_24dp
    "cloud_sync_reset" -> R.drawable.baseline_logout_24
    "cloud_sync_info" -> R.drawable.ic_info_grey_24dp
    "cloud_sync_server_url" -> R.drawable.outline_shield_24
    "cloud_sync_username" -> R.drawable.outline_shield_24
    "cloud_sync_password" -> R.drawable.outline_shield_24
    "cloud_sync_folder_path" -> R.drawable.outline_shield_24
    "sync_enable_bookmarks" -> R.drawable.ic_bookmark_24dp
    "sync_enable_workspaces" -> R.drawable.ic_baseline_workspace_24
    "sync_enable_mydocuments" -> R.drawable.ic_baseline_description_gray_24
    "sync_enable_ai_settings" -> R.drawable.icon_robot
    "sync_enable_progress" -> R.drawable.ic_baseline_check_circle_24
    "sync_enable_documents" -> R.drawable.ic_baseline_description_gray_24
    "sync_documents_auto_download" -> R.drawable.ic_cloud_download_24dp
    "sync_documents_auto_upload" -> R.drawable.ic_cloud_upload_24dp
    "sync_documents_auto_delete" -> R.drawable.ic_delete_24dp
    "sync_documents_wifi_only" -> R.drawable.ic_wifi_24dp
    "document_sync_manage" -> R.drawable.ic_baseline_cloud_24

    // Round 14b — application preferences, mirroring res/xml/settings.xml `android:icon` verbatim.
    // `request_sdcard_permission_pref` has no classic icon and gets none.
    "strongs_greek_dictionary" -> R.drawable.ic_strongs_greek
    "strongs_hebrew_dictionary" -> R.drawable.ic_strongs_hebrew
    "robinson_greek_morphology" -> R.drawable.ic_morphology_24dp
    "disabled_word_lookup_dictionaries" -> R.drawable.ic_dictionary_24dp
    "navigate_to_verse_pref" -> R.drawable.ic_chapter_verse_numbers_24dp
    "open_links_in_special_window_pref" -> R.drawable.ic_link_window_24dp
    "screen_keep_on_pref" -> R.drawable.ic_baseline_light_mode_24
    "double_tap_to_fullscreen" -> R.drawable.ic_full_screen_24
    "auto_fullscreen_pref" -> R.drawable.ic_full_screen_by_scrolling_24dp
    "toolbar_button_actions" -> R.drawable.ic_action_for_button_press_24dp
    "disable_two_step_bookmarking" -> R.drawable.ic_bookmark_24dp
    "bible_view_swipe_mode" -> R.drawable.ic_full_screen_by_scrolling_24dp
    "volume_keys_scroll" -> R.drawable.ic_baseline_volume_up_24
    "night_mode_pref3" -> R.drawable.ic_night_mode_switching_24dp
    "global_text_display_settings" -> R.drawable.ic_text_format_white_24dp
    "locale_pref" -> R.drawable.ic_application_language_24dp
    "disable_click_to_edit" -> R.drawable.ic_click_24dp
    "notes_content_type" -> R.drawable.ic_text_format_white_24dp
    "font_size_multiplier" -> R.drawable.ic_font_size_grey_24dp
    "hide_status_bar" -> R.drawable.ic_full_screen_24
    "full_screen_hide_buttons_pref" -> R.drawable.ic_hide_window_button_bar_24dp
    "hide_window_buttons" -> R.drawable.ic_hide_window_buttons_24dp
    "hide_bible_reference_overlay" -> R.drawable.ic_hide_bible_reference_overlay_24dp
    "show_active_window_indicator" -> R.drawable.ic_active_window_24dp
    "disable_bible_bookmark_modal_buttons" -> R.drawable.ic_one_tap_bible_24
    "disable_gen_bookmark_modal_buttons" -> R.drawable.ic_one_tap_other_24
    "display_color_mode" -> R.drawable.ic_eink_24dp
    "eink_mode" -> R.drawable.ic_eink_24dp
    "disable_animations" -> R.drawable.ic_animate_24dp
    "discrete_help" -> R.drawable.ic_warning_red_24dp
    "discrete_mode" -> R.drawable.ic_calc_24
    "show_calculator" -> R.drawable.ic_calc_smoke_screen
    "calculator_pin" -> R.drawable.ic_calc_pin
    "sync_settings_shortcut" -> R.drawable.ic_syncdb_24dp
    "ai_settings_shortcut" -> R.drawable.icon_robot
    "reading_progress_settings_shortcut" -> R.drawable.ic_baseline_check_circle_24
    "experimental_features" -> R.drawable.ic_bug_report_white_24dp
    "enable_bluetooth_pref" -> R.drawable.ic_baseline_media_bluetooth_on_24
    "show_errorbox" -> R.drawable.ic_bug_report_white_24dp
    "open_links" -> R.drawable.ic_link_black_24dp
    "crash_app" -> R.drawable.ic_bug_report_white_24dp

    // Round 14b — reading progress, mirroring res/xml/reading_progress_settings.xml verbatim.
    "auto_mark_memorized" -> R.drawable.ic_baseline_check_circle_24
    "memorize_type_full_words" -> R.drawable.ic_baseline_keyboard_24
    "memorize_word_visibility" -> R.drawable.ic_baseline_visibility_24
    "memorize_error_heatmap" -> R.drawable.ic_baseline_error_24
    "memorize_scramble_hide_used" -> R.drawable.ic_baseline_visibility_off_24
    "memorize_include_reference" -> R.drawable.ic_baseline_menu_book_gray_24

    // 17f: the prompt editor's Advanced tab, mirroring res/xml/prompt_advanced_settings.xml.
    // NOTE "max_iterations" here is the PROMPT row; the AI connection screen's own iteration row
    // is "agent_max_iterations" above and classic gives the two different icons.
    "model_override" -> R.drawable.ic_baseline_cloud_24
    "strict_context_matching" -> R.drawable.ic_baseline_description_gray_24
    "max_iterations" -> R.drawable.ic_baseline_refresh_gray_24
    "specify_before_run" -> R.drawable.ic_baseline_keyboard_24
    "no_document_creation" -> R.drawable.ic_baseline_visibility_off_24
    "auto_include_documents" -> R.drawable.ic_baseline_menu_book_gray_24
    "auto_include_commentaries" -> R.drawable.ic_baseline_chat_bubble_outline_gray_24

    // A/B batch 3 F4: the Text-options screen's rows carry a classic drawable NAME as their
    // iconKey (not a settings key like the entries above), so fall through to that table.
    else -> textOptionDrawableRes(key)
}
