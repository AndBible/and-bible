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

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentDictionaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.CurrentMyNotePage
import net.bible.android.control.page.CurrentPage
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.SettingsLevel
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.download.FakeBookFactory
import net.bible.service.sword.epub.epubBackend
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.KeyChooserPage
import net.bible.sharedcore.reading.QuickDocRow
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.reading.host.slots.ReadingHostSlots
import net.bible.sharedui.reading.toolbar.KeyChooserOpening
import net.bible.sharedui.reading.toolbar.ReadingDrawerHost
import net.bible.sharedui.reading.toolbar.ReadingOverflowHost
import net.bible.sharedui.reading.toolbar.ReadingToolbarHost
import net.bible.sharedui.reading.toolbar.WorkspaceCycler
import net.bible.sharedui.reading.toolbar.resolveKeyChooserOpening
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.BookCategory
import platform.Foundation.NSBundle

/**
 * The iOS side of the reading toolbar, overflow menu and drawer: implements the common [ReadingToolbarHost],
 * [ReadingOverflowHost] and [ReadingDrawerHost] over the window and document controls, [ReadingHostState]'s sheets,
 * the navigator and the speak transport. The decisions live in `ReadingToolbarCommands`, `ReadingOverflowController` and
 * `ReadingDrawerMenu` (common, unit tested); this class only reads and writes what they ask for.
 *
 * What is NOT here, by name:
 * - Overflow "Auto assign labels": the workspace label manager's result (applied to the workspace settings) is not
 *   exposed by [IosReadingNavigator]; needs a navigator member that awaits the manager.
 * - Stand-in: a recent text option that is neither a toggle nor sheet-editable opens the full text options screen
 *   (Android calls the option's own dialog).
 * - Drawer rows listed in `ReadingDrawerMenu.OMITTED_ROWS`.
 *
 * [overflowItems] and [overflowExpanded] are the menu's state, handed to `ReadingHostDeps`.
 */
