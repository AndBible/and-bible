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
package net.bible.sharedui.reading.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.DbDate
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.screen.PaneMenuAnchor
import net.bible.android.view.activity.page.screen.ReadingDialog
import net.bible.android.view.activity.page.screen.WindowButtonsVisibility
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.service.llm.PromptContext
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentQuickTabs
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.navigation.buildDocumentQuickTabs
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingOverlayExclusion
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedui.reading.session.ReadingSheets
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.VerseRange

/**
 * The platform-owned parts [ReadingHostState] cannot reach from common code: the three overlays whose
 * state lives in the host ([closeSpeakSheet], [closeTextSettingsEditor], [dismissLlmSheets]), the
 * actions behind the sheets the session asks for, and the few reads that need a platform API. Every
 * member has an inert default so a host (or a test) supplies only what it exercises.
 */
class ReadingHostHooks(
    /** Closes the Speak settings sheet ([ReadingOverlay.SpeakSheet]). */
    val closeSpeakSheet: () -> Unit = {},
    /** Closes the text-settings editor ([ReadingOverlay.TextSettingsEditor]). */
    val closeTextSettingsEditor: () -> Unit = {},
    /** Dismisses the two SHEET-shaped LLM dialogs if one is showing; the dialog-shaped ones stay. */
    val dismissLlmSheets: () -> Unit = {},
    val openPromptSelector: (Selection, PromptContext, DocumentCategory?) -> Unit = { _, _, _ -> },
    /** Opens the regenerate dialog for a page, against the view of the window that asked. */
    val openRegenerate: (pageId: IdType, windowId: IdType) -> Unit = { _, _ -> },
    val openCompare: (VerseRange) -> Unit = {},
    /** Navigates the reading view to a verse picked in the Grid sheet (no outstanding chooser request). */
    val applyChosenVerse: (String) -> Unit = {},
    /** Platform date formatting for a history row (`"h:mm a, E d MMM "` on Android). */
    val formatHistoryTimestamp: (DbDate) -> String = { "" },
    /** The id of the window whose history the sheet lists; defaults to the active window. */
    val activeWindowId: (() -> IdType)? = null,
    /** The history items of a window, newest first; defaults to [HistoryManager.getHistory]. */
    val historyItemsFor: ((IdType) -> List<HistoryItem>)? = null,
    /** The ☰ menu items for a window, or null when the window no longer exists. */
    val paneMenuItemsFor: (windowId: String) -> List<WindowPaneMenuItem>? = { null },
    /** Every installed (and pseudo) document as a row; BLOCKING, see [ReadingHostState.buildDocumentQuickTabsForHost]. */
    val installedDocRows: () -> List<DocRow> = { emptyList() },
    val recentDocumentInitials: () -> List<String> = { emptyList() },
    /** Initials of the books the "This verse" tab offers, per sheet scope; defaults to [DocumentControl]'s. */
    val forVerseInitials: ((DocumentSheetScope) -> Set<String>)? = null,
)

/**
 * The reading host's overlay and sheet state, in common code: the reading dialog, the quick sheet and
 * its close rules, the verse-chooser request, the history list, share options, document quick tabs,
 * the pane (☰) menu, the drawer request and the window-button auto-hide state. The Android
 * `ComposeReadingViewHost` constructs one and forwards to it; the iOS host will do the same.
 *
 * Also the [ReadingSheets] the session shows its sheets through.
 */
