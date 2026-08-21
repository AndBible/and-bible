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

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import net.bible.android.activity.R
import net.bible.service.common.AndBibleAddons
import net.bible.sharedcore.settings.TextDisplaySettingsLabels
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedui.settings.BackgroundImageChooserLabels
import net.bible.sharedui.settings.ColorSettingsLabels
import net.bible.sharedui.settings.TextDisplaySettingsScreenLabels

/**
 * The four label bundles [TextDisplaySettingsComposeActivity] builds for the text-display-settings
 * screen, its colours form and its background-image chooser — hoisted out to top-level, `Context`-
 * taking functions (Settings editor sheets T10) so [net.bible.android.view.activity.page.screen
 * .ComposeReadingViewHost]'s in-place editor can build the SAME labels without duplicating ~90
 * `getString` calls (or drifting from them). Moved verbatim from that Activity's four private
 * `build*Labels()` methods — every `getString(...)` became `context.getString(...)` and nothing
 * else changed; the Activity's own `by lazy` properties now just call these.
 */
fun buildTextDisplayControllerLabels(context: Context) = TextDisplaySettingsLabels(
    categoryParent = context.getString(R.string.parent_settings_category_title),
    categoryFontColors = context.getString(R.string.prefs_font_and_colors_title),
    categoryTextLayout = context.getString(R.string.prefs_text_layout_title),
    categoryStrongsMorphology = context.getString(R.string.prefs_strongs_and_morphology_title),
    categoryFootnotesXrefs = context.getString(R.string.prefs_footnotes_and_xrefs_title),
    categoryVersesHeadings = context.getString(R.string.prefs_verses_and_headings_title),
    categoryPageScrolling = context.getString(R.string.prefs_page_scrolling_title),
    categoryBookmarks = context.getString(R.string.prefs_text_bookmarks_title),
    categoryReadingMemorization = context.getString(R.string.prefs_reading_and_memorization_title),
    // Contains "%s" -- left un-substituted here; the controller does its own .replace("%s", ...).
    workspaceLinkTitleFormat = context.getString(R.string.workspace_text_options_link),
    workspaceLinkSummary = context.getString(R.string.workspace_text_options_link_summary),
    globalLinkTitle = context.getString(R.string.global_text_options_link),
    globalLinkSummary = context.getString(R.string.global_text_options_link_summary),
    badgeWorkspace = context.getString(R.string.text_options_inherited_workspace),
    badgeGlobal = context.getString(R.string.text_options_inherited_global),
    titles = mapOf(
        TextSettingType.COLORS to context.getString(R.string.prefs_text_colors_menutitle),
        TextSettingType.FONTSIZE to context.getString(R.string.font_size_title),
        TextSettingType.FONTFAMILY to context.getString(R.string.pref_font_family_label),
        TextSettingType.LINE_SPACING to context.getString(R.string.line_spacing_title),
        TextSettingType.REDLETTERS to context.getString(R.string.prefs_red_letter_title),
        TextSettingType.MARGINSIZE to context.getString(R.string.prefs_margin_size_title),
        TextSettingType.TOPMARGIN to context.getString(R.string.prefs_top_margin_title),
        TextSettingType.JUSTIFY to context.getString(R.string.prefs_justify_title),
        TextSettingType.HYPHENATION to context.getString(R.string.prefs_hyphenation_title),
        TextSettingType.VERSEPERLINE to context.getString(R.string.prefs_verse_per_line_title),
        TextSettingType.STRONGS to context.getString(R.string.prefs_show_strongs_title),
        TextSettingType.MORPH to context.getString(R.string.prefs_show_morphology_title),
        TextSettingType.NON_STRONGS_WORD_ITALIC to context.getString(R.string.prefs_non_strongs_word_italic_title),
        TextSettingType.FOOTNOTES to context.getString(R.string.prefs_show_footnotes_title),
        TextSettingType.FOOTNOTES_INLINE to context.getString(R.string.prefs_show_footnotes_inline_title),
        TextSettingType.XREFS to context.getString(R.string.prefs_show_xrefs_title),
        TextSettingType.EXPAND_XREFS to context.getString(R.string.prefs_expand_footnotes_title),
        TextSettingType.VERSENUMBERS to context.getString(R.string.prefs_show_verseno_title),
        TextSettingType.SECTIONTITLES to context.getString(R.string.prefs_section_title_title),
        TextSettingType.TITLE_SCROLL_BUTTON to context.getString(R.string.prefs_title_scroll_button_title),
        TextSettingType.PAGENUMBER to context.getString(R.string.page_number_title),
        TextSettingType.INFINITE_SCROLL to context.getString(R.string.prefs_infinite_scroll_title),
        TextSettingType.PAGE_SCROLL_AMOUNT to context.getString(R.string.prefs_page_scroll_amount_title),
        TextSettingType.SCROLL_HELPER_LINES to context.getString(R.string.prefs_scroll_helper_lines_title),
        TextSettingType.SCROLL_HELPER_LINE_STYLE to context.getString(R.string.prefs_scroll_helper_line_style_title),
        TextSettingType.PAGE_BUTTONS to context.getString(R.string.prefs_page_buttons_title),
        TextSettingType.ORDINALS to context.getString(R.string.prefs_show_ordinals_title),
        TextSettingType.SHOW_READING_PROGRESS to context.getString(R.string.prefs_show_reading_progress_title),
        TextSettingType.BOOKMARKS_SHOW to context.getString(R.string.prefs_show_bookmarks_title),
        TextSettingType.MYNOTES to context.getString(R.string.prefs_show_mynotes_title),
        TextSettingType.AI_DOC_MARKERS to context.getString(R.string.prefs_show_ai_doc_markers_title),
        TextSettingType.BOOKMARKS_HIDELABELS to context.getString(R.string.bookmark_settings_hide_labels_title),
        TextSettingType.MARK_AS_READ_BUTTON to context.getString(R.string.prefs_mark_as_read_button_title),
        TextSettingType.MEMORIZATION_INDICATORS to context.getString(R.string.prefs_show_memorization_indicators_title),
        TextSettingType.AUTO_TRACK_READING to context.getString(R.string.prefs_auto_track_reading_title),
    ),
    summaries = mapOf(
        TextSettingType.COLORS to context.getString(R.string.prefs_text_colors_summary),
        TextSettingType.FONTSIZE to context.getString(R.string.prefs_font_text_size_summary),
        TextSettingType.FONTFAMILY to context.getString(R.string.prefs_font_family_summary),
        TextSettingType.LINE_SPACING to context.getString(R.string.line_spacing_summary),
        TextSettingType.REDLETTERS to context.getString(R.string.prefs_red_letter_summary),
        TextSettingType.MARGINSIZE to context.getString(R.string.prefs_margin_size_summary),
        TextSettingType.TOPMARGIN to context.getString(R.string.prefs_top_margin_summary),
        TextSettingType.JUSTIFY to context.getString(R.string.prefs_justify_summary),
        TextSettingType.HYPHENATION to context.getString(R.string.prefs_hyphenation_summary),
        TextSettingType.VERSEPERLINE to context.getString(R.string.prefs_verse_per_line_summary),
        TextSettingType.STRONGS to context.getString(R.string.prefs_show_strongs_summary),
        TextSettingType.MORPH to context.getString(R.string.prefs_show_morphology_summary),
        TextSettingType.NON_STRONGS_WORD_ITALIC to context.getString(R.string.prefs_non_strongs_word_italic_summary),
        TextSettingType.FOOTNOTES to context.getString(R.string.prefs_show_footnotes_summary),
        TextSettingType.FOOTNOTES_INLINE to context.getString(R.string.prefs_show_footnotes_inline_summary),
        TextSettingType.XREFS to context.getString(R.string.prefs_show_xrefs_summary),
        TextSettingType.EXPAND_XREFS to context.getString(R.string.prefs_expand_footnotes_summary),
        TextSettingType.VERSENUMBERS to context.getString(R.string.prefs_show_verseno_summary),
        TextSettingType.SECTIONTITLES to context.getString(R.string.prefs_section_title_summary),
        TextSettingType.TITLE_SCROLL_BUTTON to context.getString(R.string.prefs_title_scroll_button_summary),
        TextSettingType.PAGENUMBER to context.getString(R.string.page_number_summary),
        TextSettingType.INFINITE_SCROLL to context.getString(R.string.prefs_infinite_scroll_summary),
        TextSettingType.PAGE_SCROLL_AMOUNT to context.getString(R.string.prefs_page_scroll_amount_summary),
        TextSettingType.SCROLL_HELPER_LINES to context.getString(R.string.prefs_scroll_helper_lines_summary),
        TextSettingType.SCROLL_HELPER_LINE_STYLE to context.getString(R.string.prefs_scroll_helper_line_style_summary),
        TextSettingType.PAGE_BUTTONS to context.getString(R.string.prefs_page_buttons_summary),
        TextSettingType.ORDINALS to context.getString(R.string.prefs_show_ordinals_summary),
        TextSettingType.SHOW_READING_PROGRESS to context.getString(R.string.prefs_show_reading_progress_summary),
        TextSettingType.BOOKMARKS_SHOW to context.getString(R.string.prefs_show_bookmarks_summary),
        TextSettingType.MYNOTES to context.getString(R.string.prefs_show_mynotes_summary),
        TextSettingType.AI_DOC_MARKERS to context.getString(R.string.prefs_show_ai_doc_markers_summary),
        TextSettingType.BOOKMARKS_HIDELABELS to context.getString(R.string.bookmark_settings_hide_labels_summary),
        TextSettingType.MARK_AS_READ_BUTTON to context.getString(R.string.prefs_mark_as_read_button_summary),
        TextSettingType.MEMORIZATION_INDICATORS to context.getString(R.string.prefs_show_memorization_indicators_summary),
        TextSettingType.AUTO_TRACK_READING to context.getString(R.string.prefs_auto_track_reading_summary),
    ),
)

