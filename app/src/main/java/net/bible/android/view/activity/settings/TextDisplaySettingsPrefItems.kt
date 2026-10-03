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

/**
 * The `SettingsBundle` -> `OptionsMenuItemInterface` mapping for every text-display setting.
 *
 * Split out of `TextDisplaySettings.kt` by Z-late S12, which deletes the classic activity that used
 * to host it. FIVE survivors consume it -- MainBibleActivity, WindowPaneMenuStateBuilder,
 * WorkspaceServiceImpl, WindowControl, and (importlessly, same package) TextDisplaySettingsServiceImpl,
 * which is the Compose side's single source of truth for the sparse-override/inheritance math.
 * (Six when S12 wrote this: classic `SplitBibleArea` was the sixth, and was deleted by the epilogue
 * task that orphaned it -- leaving this a five-consumer list, not a stale six.)
 */

import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.view.activity.page.Preference as ItemPreference
import net.bible.android.view.activity.page.AiDocMarkersPreference
import net.bible.android.view.activity.page.ColorPreference
import net.bible.android.view.activity.page.CommandPreference
import net.bible.android.view.activity.page.ExpandXrefsPreference
import net.bible.android.view.activity.page.FontFamilyPreference
import net.bible.android.view.activity.page.FontSizePreference
import net.bible.android.view.activity.page.FootnotesInlinePreference
import net.bible.android.view.activity.page.HideLabelsPreference
import net.bible.android.view.activity.page.InfiniteScrollPreference
import net.bible.android.view.activity.page.LineSpacingPreference
import net.bible.android.view.activity.page.MarginSizePreference
import net.bible.android.view.activity.page.MorphologyPreference
import net.bible.android.view.activity.page.MyNotesPreference
import net.bible.android.view.activity.page.NonStrongsWordItalicPreference
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.OrdinalsPreference
import net.bible.android.view.activity.page.PageButtonsPreference
import net.bible.android.view.activity.page.PageScrollAmountPreference
import net.bible.android.view.activity.page.RedLettersPreference
import net.bible.android.view.activity.page.ScrollHelperLineStylePreference
import net.bible.android.view.activity.page.ScrollHelperLinesPreference
import net.bible.android.view.activity.page.StrongsPreference
import net.bible.android.view.activity.page.TopMarginPreference

fun getPrefItem(settings: SettingsBundle, key: String): OptionsMenuItemInterface {
    return try {
        val type = Types.valueOf(key)
        getPrefItem(settings, type)
    } catch (e: IllegalArgumentException) {
        when(key) {
            "apply_to_all_workspaces" -> CommandPreference()
            else -> throw RuntimeException("Unsupported item key $key")
        }
    }
}

fun getPrefItem(settings: SettingsBundle, type: Types): OptionsMenuItemInterface =
    when(type) {
        Types.BOOKMARKS_SHOW -> ItemPreference(settings, Types.BOOKMARKS_SHOW)
        Types.REDLETTERS -> RedLettersPreference(settings)
        Types.SECTIONTITLES -> ItemPreference(settings, Types.SECTIONTITLES)
        Types.VERSENUMBERS -> ItemPreference(settings, Types.VERSENUMBERS)
        Types.VERSEPERLINE -> ItemPreference(settings, Types.VERSEPERLINE)
        Types.FOOTNOTES -> ItemPreference(settings, Types.FOOTNOTES)
        Types.FOOTNOTES_INLINE -> FootnotesInlinePreference(settings)
        Types.EXPAND_XREFS -> ExpandXrefsPreference(settings)
        Types.XREFS -> ItemPreference(settings, Types.XREFS)
        Types.MYNOTES -> MyNotesPreference(settings)
        Types.STRONGS -> StrongsPreference(settings)
        Types.MORPH -> MorphologyPreference(settings)
        Types.FONTSIZE -> FontSizePreference(settings)
        Types.FONTFAMILY -> FontFamilyPreference(settings)
        Types.MARGINSIZE -> MarginSizePreference(settings)
        Types.COLORS -> ColorPreference(settings)
        Types.JUSTIFY -> ItemPreference(settings, Types.JUSTIFY)
        Types.HYPHENATION -> ItemPreference(settings, Types.HYPHENATION)
        Types.TOPMARGIN -> TopMarginPreference(settings)
        Types.LINE_SPACING -> LineSpacingPreference(settings)
        Types.BOOKMARKS_HIDELABELS -> HideLabelsPreference(settings, Types.BOOKMARKS_HIDELABELS)
        Types.PAGENUMBER -> ItemPreference(settings, Types.PAGENUMBER)
        Types.INFINITE_SCROLL -> InfiniteScrollPreference(settings)
        Types.NON_STRONGS_WORD_ITALIC -> NonStrongsWordItalicPreference(settings)
        Types.MARK_AS_READ_BUTTON -> ItemPreference(settings, Types.MARK_AS_READ_BUTTON)
        Types.TITLE_SCROLL_BUTTON -> ItemPreference(settings, Types.TITLE_SCROLL_BUTTON)
        Types.MEMORIZATION_INDICATORS -> ItemPreference(settings, Types.MEMORIZATION_INDICATORS)
        Types.AUTO_TRACK_READING -> ItemPreference(settings, Types.AUTO_TRACK_READING)
        Types.AI_DOC_MARKERS -> AiDocMarkersPreference(settings)
        Types.ORDINALS -> OrdinalsPreference(settings)
        Types.PAGE_SCROLL_AMOUNT -> PageScrollAmountPreference(settings)
        Types.SCROLL_HELPER_LINES -> ScrollHelperLinesPreference(settings)
        Types.SCROLL_HELPER_LINE_STYLE -> ScrollHelperLineStylePreference(settings)
        Types.PAGE_BUTTONS -> PageButtonsPreference(settings)
        Types.SHOW_READING_PROGRESS -> ItemPreference(settings, Types.SHOW_READING_PROGRESS)
    }