class ReadingHostState(
    val scope: CoroutineScope,
    val settings: AppSettings,
    private val windowControl: WindowControl,
    private val historyManager: HistoryManager,
    private val documentControl: DocumentControl,
    private val hooks: ReadingHostHooks = ReadingHostHooks(),
) : ReadingSheets {

    // --- overlay close rules ---

    /**
     * The single close action for one [ReadingOverlay] member, shared by every
     * `ReadingOverlayExclusion.closedBy(...)` application site.
     */
    fun closeOverlay(overlay: ReadingOverlay) {
        when (overlay) {
            ReadingOverlay.Llm -> hooks.dismissLlmSheets()
            ReadingOverlay.SpeakSheet -> hooks.closeSpeakSheet()
            ReadingOverlay.TextSettingsEditor -> hooks.closeTextSettingsEditor()
            ReadingOverlay.QuickSheet -> closeQuickSheet()
        }
    }

    /** Closes every modal reading-view overlay (not the drawer); see `AppDialogOverlay`'s `onSheetOpening`. */
    fun closeModalOverlays() {
        ReadingOverlay.entries.forEach(::closeOverlay)
    }

    // --- reading dialog ---

    val readingDialog = mutableStateOf<ReadingDialog?>(null)

    override fun showHelp(title: String?, html: String) {
        readingDialog.value = ReadingDialog.Help(title, html)
    }

    override fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit) {
        readingDialog.value = ReadingDialog.ConfirmDeleteDocumentPage(onConfirm)
    }

    /** Runs the showing dialog's confirm action (if it has one) and clears it; a no-op when none is showing. */
    fun confirmReadingDialog() {
        val dialog = readingDialog.value ?: return
        readingDialog.value = null
        if (dialog is ReadingDialog.ConfirmDeleteDocumentPage) dialog.onConfirm()
    }

    fun dismissReadingDialog() {
        readingDialog.value = null
    }

    // --- quick sheet and verse chooser ---

    val quickSheet = mutableStateOf<ReadingQuickSheet?>(null)

    /**
     * The verse an out-of-process caller (`refChooserDialog`, via [openVerseChooserSheetForResult]) is
     * waiting for. Every exit from the sheet MUST resolve it, so both [closeQuickSheet] and
     * [showQuickSheet] abandon it.
     */
    private var pendingChosenVerse: CompletableDeferred<String?>? = null

    /**
     * Opens the Grid quick sheet to pick ONE verse and hands it back instead of navigating. The
     * deferred completes with the osisID, or with `null` if the sheet is dismissed or superseded.
     */
    fun openVerseChooserSheetForResult(): CompletableDeferred<String?> {
        val pending = CompletableDeferred<String?>()
        // Claim the slot AFTER opening: showQuickSheet abandons whatever request was outstanding.
        showQuickSheet(ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = true))
        pendingChosenVerse = pending
        return pending
    }

    /**
     * The Grid sheet's one exit with a verse in hand: answers an outstanding request if there is one,
     * else navigates. Taking ownership BEFORE closing stops [closeQuickSheet]'s abandon from resolving
     * it with `null`.
     */
    fun onGridPassageChosen(osisId: String) {
        val pending = pendingChosenVerse
        pendingChosenVerse = null
        closeQuickSheet()
        if (pending != null) pending.complete(osisId) else hooks.applyChosenVerse(osisId)
    }

    private fun abandonPendingChosenVerse() {
        val pending = pendingChosenVerse ?: return
        pendingChosenVerse = null
        pending.complete(null)
    }

    /** Opens a quick sheet, closing every other modal overlay first. */
    fun showQuickSheet(sheet: ReadingQuickSheet) {
        abandonPendingChosenVerse()
        ReadingOverlayExclusion.closedBy(ReadingOverlay.QuickSheet).forEach(::closeOverlay)
        quickSheet.value = sheet
    }

    fun closeQuickSheet() {
        quickSheet.value = null
        abandonPendingChosenVerse()
    }

    fun showHistorySheet() = showQuickSheet(ReadingQuickSheet.History)

    fun showWorkspaceSheet() = showQuickSheet(ReadingQuickSheet.Workspaces)

    fun showDocumentSheet() = showQuickSheet(ReadingQuickSheet.Documents())

    fun showShareSheet(input: ShareVersesInput) = showQuickSheet(ReadingQuickSheet.Share(input))

    fun showReadHistorySheet(bookId: String, chapter: Int) =
        showQuickSheet(ReadingQuickSheet.ReadHistory(bookId, chapter))

    /**
     * The key list the CURRENTLY OPEN key-chooser sheet is showing, handed in by [showKeyChooserSheet]
     * and never resolved by the sheet itself (a plain `var`: written before the [quickSheet] state that
     * triggers the composition reading it).
     */
    var keyChooserKeys: List<Key> = emptyList()
        private set

    /**
     * The key chooser for the page shapes simple enough for a sheet. [keys] IS PASSED IN, NOT
     * RE-RESOLVED: resolving can be heavy (cold key-list walk, un-cached EPUB TOC) and the caller has
     * already resolved it once to decide whether to open a sheet at all.
     */
    fun showKeyChooserSheet(kind: KeyChooserKind, keys: List<Key>) {
        keyChooserKeys = keys
        showQuickSheet(
            ReadingQuickSheet.KeyChooser(
                kind,
                // The same source the full-screen twin falls back to; read HERE so the other opening
                // ([openVerseChooserSheetForResult]) can differ. Meaningless for the flat list kinds.
                navigateToVerse = kind == KeyChooserKind.Grid &&
                    settings.getBoolean("navigate_to_verse_pref", false),
            ),
        )
    }

    // --- history ---

    /** The listed items, stashed so [revertToHistoryItem] can resolve an entry's `id` (its list index). */
    private var historyItems: List<HistoryItem> = emptyList()

    /** The active window's [HistoryEntry] list (newest first), with the platform's timestamp format. */
    fun historyEntriesForActiveWindow(): List<HistoryEntry> {
        val windowId = hooks.activeWindowId?.invoke() ?: windowControl.activeWindow.id
        historyItems = hooks.historyItemsFor?.invoke(windowId) ?: historyManager.getHistory(windowId)
        return historyItems.mapIndexed { index, item ->
            HistoryEntry(
                id = index,
                title = item.description.toString(),
                timestamp = hooks.formatHistoryTimestamp(item.createdAt),
            )
        }
    }

    fun revertToHistoryItem(id: Int) {
        historyItems[id].revertTo()
    }

    // --- share options ---

    /** The verse-share sheet's toggle state, loaded once from prefs; [updateShareVersesOptions] writes it. */
    var shareVersesOptions by mutableStateOf(loadShareVersesOptions())
        private set

    fun updateShareVersesOptions(options: ShareVersesOptions) {
        shareVersesOptions = options
        persistShareVersesOptions(options)
    }

    private fun loadShareVersesOptions(): ShareVersesOptions = ShareVersesOptions(
        showVerseNumbers = settings.getBoolean(SHARE_VERSE_NUMBERS_KEY, true),
        advertiseApp = settings.getBoolean(SHARE_SHOW_ADD_KEY, true),
        showReference = settings.getBoolean(SHARE_SHOW_REFERENCE_KEY, true),
        abbreviateReference = settings.getBoolean(SHARE_ABBREVIATE_REFERENCE_KEY, true),
        showVersion = settings.getBoolean(SHARE_SHOW_VERSION_KEY, true),
        showNotes = settings.getBoolean(SHARE_SHOW_NOTES_KEY, true),
        showSelectionOnly = settings.getBoolean(SHARE_SHOW_SELECTION_ONLY_KEY, true),
        showEllipsis = settings.getBoolean(SHARE_SHOW_ELLIPSIS_KEY, true),
        showReferenceAtFront = settings.getBoolean(SHARE_SHOW_REFERENCE_AT_FRONT_KEY, true),
        showQuotes = settings.getBoolean(SHARE_SHOW_QUOTES_KEY, false),
        separateVersesWithNewlines = settings.getBoolean(SHARE_SEPARATE_VERSES_NEWLINES_KEY, false),
    )

    /** Persists every toggle back to the keys [loadShareVersesOptions] reads. */
    private fun persistShareVersesOptions(options: ShareVersesOptions) {
        settings.apply {
            setBoolean(SHARE_VERSE_NUMBERS_KEY, options.showVerseNumbers)
            setBoolean(SHARE_SHOW_ADD_KEY, options.advertiseApp)
            setBoolean(SHARE_SHOW_REFERENCE_KEY, options.showReference)
            setBoolean(SHARE_ABBREVIATE_REFERENCE_KEY, options.abbreviateReference)
            setBoolean(SHARE_SHOW_VERSION_KEY, options.showVersion)
            setBoolean(SHARE_SHOW_NOTES_KEY, options.showNotes)
            setBoolean(SHARE_SHOW_SELECTION_ONLY_KEY, options.showSelectionOnly)
            setBoolean(SHARE_SHOW_ELLIPSIS_KEY, options.showEllipsis)
            setBoolean(SHARE_SHOW_REFERENCE_AT_FRONT_KEY, options.showReferenceAtFront)
            setBoolean(SHARE_SHOW_QUOTES_KEY, options.showQuotes)
            setBoolean(SHARE_SEPARATE_VERSES_NEWLINES_KEY, options.separateVersesWithNewlines)
        }
    }

    // --- document quick tabs ---

    /**
     * The document quick sheet's tabs. BLOCKING AND EXPENSIVE (enumerates every installed book through
     * [ReadingHostHooks.installedDocRows]): call it from a background dispatcher, never inside a
     * composition pass.
     */
    fun buildDocumentQuickTabsForHost(scope: DocumentSheetScope): DocumentQuickTabs {
        val rows = hooks.installedDocRows()
        // `initials`, NOT `osisID`: DocRow.docId is the book's initials and so is the MRU's key.
        // The This-verse tab follows the sheet's scope: Bibles for the Bible sheet, commentaries for
        // the Commentary sheet, both for the title sheet.
        val forVerseIds = hooks.forVerseInitials?.invoke(scope) ?: run {
            val forVerseBooks = when (scope) {
                DocumentSheetScope.BIBLE -> documentControl.biblesForVerse
                DocumentSheetScope.COMMENTARY -> documentControl.commentariesForVerse
                DocumentSheetScope.ALL -> documentControl.biblesForVerse + documentControl.commentariesForVerse
            }
            forVerseBooks.map { it.initials }.toSet()
        }
        // The two keys ChooseDocument itself persists (its sticky-language seam and its type-filter
        // spinner), read here so the "Last filter" tab reproduces what the user last looked at.
        // KNOWN: `selected_document_filter_no` is written by the Download destination too, so "Last
        // filter" can reflect a filter last set on the DOWNLOAD screen (the classic key's behaviour).
        val lastLanguageCode = settings.getString("selected_language_code", null)
        val lastLanguage = lastLanguageCode?.let { code -> rows.firstOrNull { it.language.code == code }?.language }
        val lastTypeFilter = DocTypeFilter.entries.getOrElse(
            settings.getInt("selected_document_filter_no", 0),
        ) { DocTypeFilter.ALL }
        return buildDocumentQuickTabs(
            // Every mapped row, unfiltered: dropping the ones a sheet cannot action is
            // [buildDocumentQuickTabs]' own job. `lastLanguage` is deliberately resolved against this
            // FULL list, so a language whose only document is locked does not lose the saved filter.
            installed = rows,
            recentInitials = hooks.recentDocumentInitials(),
            forVerseIds = forVerseIds,
            lastLanguage = lastLanguage,
            lastTypeFilter = lastTypeFilter,
            scope = scope,
        )
    }

    /**
     * The last tab the user picked, as a tab ID, never an index (an empty tab is HIDDEN, so a stored
     * index would restore a different tab). An id naming no visible tab falls back to the first visible.
     */
    fun restoreQuickDocTab(scope: DocumentSheetScope, visible: List<DocumentQuickTab>): String? {
        val saved = settings.getString(scope.tabSettingKey, null)
        return visible.firstOrNull { it.name == saved }?.name ?: visible.firstOrNull()?.name
    }

    fun persistQuickDocTab(scope: DocumentSheetScope, tabId: String) =
        settings.setString(scope.tabSettingKey, tabId)

    // --- pane (☰) menu and window buttons ---

    val windowButtonsVisibility = WindowButtonsVisibility()

    val paneMenuWindowId = mutableStateOf<String?>(null)
    val paneMenuItems = mutableStateOf(emptyList<WindowPaneMenuItem>())
    val paneMenuAnchor = mutableStateOf(PaneMenuAnchor.Pane)

    /**
     * Opens the ☰ menu for [windowId] anchored to [anchor]. Forces the pane buttons visible first
     * (classic `showPopupMenu`), so a menu opened from the rail while the floating button is
     * auto-hidden is still usable.
     */
    fun openPaneMenu(windowId: String, anchor: PaneMenuAnchor) {
        windowButtonsVisibility.onTouch()
        if (!refreshPaneMenuItems(windowId)) return
        paneMenuAnchor.value = anchor
        paneMenuWindowId.value = windowId
    }

    /**
     * Rebuilds the ☰ menu items for [windowId] (a row that asked to stay open changes its own label).
     * False, leaving the items as they were, when the window no longer exists.
     */
    fun refreshPaneMenuItems(windowId: String): Boolean {
        val items = hooks.paneMenuItemsFor(windowId) ?: return false
        paneMenuItems.value = items
        return true
    }

    fun closePaneMenu() { paneMenuWindowId.value = null }

    // --- drawer ---

    /** The open/closed *request*; the host mirrors it into the real drawer state and back. */
    val drawerOpen = mutableStateOf(false)

    val isDrawerOpen: Boolean get() = drawerOpen.value

    fun toggleDrawer() { drawerOpen.value = !drawerOpen.value }
    fun openDrawer() { drawerOpen.value = true }
    fun closeDrawer() { drawerOpen.value = false }

    // --- ReadingSheets ---

    override fun showReadHistory(bookId: String, chapter: Int) = showReadHistorySheet(bookId, chapter)
    override fun showCompare(range: VerseRange) = hooks.openCompare(range)
    override fun showShare(input: ShareVersesInput) = showShareSheet(input)
    override fun showPromptSelector(selection: Selection, context: PromptContext, category: DocumentCategory?) =
        hooks.openPromptSelector(selection, context, category)
    override fun showRegenerate(pageId: IdType, windowId: IdType) = hooks.openRegenerate(pageId, windowId)

    private companion object {
        const val SHARE_VERSE_NUMBERS_KEY = "share_verse_numbers"
        const val SHARE_SHOW_ADD_KEY = "share_show_add"
        const val SHARE_SHOW_REFERENCE_KEY = "share_show_reference"
        const val SHARE_ABBREVIATE_REFERENCE_KEY = "share_abbreviate_reference"
        const val SHARE_SHOW_VERSION_KEY = "share_show_version"
        const val SHARE_SHOW_NOTES_KEY = "show_notes"
        const val SHARE_SHOW_SELECTION_ONLY_KEY = "show_selection_only"
        const val SHARE_SHOW_ELLIPSIS_KEY = "show_ellipsis"
        const val SHARE_SHOW_REFERENCE_AT_FRONT_KEY = "share_show_reference_at_front"
        const val SHARE_SHOW_QUOTES_KEY = "share_show_quotes"
        const val SHARE_SEPARATE_VERSES_NEWLINES_KEY = "share_separate_verses_newlines"
    }
}