fun buildTextDisplayScreenLabels(context: Context) = TextDisplaySettingsScreenLabels(
    resetContentDescription = context.getString(R.string.reset_settings),
    resetConfirmMessage = context.getString(R.string.reset_are_you_sure),
    // No classic string exists for a single-row "revert to inherited?" confirmation (this
    // long-press interaction is new in the Compose screen -- classic's per-row reset lives
    // inside each value-editor dialog, with no separate confirm step). Reusing the generic
    // bulk-reset confirmation is an accepted, if imprecise, wording (see task report).
    revertMessage = context.getString(R.string.reset_are_you_sure),
    fontSizeDialogTitle = context.getString(R.string.font_size_title),
    topMarginDialogTitle = context.getString(R.string.prefs_top_margin_title),
    lineSpacingDialogTitle = context.getString(R.string.line_spacing_title),
    marginSizeDialogTitle = context.getString(R.string.prefs_margin_size_title),
    // Contain "%d" -- left un-substituted; TextDisplaySettingsScreen/MarginContent does its own
    // .replace("%d", ...).
    marginLeftLabelFormat = context.getString(R.string.pref_left_margin_label_mm),
    marginRightLabelFormat = context.getString(R.string.pref_right_margin_label_mm),
    marginMaxWidthLabelFormat = context.getString(R.string.pref_maximum_width_of_text_label_mm),
    resetToInheritedLabel = context.getString(R.string.reset_generic),
    badgeWorkspace = context.getString(R.string.text_options_inherited_workspace),
    badgeGlobal = context.getString(R.string.text_options_inherited_global),
    okLabel = context.getString(R.string.okay),
    cancelLabel = context.getString(R.string.cancel),
)