@kotlin.native.HiddenFromObjC
internal class IosReadingToolbarHost(
    private val scope: CoroutineScope,
    private val shell: IosHostShell,
    private val hostState: ReadingHostState,
    private val navigator: IosReadingNavigator,
    private val windowControl: () -> WindowControl,
    private val documentControl: () -> DocumentControl,
    private val settings: AppSettings,
    private val prefItems: () -> TextDisplayPrefItems,
    private val recentSettings: () -> List<TextDisplaySettings.Types>,
    private val globalSettings: () -> TextDisplaySettings,
    private val toolbarState: () -> ToolbarStateService,
    private val speakControl: () -> SpeakControl,
    private val transport: () -> SpeakTransportServiceImpl,
    private val slots: () -> ReadingHostSlots,
    private val switchToWorkspace: suspend (String) -> Unit,
    private val searchModeActive: () -> Boolean,
    /** Flips full screen (the host's `setFullScreen`, which also hides the status bar). */
    private val toggleFullScreen: () -> Unit,
    private val chooserRequests: ChooserRequests,
    private val externalLinks: ExternalLinks,
    private val windowStateService: () -> WindowStateServiceImpl,
    private val fakeBooks: () -> FakeBookFactory,
    /** Whether the AI features are configured: shows the overflow "AI Actions" row. */
    private val llmConfiguredSource: () -> Boolean = { false },
    /** Opens the prompt selector for the workspace menu (the overflow "AI Actions" row). */
    private val showLlmActionsSource: () -> Unit = {},
    private val deviceSettings: IosDeviceSettings = IosDeviceSettings(),
    private val tiltAvailable: () -> Boolean = { iosMotionManager.deviceMotionAvailable },
) : ReadingToolbarHost, ReadingOverflowHost, ReadingDrawerHost {

    /** The overflow menu's rows and open flag; `ReadingOverflowController` fills the rows when the menu opens. */
    val overflowItems = mutableStateOf(emptyList<net.bible.sharedcore.reading.OptionsMenuItem>())
    val overflowExpanded = mutableStateOf(false)

    /** Set by the host once the common menu exists (it needs this object). */
    var rebuildOverflow: () -> List<net.bible.sharedcore.reading.OptionsMenuItem> = { emptyList() }

    private val windows get() = windowControl()
    private val repository get() = windows.windowRepository
    private val activePageManager get() = windows.activeWindowPageManager

    // ----- ReadingToolbarHost: sheets and screens -----

    override fun toggleDrawer() = hostState.toggleDrawer()
    override fun showDocumentSheet() = hostState.showDocumentSheet()
    override fun showWorkspaceSheet() = hostState.showWorkspaceSheet()
    override fun showScopedDocumentSheet(scope: DocumentSheetScope) =
        hostState.showQuickSheet(net.bible.sharedcore.reading.ReadingQuickSheet.Documents(scope))
    override fun showSpeakSettings() = slots().showSpeakSettings()
    override fun showTextSettingEditor(scope: SettingsScope, page: SettingsEditorPage, onReady: () -> Unit) =
        slots().showTextSettingEditor(scope, page, onReady)

    /** Android's `composeSearch`: the reading view's own search (the navigator opens it in place, see its `openReadingSearch`). */
    override fun openSearch() = navigator.openSearch(null)
    override fun openChooseDocument(type: String?) = navigator.openChooseDocument(type)
    override fun openChooseDocument() = navigator.openChooseDocument(null)

    /**
     * Android's `composeStartKeyChooser` (title tap): the quick sheet `KeyChooserRoute` names for the active page (the
     * Grid for a Bible or commentary page), the full-screen chooser only as the fallback ([resolveKeyChooserOpening]).
     * A tap before the repository holds a window must not throw.
     */
    override fun startKeyChooser() {
        val page = try {
            activePageManager.currentPage
        } catch (e: Exception) {
            null
        }
        if (page == null) {
            nsLog("ReadingToolbar: title tap before a window exists")
            return
        }
        val document = page.currentDocument
        scope.launch {
            val opening = resolveKeyChooserOpening(
                page = keyChooserPageOf(page),
                resolveKeys = { kind -> withContext(Dispatchers.Default) { keyChooserKeys(kind) } },
                stillCurrent = { activePageManager.currentPage === page && page.currentDocument == document },
            )
            when (opening) {
                null -> Unit
                KeyChooserOpening.FullScreen -> navigator.startKeyChooser(page)
                is KeyChooserOpening.Sheet -> hostState.showKeyChooserSheet(opening.kind, opening.keys)
            }
        }
    }

    /** Android's `currentKeyChooserPage`, in its order: my-note BEFORE commentary (it extends it), then the general-book four-way test. */
    private fun keyChooserPageOf(page: CurrentPage): KeyChooserPage? = when (page) {
        is CurrentBiblePage -> KeyChooserPage.BIBLE
        is CurrentMyNotePage -> KeyChooserPage.MY_NOTE
        is CurrentCommentaryPage -> KeyChooserPage.COMMENTARY
        is CurrentDictionaryPage -> KeyChooserPage.DICTIONARY
        is CurrentMapPage -> KeyChooserPage.MAP
        is CurrentGeneralBookPage -> {
            val factory = fakeBooks()
            when (val doc = page.currentDocument) {
                factory.journalDocument -> KeyChooserPage.GENERAL_BOOK_STUDY_PAD
                factory.multiDocument -> KeyChooserPage.GENERAL_BOOK_MULTI_DOCUMENT
                else -> if (doc?.isMyDocument == true) KeyChooserPage.GENERAL_BOOK_MY_DOCUMENT else KeyChooserPage.GENERAL_BOOK
            }
        }
        else -> null
    }

    /** The keys behind a key-chooser sheet (Android's `KeyChooserKeys.kt`): a map's global list, an EPUB's table of contents or a general book's list. */
    private suspend fun keyChooserKeys(kind: KeyChooserKind): List<Key> = when (kind) {
        KeyChooserKind.Map -> activePageManager.currentMap.cachedGlobalKeyList ?: emptyList()
        KeyChooserKind.GeneralBook -> {
            val page = activePageManager.currentGeneralBook
            val doc = page.currentDocument
            when {
                doc == null -> emptyList()
                doc.isEpub -> doc.epubBackend?.tocKeys() ?: emptyList()
                else -> page.cachedGlobalKeyList ?: emptyList()
            }
        }
        KeyChooserKind.Grid -> emptyList()
    }

    /** Opens the menu; not while the toolbar shows the search field (it has no overflow button then). */
    override fun openOverflowMenu() {
        if (searchModeActive()) return
        overflowItems.value = rebuildOverflow()
        overflowExpanded.value = true
    }

    // ----- ReadingToolbarHost: documents -----

    override val toolbarButtonSetting: String? get() = settings.getString("toolbar_button_actions", "default")

    override fun currentDocumentInitials(): String? = windows.activeWindow.pageManager.currentPage.currentDocument?.initials

    private fun rows(books: List<Book>) = books.map { QuickDocRow(it.initials, it.language.code, it.abbreviation) }

    override fun biblesForVerse() = rows(documentControl().biblesForVerse)
    override fun commentariesForVerse() = rows(documentControl().commentariesForVerse)
    override fun generalBooksAndDictionaries() =
        rows(SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK) + SwordDocumentFacade.getBooks(BookCategory.DICTIONARY))

    override fun suggestedBibleInitials(): String? = documentControl().suggestedBible?.initials
    override fun suggestedCommentaryInitials(): String? = documentControl().suggestedCommentary?.initials

    /** Android's `setCurrentDocument`: shows the book in the active window and remembers it as the category default. */
    override fun switchDocument(initials: String) {
        val book = SwordDocumentFacade.getDocumentByInitials(initials)
        if (book == null) {
            nsLog("ReadingToolbar: cannot switch to $initials, the document is not installed")
            return
        }
        windows.activeWindow.pageManager.setCurrentDocument(book)
        settings.setString("default-${book.bookCategory.name}", book.initials)
    }

    // ----- ReadingToolbarHost: Strong's -----

    private fun strongsItem(): PrefItemHandle? {
        val window = windows.activeWindow
        val bundle = SettingsBundle(
            level = SettingsLevel.WINDOW,
            pageManagerSettings = window.pageManager.textDisplaySettings,
            workspaceId = repository.id,
            workspaceName = repository.name,
            workspaceSettings = repository.textDisplaySettings,
            globalSettings = globalSettings(),
            windowId = window.id,
        )
        return prefItems().item(bundle, TextDisplaySettings.Types.STRONGS)
    }

    override fun strongsMode(): Int = strongsItem()?.value as? Int ?: 0

    override fun setStrongsMode(mode: Int) {
        val item = strongsItem() ?: return
        item.value = mode
        item.handle()
    }

    override fun activeWindowScope(): SettingsScope =
        SettingsScope.Window(windows.activeWindow.id.toString(), repository.id.toString())

    // ----- ReadingToolbarHost: speak -----

    override fun transportBarVisible(): Boolean = transport().state.value.visible
    override fun setTransportBarVisible(visible: Boolean) = transport().setTransportVisible(visible)
    override fun speakStopped(): Boolean = speakControl().isStopped

    // ----- ReadingToolbarHost: workspaces and toolbar -----

    private val cycler = WorkspaceCycler(
        workspaceIds = { DatabaseContainer.instance.workspaceDb.workspaceDao().allWorkspaces().map { it.id.toString() } },
        currentId = { repository.id.toString() },
        switchTo = { switchToWorkspace(it) },
    )

    /** Android's `cycleWorkspace`: next or previous workspace in list order, wrapping; serialised so a double fling advances twice. */
    override fun cycleWorkspace(forward: Boolean) {
        scope.launch { cycler.cycle(forward) }
    }

    override fun refreshToolbar() {
        try {
            toolbarState().refresh()
        } catch (e: Exception) {
            nsLog("ReadingToolbar: toolbar state refresh failed: ${e.stackTraceToString()}")
        }
    }

    // ----- ReadingOverflowHost -----

    override fun toggleFullScreen() = toggleFullScreen.invoke()

    override fun nightMode(): Boolean = deviceSettings.getBoolean(NIGHT_MODE_KEY, false)

    /** iOS night mode is the manual preference only (Android's sensor and system branches have no iOS counterpart yet). */
    override fun nightModeSwitchable(): Boolean = true
    override fun setNightMode(on: Boolean) = deviceSettings.putBoolean(NIGHT_MODE_KEY, on)

    override fun tiltPossible(): Boolean = tiltAvailable()
    override fun tiltToScroll(): Boolean = repository.workspaceSettings.enableTiltToScroll
    override fun setTiltToScroll(on: Boolean) { repository.workspaceSettings.enableTiltToScroll = on }

    override fun isMultiWindow(): Boolean = windows.isMultiWindow
    override fun reverseSplit(): Boolean = repository.workspaceSettings.enableReverseSplitMode

    override fun setReverseSplit(on: Boolean) {
        repository.workspaceSettings.enableReverseSplitMode = on
        windows.windowSizesChanged()
        windowStateService().notify(WindowChange.LayoutConfigurationChanged)
    }

    override fun autoPin(): Boolean = repository.workspaceSettings.autoPin

    override fun setAutoPin(on: Boolean) {
        repository.workspaceSettings.autoPin = on
        windows.autoPinChanged()
    }

    private fun workspaceBundle() = SettingsBundle(
        level = SettingsLevel.WORKSPACE,
        workspaceId = repository.id,
        workspaceName = repository.name,
        workspaceSettings = repository.textDisplaySettings.apply {
            colors?.workspaceColor = repository.workspaceSettings.workspaceColor
        },
        globalSettings = globalSettings(),
    )

    override fun llmConfigured(): Boolean = llmConfiguredSource()
    override fun showLlmActions() = showLlmActionsSource()

    override fun recentTextOptions(): List<Pair<TextDisplaySettings.Types, PrefItemHandle>> {
        val bundle = workspaceBundle()
        return recentSettings().map { it to prefItems().item(bundle, it) }
    }

    override fun openAllTextOptions() {
        shell.navigate(NavRoutes.textDisplaySettings(scopeLevel = "workspace", workspaceId = repository.id.toString()))
    }

    override fun workspaceScope(): SettingsScope = SettingsScope.Workspace(repository.id.toString())

    override fun applyWorkspaceTextSettings(types: Set<TextDisplaySettings.Types>) {
        repository.updateWindowTextDisplaySettingsValues(types, repository.textDisplaySettings)
        repository.updateAllWindowsTextDisplaySettings()
    }

    // ----- ReadingDrawerHost -----

    override fun showSpeakTransport() = setTransportBarVisible(true)
    override fun showHistorySheet() = hostState.showHistorySheet()
    override fun openDownloads() = navigator.openDownloads(false)

    /** My Documents answers the window that is active now, even if another becomes active before the user picks. */
    override fun openMyDocuments() {
        chooserRequests.asked(ChooserKind.MyDocuments, chooserRequests.activeAsker())
        shell.navigate(NavRoutes.myDocuments())
    }

    override fun openBookmarks() {
        chooserRequests.asked(ChooserKind.Bookmarks, chooserRequests.activeAsker())
        shell.navigate(NavRoutes.bookmarks())
    }

    override fun openReadingProgress() = navigator.openReadingProgress(0)

    override fun openReadingPlan() = shell.navigate(NavRoutes.dailyReading())

    override fun navigate(route: String) = shell.navigate(route)
    override fun openUrl(url: String) = externalLinks.open(url)

    override fun searchable(): Boolean = activePageManager.currentPage.isSearchable
    override fun speakable(): Boolean = activePageManager.currentPage.isSpeakable
    override fun versionName(): String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

    private companion object {
        /** The manual night mode preference (`IosNightModeState` reads the same key). */
        const val NIGHT_MODE_KEY = "night_mode_pref"
    }
}
