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
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.EmojiPeople
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
import androidx.compose.material.icons.outlined.Label
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
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.SettingsBackupRestore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.android.control.backup.BackupControl
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.StudyPadDocument
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.bibleview.BibleViewEvents
import net.bible.sharedcore.bibleview.JsCall
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.platform.files.SaveOrShare
import net.bible.sharedui.app.bookmark.StudyPadExportPlatform
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.assembly.IosStringTableHolder
import net.bible.sharedui.ios.ports.IosBookmarkHostPlatform
import net.bible.sharedui.ios.ports.IosDocumentPicker
import net.bible.sharedui.ios.ports.IosStudyPadExportPlatform
import org.koin.mp.KoinPlatform
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.llm.PromptContext
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.textSettingEditorPageFor
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.WindowPaneMenuPlatform
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.reading.host.slots.ReadingHostSlots
import org.crosswire.jsword.book.BookCategory

/**
 * The iOS side of the window (☰) pane menu: what [WindowPaneMenuPlatform] does on iOS.
 *
 * Every platform-routed row is implemented; [IOS_OMITTED_PANE_MENU_ROWS] is empty. The export rows mirror Android's
 * `ReadingCommands`: HTML export asks the window's page to export itself (the answer reaches the window surface's share
 * sheet), StudyPad export hands the window's label to [studyPadExport], and the bookmarks CSV export hands the label's Bible
 * bookmarks to [exportCsv]; the last two do nothing outside a StudyPad window (the builder shows them only there). The LLM
 * actions row opens the prompt selector for the window's current page ([openPromptSelector]).
 *
 * @param emitEvent sends a JS event to the window's page (false/absent window: dropped)
 * @param studyPadExport the StudyPad archive export; [chooseDestination] asks "save or share"
 * @param bookmarksWithLabel the Bible bookmarks of a StudyPad label, in Bible order
 * @param exportCsv the bookmarks CSV export flow
 * @param scope where the asynchronous exports run (the host's main scope)
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
    private val openPromptSelector: (Selection, PromptContext, DocumentCategory?) -> Unit,
    private val emitEvent: (windowId: String, call: JsCall) -> Unit = { windowId, call ->
        if (IosReadingWindows.hasAssembly(windowId)) IosReadingWindows.sessionFor(windowId).emit(call)
    },
    private val studyPadExport: StudyPadExportPlatform = IosStudyPadExportPlatform(),
    private val chooseDestination: suspend () -> SaveOrShare? = {
        val koin = KoinPlatform.getKoin()
        val strings = koin.get<CoreStrings>()
        koin.get<BackupControl>().classicDestinationPrompt(strings.exportDestinationTitle, strings.exportDestinationMessage)
    },
    private val bookmarksWithLabel: suspend (BookmarkEntities.Label) -> List<BookmarkEntities.BibleBookmarkWithNotes> =
        { label -> KoinPlatform.getKoin().get<BookmarkControl>().getBibleBookmarksWithLabel(label) },
    private val exportCsv: suspend (List<BookmarkEntities.BibleBookmarkWithNotes>) -> Unit = { bookmarks ->
        val koin = KoinPlatform.getKoin()
        IosBookmarkHostPlatform(
            { koin.get() }, IosDocumentPicker(), koin.get(), koin.get(),
            { koin.getOrNull<IosStringTableHolder>()?.current ?: IosAppStartup.stringTable }, koin.get(), koin.get(),
        ).exportBookmarksCsv(bookmarks)
    },
    private val scope: CoroutineScope = shell.hostScope,
) : WindowPaneMenuPlatform {
    /** The label of the StudyPad shown in [window], as Android's `ReadingCommands` reads it; null for any other document. */
    private fun studyPadLabel(window: Window): BookmarkEntities.Label? =
        (window.bibleView?.firstDocument as? StudyPadDocument)?.label

    override fun exportHtml(window: Window) = emitEvent(window.id.toString(), BibleViewEvents.exportHtml())

    override fun exportStudyPad(window: Window) {
        val label = studyPadLabel(window) ?: return
        scope.launch { studyPadExport.export(listOf(label), chooseDestination) }
    }

    override fun exportStudyPadCsv(window: Window) {
        val label = studyPadLabel(window) ?: return
        scope.launch { exportCsv(bookmarksWithLabel(label)) }
    }

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

    /** Android's `ReadingCommands.llmActions`: the prompt selector for the window's page; nothing without a document and key. */
    override fun llmActions(window: Window) {
        val currentPage = window.pageManager.currentPage
        val selection = windowMenuSelection(currentPage.currentDocument?.initials, currentPage.key?.getOsisRef()) ?: return
        openPromptSelector(selection, PromptContext.WINDOW_MENU, currentPage.documentCategory)
    }

    override fun copyReference(label: String, url: String) {
        clipboard.write(label, url)
        toast(copiedMessage())
    }
}

