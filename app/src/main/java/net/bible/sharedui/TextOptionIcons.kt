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

import net.bible.android.activity.R

/**
 * Drawable entry name -> `R.drawable.*` for every icon a text-display setting can carry, i.e. every
 * value classic's `ItemPreference.icon` returns (`OptionsMenuItems.kt:259-289`). Used by the
 * Text-options screen (through `LocalSettingsIcon`) and by both reading menus' "last used actions"
 * rows (A/B batch 3, F4).
 *
 * An explicit table, not `resources.getIdentifier(name, "drawable", packageName)`: a name-only
 * lookup is invisible to R8 and silently resolves to `0` once release resource shrinking runs — the
 * same rationale as `ComposeReadingViewHost.menuIconResIds`/`drawerIconResIds`. Kept in step with
 * classic by `TextDisplaySettingsIconsTest.everyClassicTextOptionIconIsInTheTable`.
 */
private val textOptionIconResIds: Map<String, Int> = mapOf(
    "ic_strongs_greek" to R.drawable.ic_strongs_greek,
    "ic_strongs_hebrew" to R.drawable.ic_strongs_hebrew,
    "ic_bookmarks_show_24dp" to R.drawable.ic_bookmarks_show_24dp,
    "ic_labels_hide_24dp" to R.drawable.ic_labels_hide_24dp,
    "ic_morphology_24dp" to R.drawable.ic_morphology_24dp,
    "ic_footnotes_24dp" to R.drawable.ic_footnotes_24dp,
    "ic_xrefs_inline_24dp" to R.drawable.ic_xrefs_inline_24dp,
    "ic_xrefs_24dp" to R.drawable.ic_xrefs_24dp,
    "ic_section_titles_24dp" to R.drawable.ic_section_titles_24dp,
    "ic_chapter_verse_numbers_24dp" to R.drawable.ic_chapter_verse_numbers_24dp,
    "ic_color_settings_24dp" to R.drawable.ic_color_settings_24dp,
    "ic_font_size_24dp" to R.drawable.ic_font_size_24dp,
    "ic_font_family_24dp" to R.drawable.ic_font_family_24dp,
    "ic_margin_size_24dp" to R.drawable.ic_margin_size_24dp,
    "ic_margin_top_24dp" to R.drawable.ic_margin_top_24dp,
    "ic_line_spacing_24dp" to R.drawable.ic_line_spacing_24dp,
    "ic_red_letter_24dp" to R.drawable.ic_red_letter_24dp,
    "ic_one_verse_per_line_24dp" to R.drawable.ic_one_verse_per_line_24dp,
    "ic_justify_text_24dp" to R.drawable.ic_justify_text_24dp,
    "ic_hyphenation_24dp" to R.drawable.ic_hyphenation_24dp,
    "ic_note_regular_24dp" to R.drawable.ic_note_regular_24dp,
    "ic_full_screen_by_scrolling_24dp" to R.drawable.ic_full_screen_by_scrolling_24dp,
    "ic_format_italic_24dp" to R.drawable.ic_format_italic_24dp,
    "ic_baseline_check_circle_24" to R.drawable.ic_baseline_check_circle_24,
    "ic_baseline_star_24" to R.drawable.ic_baseline_star_24,
)

/** See [textOptionIconResIds]. `null` for an unknown name. */
fun textOptionDrawableRes(name: String): Int? = textOptionIconResIds[name]