fun buildColorSettingsLabels(context: Context) = ColorSettingsLabels(
    dayMode = context.getString(R.string.colors_day_mode_title),
    nightMode = context.getString(R.string.colors_night_mode_title),
    textColor = context.getString(R.string.color_text),
    backgroundColor = context.getString(R.string.color_background),
    noise = context.getString(R.string.prefs_noise_title),
    workspaceColor = context.getString(R.string.color_workspace),
    backgroundImageDay = context.getString(R.string.background_image_day),
    backgroundImageNight = context.getString(R.string.background_image_night),
    opacityDay = context.getString(R.string.background_image_opacity_day),
    opacityNight = context.getString(R.string.background_image_opacity_night),
    change = context.getString(R.string.background_image_change),
    // No standalone R.string.reset exists (only "reset settings"-flavoured strings) -- reuse
    // the same generic reset wording buildScreenLabels() uses for resetToInheritedLabel.
    reset = context.getString(R.string.reset_generic),
)

fun buildBackgroundImageChooserLabels(context: Context) = BackgroundImageChooserLabels(
    title = context.getString(R.string.background_image_title),
    none = context.getString(R.string.background_image_none),
    import = context.getString(R.string.background_image_import),
    empty = context.getString(R.string.background_image_empty),
    importing = context.getString(R.string.background_image_importing),
    deleteTitle = context.getString(R.string.background_image_delete_title),
    deleteConfirm = context.getString(R.string.background_image_delete_confirm),
    delete = context.getString(R.string.delete),
    cancel = context.getString(R.string.cancel),
)

/**
 * Down-samples a background-image file to a thumbnail-sized [ImageBitmap] for the background-
 * image chooser grid, caching by [net.bible.sharedcore.settings.BackgroundImageOption
 * .thumbnailToken] (== module initials). Ports classic
 * `BackgroundImageChooserActivity.Adapter.decodeThumbnail`: down-samples via `inJustDecodeBounds`
 * so a large source photo doesn't fully decode just to draw an 8dp grid tile.
 *
 * Hoisted out of [TextDisplaySettingsComposeActivity] (Settings editor sheets T10) into its own
 * small class, rather than a bare top-level function, because the cache needs a HOME: two hosts
 * (that Activity and [net.bible.android.view.activity.page.screen.ComposeReadingViewHost]'s
 * in-place editor) each render the chooser, and each must own its own instance/cache — a shared
 * top-level cache would leak across them for no benefit, and a stateless function would re-decode
 * on every recomposition. Null-safe throughout: a missing/unreadable file yields `null`, and
 * `BackgroundImageChooserScreen`/`BackgroundImageChooserContent` fall back to a themed placeholder
 * box for that tile.
 */
class BackgroundThumbnailResolver {
    private val cache = mutableMapOf<String, ImageBitmap?>()

    fun resolve(token: String): ImageBitmap? = cache.getOrPut(token) {
        val file = AndBibleAddons.providedBackgroundImages[token]?.file ?: return@getOrPut null
        runCatching {
            BitmapFactory.Options().run {
                inJustDecodeBounds = true
                BitmapFactory.decodeFile(file.path, this)
                var sample = 1
                while (outWidth / sample > 240 || outHeight / sample > 240) sample *= 2
                inJustDecodeBounds = false
                inSampleSize = sample
                BitmapFactory.decodeFile(file.path, this)
            }
        }.getOrNull()?.asImageBitmap()
    }
}