/** The whole-page selection the window (☰) menu's AI row hands to the prompt selector; null without a document or key. */
@kotlin.native.HiddenFromObjC
fun windowMenuSelection(bookInitials: String?, osisRef: String?): Selection? =
    if (bookInitials != null && osisRef != null) Selection(bookInitials, osisRef, -1, -1) else null

/** The pane menu rows iOS does not offer (their actions are not implemented, so the builder leaves them out): none. */
@kotlin.native.HiddenFromObjC
val IOS_OMITTED_PANE_MENU_ROWS: Set<String> = emptySet()

/**
 * The platform-routed rows [IosWindowPaneMenuPlatform] implements. Together with [IOS_OMITTED_PANE_MENU_ROWS] it is exactly
 * [net.bible.android.view.activity.page.WindowPaneMenuDispatcher.PLATFORM_ROWS] (pinned by `IosWindowPaneMenuTest`).
 */
@kotlin.native.HiddenFromObjC
val IOS_IMPLEMENTED_PANE_MENU_ROWS: Set<String> = setOf(
    WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS,
    WindowPaneMenuStateBuilder.ID_ADD_WHOLE_PAGE_BOOKMARK,
    WindowPaneMenuStateBuilder.ID_COPY_REFERENCE,
    WindowPaneMenuStateBuilder.ID_LLM_ACTIONS_SUBMENU,
    WindowPaneMenuStateBuilder.ID_EXPORT_HTML,
    WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD,
    WindowPaneMenuStateBuilder.ID_EXPORT_STUDYPAD_CSV,
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
    // overflow menu rows
    "ic_full_screen_24" -> Icons.Outlined.Fullscreen
    "ic_night_mode_24" -> Icons.Outlined.DarkMode
    "ic_baseline_workspace_24" -> Icons.Outlined.Workspaces
    "ic_tilt_to_scroll_24dp" -> Icons.Outlined.ScreenRotation
    "ic_reverse_split_mode_24dp" -> Icons.Outlined.SwapVert
    "ic_window_pinning_24" -> Icons.Outlined.PushPin
    "ic_label_settings_24" -> Icons.Outlined.Label
    else -> null
}

/** Material stand-in for the drawer row icon named [iconKey]; null when unmapped. */
@kotlin.native.HiddenFromObjC
fun iosDrawerIcon(iconKey: String): ImageVector? = when (iconKey) {
    "ic_library_books_white_24dp" -> Icons.Outlined.LibraryBooks
    "ic_search_24dp" -> Icons.Outlined.Search
    "ic_baseline_headphones_24" -> Icons.Outlined.Headphones
    "ic_baseline_description_24" -> Icons.Outlined.Description
    "ic_history_clock_24dp" -> Icons.Outlined.History
    "ic_file_download_24dp" -> Icons.Outlined.Download
    "icon_robot" -> Icons.Outlined.SmartToy
    "ic_settings_white_24dp" -> Icons.Outlined.Settings
    "ic_need_help_24dp" -> Icons.Outlined.HelpOutline
    "ic_baseline_emoji_people_24" -> Icons.Outlined.EmojiPeople
    "ic_baseline_bookmark_24" -> Icons.Outlined.Bookmark
    "ic_reading_plan_24dp" -> Icons.Outlined.EventNote
    "ic_bar_chart_24dp" -> Icons.Outlined.BarChart
    // R3
    "ic_baseline_studypads_24" -> Icons.Outlined.Notes
    "ic_settings_backup_restore_db_24dp" -> Icons.Outlined.SettingsBackupRestore
    "ic_syncdb_24dp" -> Icons.Outlined.Sync
    "ic_help_white_24dp" -> Icons.Outlined.Info
    "baseline_attach_money_24" -> Icons.Outlined.AttachMoney
    "ic_baseline_copyright_24" -> Icons.Outlined.Copyright
    "ic_baseline_people_24" -> Icons.Outlined.People
    "ic_rate_review_white_24dp" -> Icons.Outlined.RateReview
    "ic_bug_report_white_24dp" -> Icons.Outlined.BugReport
    else -> null
}

/** The painter of the drawer row icon [iconKey] for the drawer's `drawerIcon` slot. */
@Composable
@kotlin.native.HiddenFromObjC
fun rememberIosDrawerIcon(iconKey: String): Painter? = iosDrawerIcon(iconKey)?.let { rememberVectorPainter(it) }

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
