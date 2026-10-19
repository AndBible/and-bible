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

@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.reading

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatAlignJustify
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LabelOff
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.ViewHeadline
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.android.view.activity.page.WindowPaneMenuPlatform
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.reading.host.slots.ReadingHostSlots
import org.crosswire.jsword.book.BookCategory

/**
 * The iOS side of the window (☰) pane menu: what [WindowPaneMenuPlatform] does on iOS.
 *
 * **Left out on iOS (named gaps, the builder omits their rows, see [IOS_OMITTED_PANE_MENU_ROWS]):** HTML export, StudyPad
 * export, StudyPad bookmarks CSV export (the iOS file export flows are still pending ports, `IosStudyPadExportPlatform`), and
 * the LLM actions row (no agent start on iOS yet; `openPromptSelector` is an inert hook).
 */
@kotlin.native.HiddenFromObjC
class IosWindowPaneMenuPlatform(
    private val shell: IosHostShell,
    private val windowControl: () -> WindowControl,
    private val slots: () -> ReadingHostSlots,
    private val clipboard: ClipboardText,
    private val toast: (String) -> Unit,
    private val copiedMessage: () -> String,
    private val bridgeFor: (windowId: String) -> net.bible.sharedcore.bibleview.BibleBridge?,
) : WindowPaneMenuPlatform {
    private fun route(window: Window) = NavRoutes.textDisplaySettings(
        scopeLevel = "window", windowId = window.id.toString(), workspaceId = windowControl().windowRepository.id.toString(),
    )

    /** The window-level text settings screen (the shared root's `settings/textDisplay` destination). */
    override fun openAllTextOptions(window: Window) = shell.navigate(route(window))

    /**
     * A non-toggle "last used" row: the in-place editor sheet at WINDOW scope when the setting has one (the same rule as
     * Android's), else the window's text settings screen, where the row lives.
     */
    override fun openTextOption(window: Window, type: TextDisplaySettings.Types, item: PrefItemHandle, bundle: SettingsBundle): Boolean {
        val page = textSettingEditorPageFor(type.name)
        if (page == null) {
            shell.navigate(route(window))
            return false
        }
        slots().showTextSettingEditor(
            SettingsScope.Window(window.id.toString(), windowControl().windowRepository.id.toString()), page,
        ) { window.bibleView?.updateTextDisplaySettings() }
        return false
    }

    override fun addWholePageBookmark(window: Window, bookInitials: String, osisRef: String) {
        bridgeFor(window.id.toString())?.createWholePageBookmark(bookInitials, osisRef)
    }

    override fun copyReference(label: String, url: String) {
        clipboard.write(label, url)
        toast(copiedMessage())
    }
}

/** The pane menu rows iOS does not offer (their actions are not implemented, so the builder leaves them out). */
@kotlin.native.HiddenFromObjC
val IOS_OMITTED_PANE_MENU_ROWS: Set<String> = setOf(
    WindowPaneMenuStateBuilder.ID_EXPORT_HTML,
    WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD,
    WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV,
    WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU,
)

