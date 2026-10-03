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
package net.bible.android.view.activity.settings

import net.bible.android.TEST_SDK
import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.sharedui.textOptionDrawableRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Mirrors `OptionsMenuItems.kt:259-289` (`ItemPreference.icon`) exactly, so a future edit to one side
 * without the other fails [everyClassicTextOptionIconIsInTheTable] instead of shipping a blank row.
 * `isNewTestament` is not observable here without a real document, so both Strongs variants are
 * exercised explicitly by passing it in.
 */
private fun classicIconResIdFor(type: TextDisplaySettings.Types, isNewTestament: Boolean = true): Int? =
    when (type) {
        TextDisplaySettings.Types.STRONGS -> if (isNewTestament) R.drawable.ic_strongs_greek else R.drawable.ic_strongs_hebrew
        TextDisplaySettings.Types.BOOKMARKS_SHOW -> R.drawable.ic_bookmarks_show_24dp
        TextDisplaySettings.Types.BOOKMARKS_HIDELABELS -> R.drawable.ic_labels_hide_24dp
        TextDisplaySettings.Types.MORPH -> R.drawable.ic_morphology_24dp
        TextDisplaySettings.Types.FOOTNOTES -> R.drawable.ic_footnotes_24dp
        TextDisplaySettings.Types.EXPAND_XREFS -> R.drawable.ic_xrefs_inline_24dp
        TextDisplaySettings.Types.XREFS -> R.drawable.ic_xrefs_24dp
        TextDisplaySettings.Types.SECTIONTITLES -> R.drawable.ic_section_titles_24dp
        TextDisplaySettings.Types.VERSENUMBERS -> R.drawable.ic_chapter_verse_numbers_24dp
        TextDisplaySettings.Types.COLORS -> R.drawable.ic_color_settings_24dp
        TextDisplaySettings.Types.FONTSIZE -> R.drawable.ic_font_size_24dp
        TextDisplaySettings.Types.FONTFAMILY -> R.drawable.ic_font_family_24dp
        TextDisplaySettings.Types.MARGINSIZE -> R.drawable.ic_margin_size_24dp
        TextDisplaySettings.Types.TOPMARGIN -> R.drawable.ic_margin_top_24dp
        TextDisplaySettings.Types.LINE_SPACING -> R.drawable.ic_line_spacing_24dp
        TextDisplaySettings.Types.REDLETTERS -> R.drawable.ic_red_letter_24dp
        TextDisplaySettings.Types.VERSEPERLINE -> R.drawable.ic_one_verse_per_line_24dp
        TextDisplaySettings.Types.JUSTIFY -> R.drawable.ic_justify_text_24dp
        TextDisplaySettings.Types.HYPHENATION -> R.drawable.ic_hyphenation_24dp
        TextDisplaySettings.Types.MYNOTES -> R.drawable.ic_note_regular_24dp
        TextDisplaySettings.Types.PAGENUMBER -> R.drawable.ic_chapter_verse_numbers_24dp
        TextDisplaySettings.Types.INFINITE_SCROLL -> R.drawable.ic_full_screen_by_scrolling_24dp
        TextDisplaySettings.Types.NON_STRONGS_WORD_ITALIC -> R.drawable.ic_format_italic_24dp
        TextDisplaySettings.Types.MARK_AS_READ_BUTTON -> R.drawable.ic_baseline_check_circle_24
        TextDisplaySettings.Types.TITLE_SCROLL_BUTTON -> R.drawable.ic_section_titles_24dp
        TextDisplaySettings.Types.MEMORIZATION_INDICATORS -> R.drawable.ic_baseline_check_circle_24
        TextDisplaySettings.Types.AUTO_TRACK_READING -> R.drawable.ic_baseline_check_circle_24
        else -> R.drawable.ic_baseline_star_24
    }

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class TextDisplaySettingsIconsTest {

    /**
     * Every drawable classic's `ItemPreference.icon` can return must be resolvable by name. A new
     * `TextDisplaySettings.Types` entry whose icon is not in the table fails HERE rather than
     * shipping a blank row. Both Strongs variants (greek/hebrew) are checked explicitly since
     * `classicIconResIdFor` takes `isNewTestament` as a parameter rather than reading it from a
     * document.
     */
    @Test
    fun everyClassicTextOptionIconIsInTheTable() {
        val res = RuntimeEnvironment.getApplication().resources
        val missing = TextDisplaySettings.Types.entries.flatMap { type ->
            listOf(true, false).mapNotNull { isNt ->
                val resId = classicIconResIdFor(type, isNt) ?: return@mapNotNull null
                val name = res.getResourceEntryName(resId)
                if (textOptionDrawableRes(name) == null) "$type (isNewTestament=$isNt) -> $name" else null
            }
        }
        assertTrue("textOptionDrawableRes is missing: $missing", missing.isEmpty())
    }

    @Test
    fun aKnownNameResolvesToTheSameDrawable() {
        val res = RuntimeEnvironment.getApplication().resources
        val id = textOptionDrawableRes("ic_footnotes_24dp")
        assertNotNull(id)
        assertEquals("ic_footnotes_24dp", res.getResourceEntryName(id!!))
    }

    @Test
    fun anUnknownNameResolvesToNull() {
        assertEquals(null, textOptionDrawableRes("not_a_real_drawable"))
    }
}
