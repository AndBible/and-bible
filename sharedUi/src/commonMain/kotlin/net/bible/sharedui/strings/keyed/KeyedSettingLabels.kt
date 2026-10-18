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

package net.bible.sharedui.strings.keyed

import net.bible.android.control.page.window.SettingLabels
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayString

/**
 * [SettingLabels] over a [StringTable]: the static title of each text display setting (the keys `getPrefItem` uses
 * in `OptionsMenuItems.kt`). Android's dynamic titles (current font size, margins, ...) are not part of this port.
 */
class KeyedSettingLabels(private val t: StringTable) : SettingLabels {
    override fun label(bundle: SettingsBundle, type: TextDisplaySettings.Types): String = t.text(titleKey(type))

    companion object {
        /** The resource key of [type]'s title. Exhaustive: a new [Types] value fails to compile here. */
        fun titleKey(type: Types): String = when (type) {
            Types.STRONGS -> "prefs_show_strongs_title"
            Types.MORPH -> "prefs_show_morphology_title"
            Types.FOOTNOTES -> "prefs_show_footnotes_title"
            Types.FOOTNOTES_INLINE -> "prefs_show_footnotes_inline_title"
            Types.EXPAND_XREFS -> "prefs_expand_footnotes_title"
            Types.XREFS -> "prefs_show_xrefs_title"
            Types.REDLETTERS -> "prefs_red_letter_title"
            Types.SECTIONTITLES -> "prefs_section_title_title"
            Types.VERSENUMBERS -> "prefs_show_verseno_title"
            Types.VERSEPERLINE -> "prefs_verse_per_line_title"
            Types.MYNOTES -> "prefs_show_mynotes_title"
            Types.COLORS -> "prefs_text_colors_menutitle"
            Types.JUSTIFY -> "prefs_justify_title"
            Types.HYPHENATION -> "prefs_hyphenation_title"
            Types.TOPMARGIN -> "prefs_top_margin_title"
            Types.FONTSIZE -> "font_size_title"
            Types.FONTFAMILY -> "pref_font_family_label"
            Types.MARGINSIZE -> "prefs_margin_size_title"
            Types.LINE_SPACING -> "line_spacing_title"
            Types.AI_DOC_MARKERS -> "prefs_show_ai_doc_markers_title"
            Types.BOOKMARKS_SHOW -> "prefs_show_bookmarks_title"
            Types.BOOKMARKS_HIDELABELS -> "bookmark_settings_hide_labels_title"
            Types.PAGENUMBER -> "page_number_title"
            Types.INFINITE_SCROLL -> "prefs_infinite_scroll_title"
            Types.NON_STRONGS_WORD_ITALIC -> "prefs_non_strongs_word_italic_title"
            Types.MARK_AS_READ_BUTTON -> "prefs_mark_as_read_button_title"
            Types.TITLE_SCROLL_BUTTON -> "prefs_title_scroll_button_title"
            Types.MEMORIZATION_INDICATORS -> "prefs_show_memorization_indicators_title"
            Types.AUTO_TRACK_READING -> "prefs_auto_track_reading_title"
            Types.PAGE_SCROLL_AMOUNT -> "prefs_page_scroll_amount_title"
            Types.SCROLL_HELPER_LINES -> "prefs_scroll_helper_lines_title"
            Types.SCROLL_HELPER_LINE_STYLE -> "prefs_scroll_helper_line_style_title"
            Types.PAGE_BUTTONS -> "prefs_page_buttons_title"
            Types.ORDINALS -> "prefs_show_ordinals_title"
            Types.SHOW_READING_PROGRESS -> "prefs_show_reading_progress_title"
        }
    }
}

/**
 * The resource keys behind `TextDisplayPrefItems.string(...)` and `.array(...)` (the same mapping as
 * `AndroidTextDisplayPrefItems`), so an iOS implementation is `t.fmt(textDisplayStringKey(key), *args)`.
 */
fun textDisplayStringKey(key: TextDisplayString): String = when (key) {
    TextDisplayString.FONT_SIZE_PT -> "font_size_pt"
    TextDisplayString.VALUE_MM -> "value_mm"
    TextDisplayString.LINE_SPACING_PT -> "prefs_line_spacing_pt"
    TextDisplayString.MARGIN_SIZE_MM_TITLE -> "prefs_margin_size_mm_title"
    TextDisplayString.TEXT_COLORS_SUMMARY -> "prefs_text_colors_summary"
    TextDisplayString.HIDE_LABELS_SUMMARY -> "bookmark_settings_hide_labels_summary"
    TextDisplayString.GLOBAL_TITLE -> "global_text_display_settings_title"
    TextDisplayString.WORKSPACE_TITLE -> "workspace_text_display_settings_title"
    TextDisplayString.WINDOW_TITLE -> "window_text_display_settings_title"
    TextDisplayString.WINDOW_COLOR_TITLE -> "window_color_settings_title"
    TextDisplayString.WORKSPACE_COLOR_TITLE -> "workspace_color_settings_title"
    TextDisplayString.BACKGROUND_IMAGE_NONE -> "background_image_none"
}

/** Array resource name of [key]; see [textDisplayStringKey]. */
fun textDisplayArrayName(key: TextDisplayArray): String = when (key) {
    TextDisplayArray.STRONGS_MODE_ENTRIES -> "strongsModeEntries"
    TextDisplayArray.PAGE_SCROLL_AMOUNT_ENTRIES -> "pageScrollAmountEntries"
    TextDisplayArray.SCROLL_HELPER_LINE_STYLE_ENTRIES -> "scrollHelperLineStyleEntries"
}