/** Material stand-in for the Android drawable named [iconKey] (pane menu rows and the text option rows); null when unmapped. */
@kotlin.native.HiddenFromObjC
fun iosPaneMenuIcon(iconKey: String): ImageVector? = when (iconKey) {
    "ic_window_add_outline_black_24dp" -> Icons.Outlined.AddBox
    "ic_window_maximise_24dp", "ic_full_screen_24", "ic_full_screen_by_scrolling_24dp" -> Icons.Outlined.Fullscreen
    "ic_baseline_minimise_24" -> Icons.Outlined.Remove
    "ic_link_black_24dp", "ic_xrefs_24dp", "ic_xrefs_inline_24dp" -> Icons.Outlined.Link
    "ic_window_move_to_24dp" -> Icons.Outlined.DriveFileMove
    "ic_pin" -> Icons.Outlined.PushPin
    "ic_window_sync_24dp" -> Icons.Outlined.Sync
    "ic_baseline_bookmark_24" -> Icons.Outlined.Bookmark
    "ic_text_options_24dp" -> Icons.Outlined.TextFields
    "ic_content_copy_black_24dp" -> Icons.Outlined.ContentCopy
    "baseline_content_paste_24" -> Icons.Outlined.ContentPaste
    "ic_baseline_headphones_24" -> Icons.Outlined.Headphones
    "ic_close_white_24dp" -> Icons.Outlined.Close
    "ic_strongs_greek", "ic_strongs_hebrew" -> Icons.Outlined.Translate
    "ic_bookmarks_show_24dp" -> Icons.Outlined.Bookmarks
    "ic_labels_hide_24dp" -> Icons.Outlined.LabelOff
    "ic_morphology_24dp" -> Icons.Outlined.Spellcheck
    "ic_footnotes_24dp" -> Icons.Outlined.Notes
    "ic_section_titles_24dp" -> Icons.Outlined.Title
    "ic_chapter_verse_numbers_24dp" -> Icons.Outlined.FormatListNumbered
    "ic_color_settings_24dp" -> Icons.Outlined.Palette
    "ic_font_size_24dp" -> Icons.Outlined.FormatSize
    "ic_font_family_24dp" -> Icons.Outlined.FontDownload
    "ic_margin_size_24dp" -> Icons.Outlined.FormatIndentIncrease
    "ic_margin_top_24dp" -> Icons.Outlined.VerticalAlignTop
    "ic_line_spacing_24dp" -> Icons.Outlined.FormatLineSpacing
    "ic_red_letter_24dp" -> Icons.Outlined.FormatColorText
    "ic_one_verse_per_line_24dp" -> Icons.Outlined.ViewHeadline
    "ic_justify_text_24dp" -> Icons.Outlined.FormatAlignJustify
    "ic_hyphenation_24dp" -> Icons.Outlined.TextFormat
    "ic_note_regular_24dp" -> Icons.Outlined.StickyNote2
    "ic_format_italic_24dp" -> Icons.Outlined.FormatItalic
    "ic_baseline_check_circle_24" -> Icons.Outlined.CheckCircle
    "ic_baseline_star_24" -> Icons.Outlined.Star
    else -> null
}

/** The painter of [iconKey] for the pane menu's `menuIcon` slot (composable: the painter is remembered). */
@Composable
@kotlin.native.HiddenFromObjC
fun rememberIosPaneMenuIcon(iconKey: String): Painter? = iosPaneMenuIcon(iconKey)?.let { rememberVectorPainter(it) }

/** The doc-type glyphs of the rail's window buttons (Android's `BookCategory.imageResource`), made once in composition. */
@kotlin.native.HiddenFromObjC
class IosPaneIcons(
    val bible: Painter,
    val commentary: Painter,
    val dictionary: Painter,
    val map: Painter,
    val generalBook: Painter,
    val addon: Painter,
) {
    /** The glyph for [category], the general book's for any other (Android's fallback). */
    fun forCategory(category: BookCategory?): Painter? = when (category) {
        null -> null
        BookCategory.BIBLE -> bible
        BookCategory.COMMENTARY -> commentary
        BookCategory.DICTIONARY -> dictionary
        BookCategory.MAPS -> map
        BookCategory.AND_BIBLE -> addon
        else -> generalBook
    }
}

@Composable
@kotlin.native.HiddenFromObjC
fun rememberIosPaneIcons(): IosPaneIcons = IosPaneIcons(
    bible = rememberVectorPainter(Icons.Outlined.Book),
    commentary = rememberVectorPainter(Icons.AutoMirrored.Outlined.MenuBook),
    dictionary = rememberVectorPainter(Icons.Outlined.Spellcheck),
    map = rememberVectorPainter(Icons.Outlined.Map),
    generalBook = rememberVectorPainter(Icons.AutoMirrored.Outlined.MenuBook),
    addon = rememberVectorPainter(Icons.Outlined.Extension),
)
