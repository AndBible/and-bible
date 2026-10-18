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
package net.bible.android.view.activity.page.screen

import android.text.format.DateFormat.format
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.event.UserMessages
import net.bible.android.control.PageChange
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.navigation.NavigationControl
import net.bible.android.control.page.CurrentBiblePage
import net.bible.android.control.page.CurrentCommentaryPage
import net.bible.android.control.page.CurrentDictionaryPage
import net.bible.android.control.page.CurrentGeneralBookPage
import net.bible.android.control.page.CurrentMapPage
import net.bible.android.control.page.CurrentMyNotePage
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.progress.ReadingProgressServiceImpl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.base.FailClosedLinkRouting
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.GridPassageControllerFactory
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.KeyIsNull
import net.bible.android.view.activity.page.ReadingCommands
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.android.view.activity.page.androidBibleView
import net.bible.android.view.activity.page.bibleViewBackgroundColorFor
import net.bible.android.view.activity.settings.BackgroundThumbnailResolver
import net.bible.service.common.CommonUtils
import net.bible.service.common.RecentDocumentsStore
import net.bible.service.device.ScreenSettings
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.hideFromSelector
import net.bible.service.history.HistoryManager
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.AgentForegroundService
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.ai.reading.ReadingLlmDialogController
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentQuickTabs
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.KeyChooserPage
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingOverlayExclusion
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.reading.bibleReferenceOverlayVisible
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchMode
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakSheetStack
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.reading.ReadingLlmDialogs
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.host.LocalReadingPane
import net.bible.sharedui.reading.host.ReadingHost
import net.bible.sharedui.reading.host.ReadingHostDeps
import net.bible.sharedui.reading.host.ReadingHostHooks
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.reading.host.slots.ReadingHostSlots
import net.bible.sharedui.reading.host.slots.ReadingSearchServices
import net.bible.sharedui.reading.host.slots.ReadingSearchState
import net.bible.android.view.activity.nav.ports.AndroidExternalLinks
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.ImagePicker
import net.bible.sharedui.app.ports.TextShare
import net.bible.android.view.activity.nav.ports.AndroidClipboardText
import net.bible.android.view.activity.nav.ports.AndroidTextShare
import net.bible.android.view.activity.page.AndroidReadingNavigator
import net.bible.sharedui.strings.AndroidStrings
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.textOptionDrawableRes
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.passage.Key
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Mounts the Compose reading view into its host Activity's content. It replaced the classic
 * `SplitBibleArea` build, which [DocumentViewManager] used to choose between; that build is gone
 * and this is now the only reading view there is. Plan A kept the classic toolbar/drawer chrome;
 * Plan B (this task) hosts the Compose `ReadingToolbar` instead: [install] asks the host to hide
 * whatever classic toolbar row it draws ([ReadingHostActivity.hideClassicToolbarRow]) and
 * re-anchors [container] (classic's `binding.mainBibleView`) from below the divider to the parent
 * top, so the Compose toolbar — which applies its own
 * `Modifier.windowInsetsPadding(WindowInsets.statusBars)` — owns the top inset instead. On the
 * classic host the drawer (`binding.drawerLayout`) stays a classic `View`; only the toolbar row
 * moves into Compose. Each pane hosts the window's existing
 * [net.bible.android.view.activity.page.BibleView] via [AndroidView] wrapping the host's
 * [ReadingCommands.bibleViewFactory] — the WebView/JS bridge stays an unmodified black box.
 *
 * **Reading-host re-typing R6d: this class no longer names `MainBibleActivity` in any type
 * position.** It takes R4's [ReadingHostActivity], widened once by this task from seven members to
 * twelve — see that interface's kdoc for the five additions and for what `NavHostComposeActivity`
 * supplies for each. The 72 host references measured at the start of this task resolve as: 30 were
 * already on the interface (`getString`, `lifecycleScope`, `fullScreen` and the four chrome calls),
 * 33 go through [ReadingHostActivity.readingCommands], 2 through
 * [ReadingHostActivity.readingInsets], 4 through [ReadingHostActivity.hostActivity], 2 became
 * [ReadingHostActivity.hideClassicToolbarRow], and `drawerRateVisible` turned out to be
 * host-independent and moved to [DrawerMenuStateBuilder]. **No cast, anywhere** (addendum Ruling
 * F): recovering the old Activity surface with a downcast -- however it is spelled -- would satisfy
 * the guards' regexes while leaving the coupling intact, and would be a `ClassCastException` the
 * moment slice 7 Task 12 makes `NavHostComposeActivity` the launcher.
 *
 * NB the prose above does not SPELL that downcast, and no comment in this file may: both of
 * `CollaboratorTypeGuardTest`'s first two scans read RAW text, so "<the cast keyword> <the
 * Activity's name>" and "<colon> <the Activity's name>" read as real code to them. `ReadingCommands`
 * carries the same warning for the same reason.
 *
 * The two surviving `MainBibleActivity` tokens in this file are a companion constant
 * (`WORKSPACE_CHANGED`) and a nested class (`KeyIsNull`). Decoupling the
 * TYPE does not remove them and slice 7 Task 13 re-homes them; `CollaboratorTypeGuardTest`
 * allow-lists exactly those two, visibly, and fails when an allow-list entry goes stale
 * (addendum Ruling E).
 */
class ComposeReadingViewHost(private val activity: ReadingHostActivity) : KoinComponent {
    private val fakeBookFactory: FakeBookFactory get() = org.koin.java.KoinJavaComponent.get(FakeBookFactory::class.java)

    private val windowState: WindowStateServiceImpl by inject()
    private val recentDocumentsStore: RecentDocumentsStore by inject()
    private val subscriptions = Subscriptions()
    private val commands: WindowCommands by inject()
    private val toolbarStateService: ToolbarStateService by inject()
    private val readingLlmService: ReadingLlmService by inject()
    private val agentSessionService: AgentSessionService by inject()
    private val speakTransportService: SpeakTransportService by inject()
    private val speakSettingsService: SpeakSettingsService by inject()

    /** Round 13a: the Speak sheet's verse-picker page drives the SAME grid seams
     *  `GridChoosePassageComposeActivity` does (`GridPassageHostSupport.kt`), and those need the
     *  versification + the active window's current key. Neither was injected here before. */
    private val navigationControl: NavigationControl by inject()
    private val windowControl: WindowControl by inject()
    private val documentControl: DocumentControl by inject()
    private val historyManager: HistoryManager by inject()
    private val speakControl: SpeakControl by inject()
    private val linkControl: LinkControl by inject()

    /** Owns [readingLlmDialogs]' coroutine work (dialog open/execute/dismiss). Cancelled in
     *  [dispose] — one host per activity (re-)creation, mirroring the [subscriptions]
     *  lifecycle right below. */
    private val hostScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * The overlay/sheet/dialog/history/share/pane-menu/drawer state, in common code (`:sharedUi`
     * `ReadingHostState`); this class forwards to it and supplies what only the Android host can: the
     * three overlays it still owns, the actions behind the sheets, date formatting and document rows.
     * Declared BEFORE [readingLlmDialogs] and after the injected collaborators it reads; every hook is
     * a lambda that runs on a user action, long after construction, so the properties it names below
     * (`speakSheet`, `textSettingsEditor`, `paneMenuStateBuilder`) may be declared further down.
     */
    internal val hostState = ReadingHostState(
        scope = hostScope,
        settings = CommonUtils.settings,
        windowControl = windowControl,
        historyManager = historyManager,
        documentControl = documentControl,
        hooks = ReadingHostHooks(
            closeSpeakSheet = { slots.speakSheet.close() },
            closeTextSettingsEditor = { textSettingsEditor.close() },
            dismissLlmSheets = {
                when (readingLlmDialogs.state.value.dialog) {
                    is ReadingLlmDialog.PromptSelector, is ReadingLlmDialog.ModelSelection -> readingLlmDialogs.dismiss()
                    is ReadingLlmDialog.None, is ReadingLlmDialog.SpecifyBeforeRun, is ReadingLlmDialog.Regenerate -> Unit
                }
            },
            openPromptSelector = { selection, context, category -> showPromptSelector(selection, context, category) },
            // The BibleView adapter still calls `showRegenerate(pageId, bibleView)` directly (it has the view);
            // this arm serves a caller that holds the page id and the asking window's id, and uses THAT
            // window's view (never the active window's, which can be a different pane).
            openRegenerate = { pageId, windowId ->
                activity.hostWindowRepository.getWindow(windowId)?.androidBibleView?.let { showRegenerate(pageId, it) }
            },
            openCompare = { range -> linkControl.openCompare(range) },
            applyChosenVerse = { osisId -> activity.readingCommands.applyChosenVerse(osisId) },
            formatHistoryTimestamp = { createdAt -> format("h:mm a, E d MMM ", createdAt).toString() },
            paneMenuItemsFor = { windowId ->
                activity.hostWindowRepository.getWindow(IdType(windowId))?.let { paneMenuStateBuilder.build(it) }
            },
            installedDocRows = {
                val books = SwordDocumentFacade.documents +
                    fakeBookFactory.pseudoDocuments.filterNot { it.hideFromSelector }
                val mapper = DocRowMapper(downloadControl, books)
                books.map { mapper.toDocRow(it) }
            },
            recentDocumentInitials = { recentDocumentsStore.read() },
        ),
    )

    /**
     * State machine driving the reading-view LLM surfaces (Batch 12e-A Task 6): prompt selector,
     * specify-before-run, model selection, regenerate-confirm. Not `private` so
     * `ComposeReadingViewHostTest`-style tests can assert against it directly, mirroring
     * [controller]'s visibility below. Rendered by [mountComposeView] (see [install]) as a sibling
     * of `ReadingViewScreen`; its `onExecute`/`onRegenerate` callbacks are wired by
     * [showPromptSelector]/[showRegenerate].
     *
     * **Round 14a: two of those four arms are `ModalBottomSheet`s now, so opening one has to close
     * whatever other modal overlay is up.** Sheet-over-sheet is banned port-wide, and the three
     * modal overlays of this host are gated by three INDEPENDENT states, so nothing structural
     * prevented two being open — this round is what made it reachable (spec §5).
     * [ReadingOverlayExclusion] holds the rule (pure, unit-tested in `:sharedCore`, no Compose); this
     * is the only place that applies it. The `when` is exhaustive on purpose: a fourth modal overlay
     * must be given a closer here or the build fails.
     *
     * `speakSheet` and `textSettingsEditor` are declared BELOW this property. Reading them from
     * inside this lambda is safe because the lambda runs on a user action, long after the constructor
     * — do not hoist either read out of it.
     */
    val readingLlmDialogs = ReadingLlmDialogController(
        readingLlmService,
        hostScope,
        onSheetOpening = { ReadingOverlayExclusion.closedBy(ReadingOverlay.Llm).forEach(::closeOverlay) },
    )

    /**
     * The single close action for one [ReadingOverlay] member — shared by every
     * `ReadingOverlayExclusion.closedBy(...)` application site (below, and in [showQuickSheet],
     * [showTextSettingEditor] and [showSpeakSettings]) and by [closeModalOverlays] (spec Task 5,
     * `AppDialogOverlay`'s `onSheetOpening`), instead of repeating the same four-way `when` at each
     * call site.
     *
     * **[ReadingOverlay.Llm] only dismisses the two SHEET-shaped arms.** `readingLlmDialogs`'
     * `PromptSelector`/`ModelSelection` are `ModalBottomSheet`s and are exactly what this exclusion
     * rule is about; `SpecifyBeforeRun`/`Regenerate` are plain `AlertDialog`s, and
     * `ReadingOverlayExclusion`'s own kdoc says a dialog over a sheet is fine — unconditionally
     * dismissing them here (platform-dialog removal Task 18's fix) used to silently drop a pending
     * LLM answer (e.g. free-text instructions the user had already typed) the moment ANY other
     * overlay/sheet opened, including an unrelated app-wide one via [closeModalOverlays].
     */
    private fun closeOverlay(overlay: ReadingOverlay): Unit = hostState.closeOverlay(overlay)

    /**
     * Close every modal reading-view overlay (spec Task 5): called from `AppDialogOverlay`'s
     * `onSheetOpening` when an app-wide dialog/sheet is about to be shown, so it never stacks under
     * (or is stacked under by) one of these.
     */
    internal fun closeModalOverlays() = hostState.closeModalOverlays()

    /**
     * Platform-dialog removal Task 18: the reading view's own dialogs (see [ReadingDialog]'s kdoc
     * for why this is not a [ReadingOverlay]). ONE state, rendered through the one host-private
     * slot [ReadingDialogSlot] — the same shape as [quickSheet]/[QuickSheetContent].
     */
    internal val readingDialog get() = hostState.readingDialog

    /** `BibleJavascriptInterface.helpDialog`/`.helpBookmarks`'s entry point. */
    internal fun showHelp(title: String?, html: String) = hostState.showHelp(title, html)

    /** `BibleJavascriptInterface.deleteMyDocumentPage`'s entry point. */
    internal fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit) = hostState.showDeleteDocumentPageConfirm(onConfirm)

    /** Runs the showing dialog's confirm action (if any) and clears it; see [ReadingHostState.confirmReadingDialog]. */
    internal fun confirmReadingDialog() = hostState.confirmReadingDialog()

    /** Dismisses whatever [ReadingDialog] is showing, running no action. */
    internal fun dismissReadingDialog() = hostState.dismissReadingDialog()

    /** Which quick sheet is open over the reading view, if any (see [ReadingQuickSheet]). */
    internal val quickSheet get() = hostState.quickSheet

    /** Opens the Grid sheet to pick ONE verse; see [ReadingHostState.openVerseChooserSheetForResult]. */
    internal fun openVerseChooserSheetForResult(): CompletableDeferred<String?> = hostState.openVerseChooserSheetForResult()

    /** The Grid sheet's one exit with a verse in hand; see [ReadingHostState.onGridPassageChosen]. */
    internal fun onGridPassageChosen(osisId: String) = hostState.onGridPassageChosen(osisId)

    /** Open a quick sheet, closing every other modal overlay first (spec §4.1). */
    internal fun showQuickSheet(sheet: ReadingQuickSheet) = hostState.showQuickSheet(sheet)

    internal fun closeQuickSheet() = hostState.closeQuickSheet()

    /** Round 15b: the reading view's History list as a quick sheet (spec §4.3). */
    internal fun showHistorySheet() = hostState.showHistorySheet()

    private val workspaceService: WorkspaceService by inject()

    /** Round 15b: the workspace QUICK switch (spec §4.4). The full selector is the footer row. */
    internal fun showWorkspaceSheet() = hostState.showWorkspaceSheet()

    private val downloadControl: DownloadControl by inject()

    /** Round 15b: the document QUICK switch (spec §4.5). ChooseDocument is the footer row. */
    internal fun showDocumentSheet() = hostState.showDocumentSheet()

    /**
     * Task 26 (platform-dialog removal, run 3): the verse-share sheet's toggle state, loaded once
     * from prefs and kept live for this host's whole life. [updateShareVersesOptions] is the only
     * writer — every switch in [ShareVersesSheet] calls it, which both updates this state (so the
     * preview redraws) and persists it (so it survives a reopen, exactly as the classic
     * `ShareWidget`'s per-click `CommonUtils.settings.setBoolean(...)` did).
     */
    internal val shareVersesOptions get() = hostState.shareVersesOptions

    /**
     * `BibleView`'s "Share" selection action and `BibleJavascriptInterface`'s two share entry
     * points (bookmark share, JS selection share) all resolve a [ShareVersesInput] via
     * `SwordContentFacade.buildShareVersesInput` and call this.
     */
    internal fun showShareSheet(input: ShareVersesInput) = hostState.showShareSheet(input)

    internal fun updateShareVersesOptions(options: ShareVersesOptions) = hostState.updateShareVersesOptions(options)

    private val readingProgressService: ReadingProgressServiceImpl by inject()

    /**
     * Task 27 (platform-dialog removal, run 3): the reading view's per-chapter read-history sheet.
     * `BibleJavascriptInterface.openChapterReadHistory` is the only caller — it replaces classic
     * `ReadHistoryDialog.showForChapter`, which it already resolves the tapped verse's KJV book/
     * chapter for.
     */
    internal fun showReadHistorySheet(bookId: String, chapter: Int) = hostState.showReadHistorySheet(bookId, chapter)

    /**
     * The document quick sheet's three tabs, built from the same inputs the full ChooseDocument
     * screen uses (spec §4.5).
     *
     * BLOCKING AND EXPENSIVE — enumerating every installed book and asking `downloadControl` for
     * each one's status is fine on a background dispatcher and is emphatically not fine inside a
     * composition pass. The only caller runs it under `withContext(Dispatchers.Default)`.
     *
     * The book list is `ChooseDocumentComposeActivity.loadDocuments`' verbatim, and the mapping goes
     * through the shared [DocRowMapper], so a document's language grouping and category are
     * identical in the sheet and in the full screen.
     */
    internal fun buildDocumentQuickTabsForHost(scope: DocumentSheetScope): DocumentQuickTabs =
        hostState.buildDocumentQuickTabsForHost(scope)

    /**
     * The last tab the user picked, as a tab ID — never an index. An empty tab is HIDDEN, so
     * `visible`'s shape differs between openings (no MRU yet, no commentary for this verse, …) and a
     * stored index would restore a different tab the first time that happened. A stored id that
     * names no currently-visible tab simply falls back to the first visible one. Each [scope] keeps
     * its own saved tab ([DocumentSheetScope.tabSettingKey]).
     */
    internal fun restoreQuickDocTab(scope: DocumentSheetScope, visible: List<DocumentQuickTab>): String? =
        hostState.restoreQuickDocTab(scope, visible)

    internal fun persistQuickDocTab(scope: DocumentSheetScope, tabId: String) =
        hostState.persistQuickDocTab(scope, tabId)

    /**
     * Round 15b: the key chooser for the three page shapes simple enough for a sheet (spec §4.6).
     * Which shapes those are is `KeyChooserRoute`'s decision, never this function's.
     *
     * **[keys] IS PASSED IN, NOT RE-RESOLVED, AND THAT IS A PERFORMANCE CONTRACT — do not
     * "simplify" this back to a resolve inside the sheet.** The caller has already resolved the list
     * to decide whether to open a sheet at all ([keyChooserSheetHasRows]), and resolving a second
     * time is not free: `CurrentPageBase.cachedGlobalKeyList` is memoised, but a COLD cache walks the
     * whole `globalKeyList` — work this repo already treats as too heavy for the main thread
     * (`ChooseDictionaryWordComposeActivity` wraps exactly it in `Dispatchers.IO`) — and
     * `EpubBackendState.tocKeys` is **not cached at all**: every access re-runs two XPath passes and
     * rebuilds every `Key`. So an EPUB would pay the full cost TWICE, both times on the UI thread,
     * blocking a reading-view frame on a title tap. The classic path paid it once, behind an activity
     * transition. One resolution also means the emptiness decision and the rows on screen can never
     * disagree — the `keyId`-is-an-index contract depends on exactly that.
     */
    internal fun showKeyChooserSheet(kind: KeyChooserKind, keys: List<Key>) = hostState.showKeyChooserSheet(kind, keys)

    private val mapPage: CurrentMapPage get() = windowControl.activeWindowPageManager.currentMap
    private val generalBookPage: CurrentGeneralBookPage get() = windowControl.activeWindowPageManager.currentGeneralBook

    /**
     * Maps the active page onto [KeyChooserPage]; null when nothing here recognises it.
     *
     * Mirrors `AndroidKeyChooserLauncher`'s dispatch, including its ORDER.
     * [CurrentMyNotePage] extends [CurrentCommentaryPage] and shares its launcher arm,
     * so the my-note check must come FIRST — see [KeyChooserPage]'s kdoc, and
     * `KeyChooserDispatchGuardTest`, which fails if these two lines are ever swapped back.
     * The general-book four-way test follows `AndroidKeyChooserLauncher`, in its own
     * order (journal → multi-document → my-document → plain).
     */
    internal fun currentKeyChooserPage(): KeyChooserPage? {
        val page = windowControl.activeWindowPageManager.currentPage
        return when (page) {
            is CurrentBiblePage -> KeyChooserPage.BIBLE
            is CurrentMyNotePage -> KeyChooserPage.MY_NOTE
            is CurrentCommentaryPage -> KeyChooserPage.COMMENTARY
            is CurrentDictionaryPage -> KeyChooserPage.DICTIONARY
            is CurrentMapPage -> KeyChooserPage.MAP
            is CurrentGeneralBookPage -> when (val doc = page.currentDocument) {
                fakeBookFactory.journalDocument -> KeyChooserPage.GENERAL_BOOK_STUDY_PAD
                fakeBookFactory.multiDocument -> KeyChooserPage.GENERAL_BOOK_MULTI_DOCUMENT
                else -> if (doc?.isMyDocument == true) {
                    KeyChooserPage.GENERAL_BOOK_MY_DOCUMENT
                } else {
                    KeyChooserPage.GENERAL_BOOK
                }
            }
            else -> null
        }
    }

    /**
     * The keys behind a key-chooser sheet, resolved from the extension the full-screen chooser
     * activities use (`KeyChooserKeys.kt`), so the sheet and the screen can never offer different
     * lists. A row's id is the INDEX into the returned list, which is why every caller must resolve
     * ONCE and hold the result for as long as its rows are on screen.
     *
     * [KeyChooserKind.Grid] has no key list at all — it is driven by `GridChoosePassageController` —
     * so it resolves to empty and is excluded from [keyChooserSheetHasRows]'s emptiness rule.
     */
    internal suspend fun resolveKeyChooserKeys(kind: KeyChooserKind): List<Key> = when (kind) {
        KeyChooserKind.Map -> mapPage.keyChooserKeys()
        KeyChooserKind.GeneralBook -> generalBookPage.keyChooserKeys()
        KeyChooserKind.Grid -> emptyList()
    }

    /**
     * Whether [kind] has anything to put in a sheet, given the list [resolveKeyChooserKeys] already
     * returned — the routing precondition, deliberately NOT a guard inside the sheet body (Task 9
     * amendment E3).
     *
     * An EMPTY key list is real behaviour, not a degenerate case: both chooser activities answer it
     * by returning their fallback selection and finishing without ever drawing a list
     * (`ChooseMapKeyComposeActivity`/`ChooseGeneralBookKeyComposeActivity` `onCreate`, and for a
     * general book that fallback is `globalKeyList.first()`). A sheet cannot reproduce that, and an
     * empty sheet with only a ✕ is strictly worse than what exists today — so an empty list falls
     * through to the Activity exactly as an unrecognised page or a null host does.
     *
     * Takes the resolved list rather than resolving it, so the caller resolves ONCE and the Grid
     * exemption — it has no key list at all — stays here rather than leaking into the caller.
     */
    internal fun keyChooserSheetHasRows(kind: KeyChooserKind, keys: List<Key>): Boolean =
        kind == KeyChooserKind.Grid || keys.isNotEmpty()

    // The REVERSE directions of the same rule live at the two other opening sites --
    // [showTextSettingEditor] and [showSpeakSettings] -- and were APPLIED AT THE 14a/14a-2 MERGE,
    // which is where round 14a's status entry deferred them to. They could not be written in 14a
    // itself: both methods sit inside the edit region the sibling container owned that round, and
    // manufacturing a hunk there is the one thing the two-container fork existed to avoid. Neither
    // direction was ever reachable by touch (an open sheet's scrim covers the toolbar, the speak
    // bar's cog and the menu, which are the only ways in), so the gap was a completeness gap in the
    // model, not a live defect -- but `ReadingOverlayExclusion` states TOTAL mutual exclusion, and a
    // rule applied in one of three directions is the kind of half-truth the next round would read as
    // coverage. `ReadingOverlayExclusionWiringGuardTest` now pins all three, parsing the overlay list
    // off the enum so a fourth overlay fails there the moment it exists.

    /**
     * State holder for the reading-view agent-log panel (Batch 12e-B Task 6) — the compose-path
     * counterpart of classic `AgentLogWidget`. Not `private`, mirroring [readingLlmDialogs]/
     * [controller] below, so `AgentLogHostTest`-style tests can assert against it directly.
     * [onCompletedToast]/[onOpenRawLog] are verbatim mirrors of classic `AgentLogWidget`'s
     * `UserMessages.toast(R.string.ai_task_completed)` / `openRawLog()`. Rendered by
     * [mountComposeView] (see [install]) as `ReadingViewScreen`'s `agentLog` slot (Task 5).
     */
    val agentLog = AgentLogController(
        agentSessionService,
        hostScope,
        onCompletedToast = { UserMessages.toast(R.string.ai_task_completed) },
        onOpenRawLog = {
            val intent = NavHostComposeActivity.intentFor(
                activity.hostContext,
                NavRoutes.rawLlmLog(workspaceId = agentSessionService.currentWorkspaceId()),
            )
            activity.hostActivity.startActivity(intent)
        },
    )

    /**
     * State holder for the reading-view Speak transport bar (Batch 12f Task 6) — the compose-path
     * counterpart of classic `SpeakTransportWidget`. Not `private`, mirroring [readingLlmDialogs]/
     * [agentLog] above, for the same test-visibility reason. Visibility flows entirely from
     * [speakTransportService] (bridged from `NavHostComposeActivity.transportBarVisible` via
     * `setTransportVisible`), NOT from a host-owned flag — the `onConfig` seam below is
     * the only host-supplied one (round 13a: it opens the Speak settings SHEET over the reading
     * view via [showSpeakSettings]; the `Screen.BibleSpeak` route it replaced was removed from the
     * enum in Batch Z-late's epilogue). Rendered by [mountComposeView] (see [install]) as `ReadingViewScreen`'s `speakBar`
     * slot (Task 5).
     */
    val speakTransport = SpeakTransportController(
        speakTransportService,
        speakSettingsService,
        hostScope,
        onConfig = { showSpeakSettings() },
    )

    private val bibleSearchService: BibleSearchService by inject()
    private val searchIndexService: SearchIndexService by inject()
    private val searchResultsCache: SearchResultsCache by inject()
    private val searchControl: SearchControl by inject()
    private val epubSearchService: EpubSearchService by inject()

    /**
     * The reading view's search (common code, `:sharedUi`): settings, session controller, results,
     * the index feed and the run/leave logic. [searchPlatform] is what only Android can do;
     * `onLeaveFullScreen` uses the [MainBibleActivity.fullScreen] setter because `toggleFullScreen` is
     * private (assigning `false` is idempotent there). The results run on the activity's
     * `lifecycleScope`, so a destroyed activity cancels a search in flight.
     */
    private val searchPlatform = AndroidReadingSearchPlatform(
        currentDocument = { documentControl.currentDocument },
        currentBookName = { searchControl.currentBookName },
        documentInfo = { searchDocumentInfo(it) },
        windowControl = { windowControl },
        linkControl = { linkControl },
        fakeBookFactory = { fakeBookFactory },
    )

    internal val searchState = ReadingSearchState(
        settings = CommonUtils.settings,
        scope = hostScope,
        platform = searchPlatform,
        indexJobs = AndroidSearchIndexJobs(),
        services = ReadingSearchServices(bibleSearchService, epubSearchService, searchResultsCache, searchIndexService),
        strings = { AndroidStrings(activity.hostContext) },
        onLeaveFullScreen = { activity.fullScreen = false },
        resultsScope = activity.lifecycleScope,
    )

    private val textDisplaySettingsService: TextDisplaySettingsService by inject()
    private val backgroundThumbnailResolver = BackgroundThumbnailResolver()

    /**
     * Builds the passage-grid controllers of the Speak sheet's verse picker (inside [slots]) and of
     * the key-chooser quick sheet below, from `GridPassageHostSupport.kt`'s seams.
     */
    private val gridFactory = GridPassageControllerFactory(
        navigationControl = navigationControl,
        windowControl = windowControl,
        settings = CommonUtils.settings,
        workspaceName = { activity.hostWindowRepository.name },
        isPortrait = { CommonUtils.isPortrait },
    )

    /**
     * The reading view's host-built slots, in common code (`:sharedUi` `ReadingHostSlots`): the
     * reading dialogs, the agent-log panel, the Speak bar, the search settings, the text-settings editor
     * and the Speak settings sheet. Declared after [agentLog] and
     * [speakTransport], which it wraps; [fullScreen] is read lazily, in composition.
     */
    internal val slots: ReadingHostSlots = ReadingHostSlots(
        hostState = hostState,
        strings = { AndroidStrings(activity.hostContext) },
        externalLinks = AndroidExternalLinks(),
        agentLog = agentLog,
        speakTransport = speakTransport,
        disableAnimations = { CommonUtils.settings.disableAnimations },
        fullScreen = { fullScreen.value },
        searchState = searchState,
        textDisplaySettingsService = { textDisplaySettingsService },
        scope = hostScope,
        imagePicker = object : ImagePicker {
            override suspend fun pickImage(): String? = activity.readingCommands.textSettingsImagePicker()
        },
        thumbnailFor = backgroundThumbnailResolver::resolve,
        speakSettingsService = speakSettingsService,
        speakPlatform = AndroidSpeakSettingsPlatform { activity.hostActivity },
        gridPassages = gridFactory,
        workspaceService = { workspaceService },
        readingProgressService = { readingProgressService },
        quickActions = AndroidReadingQuickActions { activity },
        keyChooserPage = AndroidReadingKeyChooserPage { windowControl },
        // A stateless second instance: this slot's navigator shares nothing with the host's own.
        navigator = AndroidReadingNavigator({ activity.hostActivity }),
        textShare = object : TextShare {
            override fun shareText(text: String, chooserTitle: String) =
                AndroidTextShare(activity.hostActivity).shareText(text, chooserTitle)
        },
        clipboard = object : ClipboardText {
            private val delegate get() = AndroidClipboardText(activity.hostContext)
            override fun read(): String? = delegate.read()
            override fun write(label: String, text: String) = delegate.write(label, text)
        },
        toast = { UserMessages.toast(it) },
        workspaceName = { activity.hostWindowRepository.name },
        activeWindowPosition = { windowControl.activeWindowPosition },
        currentDocumentInitials = { documentControl.currentDocument?.initials },
    )

    // ------------------------------------------------------------------------------------------
    // Settings editor sheets T10 -- the reading view's in-place text-settings editor. Its page
    // stack, controllers and sheet live in common code ([ReadingHostSlots]); this host forwards.
    // ------------------------------------------------------------------------------------------

    /** The in-place text-settings editor's page stack (owned by [slots]). */
    internal val textSettingsEditor: SettingsEditorStack get() = slots.textSettingsEditor

    /**
     * Open the in-place editor for one text display setting, over the reading view. Meant to be
     * called from `MainBibleActivity`'s two menu dispatch sites instead of `Preference.openDialog`,
     * so the user never leaves the reading view and back has nowhere to land but here.
     */
    internal fun showTextSettingEditor(
        scope: SettingsScope,
        page: SettingsEditorPage,
        onReady: () -> Unit,
    ) = slots.showTextSettingEditor(scope, page, onReady)

    /** The Speak sheet's page stack (owned by [slots]); `internal` for test-only reads of which page is open. */
    internal val speakSheet: SpeakSheetStack get() = slots.speakSheet

    /** Open the Speak settings sheet over the reading view; the only way in. */
    internal fun showSpeakSettings(): Unit = slots.showSpeakSettings()

    /**
     * Round 14b §8: show the Speak transport bar and nothing else — no playback, no sheet, no
     * toggle. The main-menu Speak item's target.
     *
     * Why this exists at all: the menu used to call [showSpeakSettings], which opens the settings
     * sheet, and the settings sheet has NO play control. Transport-bar visibility is a separate
     * state that path never touched, so the menu was the one entry point that could reach the Speak
     * settings with no way back to playback. This corrects round 13a's design D2, which reasoned
     * that the cog needs no transport bar inside it because "the widget is already visible
     * underneath" — true for the cog and for a long-press taken while the bar is up, false for the
     * menu.
     *
     * The other three entry points are unchanged: the toolbar's short press still TOGGLES the bar
     * ([MainBibleActivity.composeToggleSpeak]), its long press and the bar's own cog still open the
     * settings sheet.
     *
     * Delegates rather than owning a flag: `MainBibleActivity.transportBarVisible` is the single
     * source of truth and the Compose side only observes it — see [speakTransport]'s kdoc.
     */
    internal fun showSpeakTransport() {
        activity.readingCommands.composeShowSpeakTransport()
    }

    /** The Speak/Advanced-Speak help dialogs' state (owned by [slots]); `internal` for test-only reads. */
    internal val speakHelp: MutableState<String?> get() = slots.speakHelp

    internal fun showSpeakHelp(): Unit = slots.showSpeakHelp()

    internal fun showAdvancedSpeakHelp(): Unit = slots.showAdvancedSpeakHelp()

    // ------------------------------------------------------------------------------------------
    // F6 Task 8a -- SWORD search inside the reading view. The session, controllers, run/leave logic
    // and the sheet live in common code (`:sharedUi` [ReadingSearchState]); this host forwards the
    // members its callers and tests use, and keeps only what needs the activity.
    // ------------------------------------------------------------------------------------------

    /** Whether the modal search-settings sheet is open (owned by [searchState]). */
    internal val searchSettingsOpen get() = searchState.searchSettingsOpen

    /** Whether the toolbar field's recent-terms dropdown is open (owned by [searchState]). */
    internal val searchRecentMenuOpen get() = searchState.searchRecentMenuOpen

    /** Whether the toolbar's search field holds focus; `MainBibleActivity` keys its IME padding on it. */
    val searchFieldFocused get() = searchState.searchFieldFocused

    internal val searchSection get() = searchState.searchSection

    /** Test-only read of the current results-list state. */
    internal val searchResultsListStateForTest: LazyListState get() = searchState.searchResultsListState.value

    /** The SWORD results (owned by [searchState]). */
    val searchResults get() = searchState.searchResults

    /** The EPUB results (owned by [searchState]). */
    val epubSearchResults get() = searchState.epubSearchResults

    /** The EPUB word-mode (owned by [searchState]; re-read on every [openSearch]). */
    val epubSearchMode: StateFlow<EpubSearchMode> get() = searchState.epubSearchMode

    /** Index-build progress rows (owned by [searchState]). */
    val searchIndexProgress get() = searchState.searchIndexProgress

    /** The "cannot be searched" snackbar payload (owned by [searchState]). */
    val searchUnavailableDocName: StateFlow<String?> get() = searchState.searchUnavailableDocName

    /** Clears [searchUnavailableDocName] once the snackbar has started showing it. */
    fun searchUnavailableMessageShown() = searchState.searchUnavailableMessageShown()

    /** The search session state machine (owned by [searchState]). */
    val searchController get() = searchState.controller

    /** What the toolbar renders in search mode, or `null` when search mode is off. */
    val searchBar: StateFlow<ReadingSearchBarState?> get() = searchState.searchBar

    /** Test-only read of the pending selector-index chain. */
    internal val searchSelectorPendingIdsForTest: List<String>? get() = searchState.searchSelectorPendingIds

    /** Test-only: the request the most recent [runSearch] built. */
    internal val lastSearchRequestForTest: SearchRequest? get() = searchState.lastSearchRequestForTest

    /**
     * Opens search for the active window's document (every retargeted entry point). See
     * [ReadingSearchState.openSearch].
     */
    fun openSearch(seedQuery: String? = null, preDecorated: Boolean = false) =
        searchState.openSearch(seedQuery, preDecorated)

    /** Strong's find-all; `false` means the caller must fall back to its own route. See [ReadingSearchState.openSearchStrongs]. */
    fun openSearchStrongs(ref: String, translationIds: List<String>): Boolean =
        searchState.openSearchStrongs(ref, translationIds)

    /** The three-stage back for search; returns whether the press was consumed. */
    fun closeSearchIfOpen(): Boolean = searchState.closeSearchIfOpen()

    internal fun buildSearchRequest(docId: String, query: String): SearchRequest =
        searchState.buildSearchRequest(docId, query)

    internal fun runSearch(docId: String, query: String) = searchState.runSearch(docId, query)

    internal fun runEpubSearch(docId: String, query: String) = searchState.runEpubSearch(docId, query)

    internal fun onEpubSearchResultSelected(keyId: String, ordinal: Int) =
        searchState.onEpubSearchResultSelected(keyId, ordinal)

    internal fun onSearchResultSelected(referenceName: String, translationId: String?) =
        searchState.onSearchResultSelected(referenceName, translationId)

    internal fun openSearchResultsInWindow() = searchState.openSearchResultsInWindow()

    internal fun onSearchTranslationsChosen(ids: List<String>) = searchState.onSearchTranslationsChosen(ids)

    internal fun persistEpubSearchMode(mode: EpubSearchMode) = searchState.persistEpubSearchMode(mode)

    /** Whether the settings sheet must show the EPUB word-mode row (decided from the live target). */
    internal fun searchSettingsForEpub(): Boolean = searchState.searchSettingsForEpub()

    private val generation = ComposeReadingViewGeneration()

    /** Invalidation source for the rail's per-window labels; see [WindowLabelFreshness]. */
    internal val windowLabelFreshness = WindowLabelFreshness()

    /** The rail button's label for [snapshot], read through [windowLabelFreshness] so it recomposes. */
    internal fun railWindowLabel(snapshot: WindowSnapshot): String =
        windowLabelFreshness.observed { activity.readingCommands.windowLabelFor(snapshot.id) }

    /** The rail button's top row for [snapshot], read through [windowLabelFreshness] so it recomposes. */
    internal fun railWindowTopLabel(snapshot: WindowSnapshot): String? =
        windowLabelFreshness.observed { activity.readingCommands.windowTopLabelFor(snapshot.id) }

    /**
     * Test-only read of THIS host's generation counter — same convention as
     * [searchSelectorPendingIdsForTest]. Exposed for review finding I4: the guard for the batch's
     * headline invariant ("nothing on the search path may rebuild the pane subtree") used to assert
     * over a LOCAL [ComposeReadingViewGeneration] that no production code could ever reach, so it
     * passed unconditionally. A test has to read the host's own counter to be able to fail.
     */
    internal val generationForTest: ComposeReadingViewGeneration get() = generation

    /**
     * Test-only read of THIS host's drawer item list — same convention as [generationForTest].
     *
     * Exposed by reading-host re-typing T8d's fix round: `onToolbarStateMayHaveChanged()` is
     * `rebuildDrawer(showSearch, showSpeak)` + `refreshHostedState()`, and it is the WHOLE of what
     * classic's `UPDATE_SUGGESTED_DOCUMENTS_ON_FINISH` arm does. Without a read of what that rebuild
     * produced, the only assertions available for that arm were negative ones ("the composition was
     * not rebuilt", "no window-resynchronization call"), which a deleted arm passes just as happily as a
     * live one -- the review's finding 1.
     */
    internal val drawerMenuForTest: State<DrawerMenuState> get() = drawerMenu

    /** See [HostedStateRefresher]. */
    private val hostedStateRefresher = HostedStateRefresher(toolbarStateService, generation)

    /**
     * The window-management command controller (Batch 12b follow-on Plan A) — hoisted to a host
     * field (rather than built fresh inside [mountComposeView], as Plan A/12b-C left it) so
     * [MainBibleActivity.handleWindowPaneMenuItem] (Task 5) can dispatch through the SAME instance
     * the pane overlay's own gestures ([openPaneMenu]'s callers) and the restore rail already
     * drive — one controller per host, not a fresh one per composition.
     */
    val controller = ReadingViewController(windowState, commands)

    /** See [WindowButtonsVisibility]. */
    private val windowButtonsVisibility get() = hostState.windowButtonsVisibility

    /**
     * Builds the per-window (☰) pane popup menu's item list (Task 4) — constructed from the host's
     * own `windowControl`/`speakControl` fields, mirroring how classic `SplitBibleArea.getItemOptions`
     * closes over its own `mainBibleActivity`'s collaborators.
     */
    private val paneMenuStateBuilder = WindowPaneMenuStateBuilder(windowControl, speakControl)

    /**
     * The windowId whose ☰ menu is currently open, or `null` when closed — host-owned so the
     * popup survives recomposition, mirroring [overflowExpanded]/[overflowItems] below. Opened by
     * [openPaneMenu] (the pane overlay's ☰-button tap, or the Plan-A restore rail's long-press);
     * closed by [closePaneMenu] (`onDismiss`, or after a non-toggle item acts).
     */
    private val paneMenuWindowId get() = hostState.paneMenuWindowId
    private val paneMenuItems get() = hostState.paneMenuItems

    /**
     * Which surface ([PaneMenuAnchor]) the currently-open [paneMenuWindowId] menu is anchored to —
     * set by [openPaneMenu]'s caller (the pane overlay's ☰-button tap passes [PaneMenuAnchor.Pane],
     * the rail's `onWindowLongPress` passes [PaneMenuAnchor.Rail]). [mountComposeView] gates each
     * surface's own `paneMenuWindowId` on this so exactly one of the two `WindowPaneMenu` instances
     * ever reports itself expanded — see the pane overlay's `paneOverlay` lambda and the rail's
     * `tabBar` lambda in [mountComposeView].
     */
    private val paneMenuAnchor get() = hostState.paneMenuAnchor

    internal val paneMenuWindowIdForTest: String? get() = paneMenuWindowId.value
    internal val paneMenuAnchorForTest: PaneMenuAnchor get() = paneMenuAnchor.value

    /**
     * Mirrors [ScreenSettings.nightMode]. Kept current via [ScreenSettings.nightModeChanges] (see
     * [init]) instead of being captured once at [install] time, which is what made the host's
     * `AbTheme` non-reactive to a runtime night-mode flip (the Plan-A carry-forward this task
     * closes — see the whole-Plan-A review Minor).
     */
    private val nightMode = mutableStateOf(ScreenSettings.nightMode)

    /**
     * Mirrors `CommonUtils.settings.monochromeMode` — the compose-drawer counterpart of classic
     * `setupUi`'s `if (monochromeMode) drawerLayout.setScrimColor(TRANSPARENT)` (this is the only
     * thing that read it on the drawer path). There is no dedicated change event for it, so it is
     * refreshed alongside [nightMode] on [ScreenSettings.nightModeChanges] (see [init]) — the
     * closest thing this codebase has to a "display appearance changed" signal, and the same event
     * the e-ink/monochrome device path posts.
     */
    private val monochrome = mutableStateOf(CommonUtils.settings.monochromeMode)
    internal val monochromeForTest: State<Boolean> get() = monochrome
    internal val windowButtonsVisibilityForTest: WindowButtonsVisibility get() = windowButtonsVisibility

    /**
     * Mirrors [MainBibleActivity.fullScreen]. Kept current via [SharedActivityState.fullScreenChanged]
     * (see [init]) so entering/leaving fullscreen from ANY path — the Compose overflow menu's
     * "Full screen" row (Batch 12b-C Task 3, dispatched via [MainBibleActivity.handleOptionsMenuItem]),
     * the same menu reached by the `"AltKeyO"` shortcut (slice 7 Task 2 repointed it at
     * [openOverflowMenu]), or the reading back chain — is reflected here. There is
     * no dedicated "toggle fullscreen" entry point in [ReadingToolbarCallbacks]/`ReadingViewScreen`
     * (only the overflow-menu row), so mirroring [MainBibleActivity.fullScreen] is what keeps this
     * host's `AbTheme`/`ReadingViewScreen` in sync regardless of which path set it.
     */
    private val fullScreen = mutableStateOf(activity.fullScreen)

    /**
     * Current-reference overlay text (classic `MainBibleActivity.bibleOverlayText`), mirrored from
     * the same events classic `SplitBibleArea` listens to (Batch 12g Task 3); empty on `KeyIsNull`
     * (no current key — mirrors classic's silent catch in `updateTitle`).
     */
    private val overlayText = mutableStateOf(readOverlayText())

    /** Whether the active window shows a Bible (classic `activeWindow.pageManager.isBibleShown`). */
    private val activeIsBibleShown = mutableStateOf(windowControl.activeWindow.pageManager.isBibleShown)

    private fun readOverlayText(): String = try { activity.readingCommands.bibleOverlayText } catch (e: KeyIsNull) { "" }

    /**
     * The Compose overflow ("3-dot") options menu's item list + expanded flag (Batch 12b-C Task 3)
     * — host-owned state, since (unlike [nightMode]/[fullScreen]) there is no owner event stream to
     * mirror: [ReadingToolbarCallbacks.onOverflow] below rebuilds [overflowItems] from
     * [MainBibleActivity.buildOptionsMenuItems] and opens the menu; the `onOverflowItemClick`/
     * `onOverflowDismiss` callbacks passed to [mountComposeView] (see [install]) drive it closed
     * again — or, for a boolean toggle, rebuild it with the flipped check — via
     * [MainBibleActivity.handleOptionsMenuItem].
     */
    private val overflowItems = mutableStateOf(emptyList<OptionsMenuItem>())
    private val overflowExpanded = mutableStateOf(false)

    /**
     * Opens the Compose overflow ("3-dot") options menu: exactly what
     * [ReadingToolbarCallbacks.onOverflow] does (that callback delegates here), exposed publicly so
     * the OTHER entry point to the reading view's options menu — the `"AltKeyO"` keyboard shortcut
     * in [net.bible.android.view.activity.page.BibleJavascriptInterface] — reaches the SAME Compose
     * menu. Nav-graph slice 7 Task 2: that shortcut used to call the native
     * `MainBibleActivity.showOptionsMenu()` `PopupMenu`, which has been deleted.
     *
     * The menu needs a toolbar to anchor on, and there are two states where `ReadingToolbar` — which
     * hosts both the overflow button and the `ReadingOverflowMenu` `DropdownMenu` that
     * [overflowExpanded] drives — is not composed at all. `"AltKeyO"` reaches both (the BibleView
     * has focus, so `keyboard.ts` keeps sending it), and merely setting the flag in either left it
     * stuck true, so the menu popped open unrequested the next time the toolbar reappeared
     * (fix round 1, Major 1). Each state is handled the way that state deserves:
     *
     * - **fullscreen** (`ReadingViewScreen` composes the toolbar only `if (!fullScreen)`): leave
     *   fullscreen first, then open. This is [searchController]'s `onLeaveFullScreen` precedent —
     *   Ctrl+F solves the identical problem that way, and the assignment is idempotent.
     * - **search mode** ([ReadingToolbar] renders the search row and `return`s before the normal
     *   toolbar): return without touching anything. Closing search mode instead would throw away the
     *   user's typed query, and there is no classic behaviour to preserve — toolbar search mode is a
     *   Compose-era feature, classic search was a separate Activity — so no menu is the honest
     *   answer for a shortcut pressed in a mode that has no overflow button.
     */
    fun openOverflowMenu() {
        if (searchController.searchModeActive.value) return
        activity.fullScreen = false
        overflowItems.value = activity.readingCommands.buildOptionsMenuItems()
        overflowExpanded.value = true
    }

    /** Test-only reads of the overflow menu state above — same convention as [paneMenuWindowIdForTest]. */
    internal val overflowExpandedForTest: Boolean get() = overflowExpanded.value
    internal val overflowItemsForTest: List<OptionsMenuItem> get() = overflowItems.value

    /**
     * The Compose navigation drawer's item list (Batch Z-early A6) — the compose-path replacement
     * for classic's `NavigationView` over `R.menu.main_bible_drawer_menu`. Host-owned `State` for
     * the same reactivity reason as [overflowItems] above: rebuilt by [rebuildDrawer] whenever its
     * dynamic inputs change, and read by `mountComposeView`'s `ModalDrawerSheet`.
     */
    private val drawerMenu = mutableStateOf(DrawerMenuState.EMPTY)

    /**
     * The open/closed *request*. `mountComposeView` mirrors it into `ModalNavigationDrawer`'s real
     * `DrawerState` (and back again, so a swipe/scrim close clears it) — see its `drawerOpenState`
     * parameter. Flipped by [toggleDrawer], cleared when a row is clicked.
     */
    private val drawerOpen get() = hostState.drawerOpen

    /**
     * Last values pushed by [MainBibleActivity.updateActions]' `showSearch`/`showSpeak` locals (see
     * [rebuildDrawer]) — cached so a rebuild triggered from anywhere else (e.g. [install]'s
     * entry-time one, which runs before the first `updateActions()`) keeps the current enablement
     * instead of resetting it. Both default `true`, mirroring the menu XML's own initial state.
     */
    private var lastShowSearch = true
    private var lastShowSpeak = true

    /**
     * Rebuilds the drawer item list from the current dynamic flags — classic parity for
     * `searchButton`/`speakButton` `isEnabled` ([MainBibleActivity.updateActions], ~1875-1876),
     * `googleDriveSync` `isVisible` (`setupUi`, ~560) and `rateButton` `isVisible` (`onCreate`,
     * ~452). Search/speak are PUSHED from `updateActions()` because they are locals there, so they
     * default to the last pushed values (see [lastShowSearch]/[lastShowSpeak]).
     */
    fun rebuildDrawer(
        showSearch: Boolean = lastShowSearch,
        showSpeak: Boolean = lastShowSpeak,
    ) {
        lastShowSearch = showSearch
        lastShowSpeak = showSpeak
        drawerMenu.value = DrawerMenuStateBuilder.build(
            showSearch = showSearch,
            showSpeak = showSpeak,
            isCloudSyncAvailable = CommonUtils.isCloudSyncAvailable,
            // R6d: host-independent (a pure BuildVariant read), so it comes from the builder
            // that consumes it rather than from an Activity — R1's rule, `ReadingHostDelegationGuardTest`.
            isRateVisible = DrawerMenuStateBuilder.drawerRateVisible,
        )
    }

    /** Toggles the Compose drawer — the target of [MainBibleActivity.toggleDrawer]. */
    fun toggleDrawer() = hostState.toggleDrawer()

    /**
     * Whether the Compose drawer is open. Reads the same request flag `mountComposeView` keeps in
     * two-way sync with the real `DrawerState` (Batch Z-early A7 fix D made that sync symmetric, so
     * this is true for a drawer opened by ANY route, not just [toggleDrawer]).
     *
     * Read by [MainBibleActivity]'s key/back handlers, which separately ask
     * `drawerLayout.isDrawerVisible(GravityCompat.START)` on the native `DrawerLayout` — always
     * `false` there, since `setupUi` locks it as soon as the host is installed.
     */
    val isDrawerOpen: Boolean get() = hostState.isDrawerOpen

    /** Opens the Compose drawer (idempotent) — see [isDrawerOpen]. */
    fun openDrawer() = hostState.openDrawer()

    /** A tap on one of this host's BibleViews: re-show the window buttons (classic SplitBibleArea.kt:203-205). */
    fun onBibleViewTouched() = windowButtonsVisibility.onTouch()

    /** Closes the Compose drawer (idempotent) — see [isDrawerOpen]. */
    fun closeDrawer() = hostState.closeDrawer()

    init {
        subscriptions.add(SharedActivityState.instance.fullScreenChanged.subscribeOnMain { fullScreen.value = it })
        subscriptions.add(ScreenSettings.nightModeChanges.subscribeOnMain {
            nightMode.value = ScreenSettings.nightMode
            monochrome.value = CommonUtils.settings.monochromeMode
        })
        subscriptions.add(PassageChangeMediator.changes.subscribeOnMain { change ->
            when (change) {
                // Batch 12g Task 3: mirrors classic `SplitBibleArea`'s own registration for the same two
                // events (`SplitBibleArea.kt:172,190`), which drive its `updateBibleReferenceOverlay`.
                // Unlike a dedicated `:sharedCore` service seam, this reuses the host's existing
                // fullscreen/night-mode event-mirror idiom: the overlay is just one string + two
                // booleans, kept as host-owned Compose `State` and gated by the pure `bibleReferenceOverlayVisible`
                // (`:sharedCore`) fn at render time — no separate service/controller class, per the plan.
                is PageChange.VerseChanged -> {
                    // Fires for every window (synchronised ones included), unlike the active-window-only
                    // `refreshHostedState()` path, so the rail's labels for ALL windows are covered.
                    windowLabelFreshness.invalidate()
                    overlayText.value = readOverlayText()
                    activeIsBibleShown.value = windowControl.activeWindow.pageManager.isBibleShown
                    // F44/B3b: this is the event a document swap WITHIN one window reliably fires
                    // synchronously — `CurrentPageManager.setCurrentDocument` ->
                    // `PassageChangeMediator.onCurrentPageChanged` emits it right after the swap takes
                    // effect on the page manager, before any async content load. `PageChange.ContentLoaded`
                    // (`PassageChangeMediator.contentChangeFinished`) also follows a document swap, but
                    // only once `Window.loadText`'s background IO coroutine finishes fetching + handing
                    // the doc to the (possibly still-null, in a headless/invisible window) `BibleView` —
                    // and it is skipped entirely when the window is not visible. This event is already
                    // subscribed to here for the same "active document may have changed" purpose, so the
                    // controller refresh reuses this proven, synchronous channel rather than adding a
                    // second, less reliable one.
                    searchController.activeDocumentChanged()
                }
                // Task 4 (F2b): classic's per-window rail top label (`WindowButtonWidget.kt:148`,
                // `pageManager.titleText`) is refreshed on this SAME event
                // (`WindowButtonWidget.kt:232-234`). `windowTopLabel`/`windowLabel`/`windowIcon` are
                // plain, non-`@Composable` lambdas (see `MainBibleActivity.windowTopLabelFor`'s kdoc for
                // why), so Compose cannot observe them: the label lambdas read [windowLabelFreshness]
                // and this handler bumps it, which is what recomposes the rail. `refreshHostedState()`
                // below only refreshes the toolbar (active window) and does NOT recompose the rail.
                // `CurrentBiblePage.doSetKey` posts this event alone (no `PageChange.VerseChanged`),
                // so the bump is needed here as well as above.
                //
                // `BibleVerseChanged` now arrives only from `CurrentBiblePage.doSetKey` and an
                // inhibited scroll (where no `VerseChanged` follows). Normal scrolling emits
                // `VerseChanged`, which refreshes the toolbar through its own subscription, so this
                // handler keeps those remaining paths fresh without a duplicate toolbar rebuild.
                PageChange.BibleVerseChanged -> {
                    windowLabelFreshness.invalidate()
                    refreshHostedState()
                }
                PageChange.ContentLoaded -> Unit
            }
        })
        subscriptions.add(windowState.windowChanges.subscribeOnMain { change ->
            if (change is WindowChange.ActiveWindowChanged) {
                overlayText.value = readOverlayText()
                activeIsBibleShown.value = windowControl.activeWindow.pageManager.isBibleShown
                // F44/B3: search mode outlives a window switch, so the panel's target follows it.
                searchController.activeDocumentChanged()
            }
        })

        // F6-B1: the activity's IME padding is keyed on this field's focus, and NO inset changes when
        // focus moves — so the insets listener never fires and the change has to be pushed.
        hostScope.launch {
            searchFieldFocused.collect { activity.readingInsets.onComposeSearchFieldFocusChanged() }
        }
    }

    /**
     * See [ComposeReadingViewGeneration]. Called by [DocumentViewManager.buildView] on the compose
     * path when `forceUpdate` is true — which is exactly the hook `MainBibleActivity.currentWorkspaceId`'s
     * setter (workspace switch, or a same-workspace reload) drives, AFTER `windowRepository` has
     * already been reloaded to the new/current workspace. Also refreshes [agentSessionService] here
     * (Batch 12e-B Task 6 workspace-switch refresh): unlike classic `AgentLogWidget`, which always
     * recomputes `workspaceId` fresh per event, [agentSessionService]'s `snapshot` is a
     * cached `StateFlow` only rebuilt on an agent event for the CURRENT workspace at the time — so
     * without this, switching workspace with no agent event in between would keep showing the
     * previous workspace's snapshot until the next agent event for the new one fires.
     */
    fun rebuild() {
        generation.rebuild()
        activity.lifecycleScope.launch { agentSessionService.refresh() }
    }

    /**
     * Pushes the Compose reading view's state forward from a *classic* refresh point — see
     * [HostedStateRefresher]. Called as `composeReadingViewHost?.refreshHostedState(...)` from
     * `MainBibleActivity.updateActions()`, `preferenceSettingsChanged()` and the two Strongs
     * mutators, so it is inert only before the host is installed.
     */
    fun refreshHostedState(rebuildComposition: Boolean = false) =
        hostedStateRefresher.refresh(rebuildComposition)

    /**
     * Opens the per-window (☰) pane menu for [windowId], anchored to [anchor] — called by the pane
     * overlay's ☰-button tap (passing [PaneMenuAnchor.Pane]) and by the rail's `onWindowLongPress`
     * (passing [PaneMenuAnchor.Rail]), both wired in [install]. Forces the pane buttons visible
     * first (mirroring classic `showPopupMenu`'s `timerTask?.cancel();
     * toggleWindowButtonVisibility(true)`, `SplitBibleArea.kt:731-732`), so a menu opened from the
     * always-visible rail while the floating ☰ overlay happens to be auto-hidden is still usable.
     * [mountComposeView] gates each surface's `WindowPaneMenu` on [paneMenuAnchor] so only the
     * surface matching [anchor] ever renders the menu expanded — a rail long-press no longer opens
     * it at the pane's floating ☰ button (the A/B batch 3 F5 bug this anchor fixes).
     */
    fun openPaneMenu(windowId: String, anchor: PaneMenuAnchor) = hostState.openPaneMenu(windowId, anchor)

    /** Closes whichever per-window ☰ menu is open (`onDismiss`, or after a non-toggle item acts). */
    fun closePaneMenu() = hostState.closePaneMenu()

    /**
     * The reader background colour for ONE pane — classic `BibleFrame.kt:138`'s
     * `bibleView.backgroundColor`, which already resolves day/night and monochrome.
     *
     * Resolved per window id, not once per split: two panes can carry different day/night reader
     * backgrounds, so a single colour captured for the whole split would be wrong for one of them.
     * `null` only when [windowId] matches no window at all; in which case the pane keeps the
     * transparent background it had before (A/B batch 4a F5).
     *
     * A/B batch 4a whole-batch review C1: this used to read `Window.bibleView?.backgroundColor`,
     * which is `null` on exactly the window F5 exists to fix — a brand-new window's `bibleView` is
     * only assigned once the pane's `AndroidView` factory runs (see `pane` below), which happens
     * strictly AFTER `SplitContent` evaluates this function for that same pane's background
     * modifier. `bibleViewBackgroundColorFor` needs only the `Window` (not a live `BibleView`), so
     * it resolves the colour immediately, before the WebView exists.
     */
    internal fun paneBackgroundArgbFor(windowId: String): Int? =
        activity.hostWindowRepository.getWindow(IdType(windowId))?.let { bibleViewBackgroundColorFor(it) }

    /**
     * Opens the Compose reading-view LLM prompt-selector dialog for [selection] (Batch 12e-A Task
     * 6) — the compose-path counterpart of classic `LlmDialogHelper.showPromptSelector`. The
     * actual dialog UI is rendered by [readingLlmDialogs]/[ReadingLlmDialogs] inside
     * [mountComposeView]; this bridge only supplies the `onExecute` callback that starts the agent
     * once a prompt (and, if needed, a specification/model override) has been chosen — a verbatim
     * mirror of classic `LlmDialogHelper.executePrompt`. Called by [MainBibleActivity]'s
     * host-gated entry points ([MainBibleActivity.showLlmPromptSelector] and the overflow/pane-menu
     * LLM actions).
     */
    fun showPromptSelector(selection: Selection, context: PromptContext, docCategory: DocumentCategory?) {
        readingLlmDialogs.openPromptSelector(context.name, docCategory?.name) { promptId, userSpecification, modelOverrideId ->
            AgentForegroundService.startAgent(
                activity.hostContext,
                IdType(promptId),
                selection,
                activity.hostWindowRepository.id,
                userSpecification,
                modelOverrideId?.let { IdType(it) },
            )
        }
    }

    /**
     * Opens the Compose reading-view regenerate-confirm dialog for [pageId]/[bibleView] (Batch
     * 12e-A Task 6) — it replaced the classic `LlmDialogHelper.showRegenerateDialog`, deleted in
     * Batch Z-late's epilogue. The `onRegenerate` callback is a verbatim mirror of classic
     * `LlmDialogHelper.startRegenerate`: it loads the "Regenerating…" placeholder into [bibleView]
     * before kicking off the foreground service. Called by [MainBibleActivity.showRegenerate].
     */
    fun showRegenerate(pageId: IdType, bibleView: BibleView) {
        readingLlmDialogs.openRegenerate(pageId.toString()) { _, instructions, keepPrevious, freshRun, modelOverrideId ->
            activity.lifecycleScope.launch {
                bibleView.loadDocument(ErrorDocument(activity.getString(R.string.ai_document_regenerating), ErrorSeverity.NORMAL))
            }
            AgentForegroundService.startRegenerate(
                activity.hostContext,
                pageId,
                activity.hostWindowRepository.id,
                bibleView.window.id,
                instructions,
                keepPrevious,
                freshRun,
                modelOverrideId?.let { IdType(it) },
            )
        }
    }

    /**
     * Cancels this host's owner-stream [subscriptions] (see [init]) and cancels [hostScope] (so
     * any in-flight [readingLlmDialogs] coroutine work is torn down with the host). Call from
     * [MainBibleActivity.onDestroy] — each activity (re-)creation builds a fresh
     * [ComposeReadingViewHost], so without this the previous instance's registration/scope would
     * leak (an activity-recreating config change would accumulate one stale registration per
     * rotation). Safe to call unconditionally even if [install] hasn't run yet.
     */
    fun dispose() {
        subscriptions.cancelAll()
        // F6 Task 8a: a `JobManager` WorkListener outlives the activity that registered it (the
        // manager is a process-wide static), so an in-flight index build would otherwise leak this
        // host through it.
        searchState.dispose()
        hostScope.cancel()
    }

    /** Mounts the Compose reading view into [container] (expected: `binding.mainBibleView`, already emptied by the caller). */
    fun install(container: ViewGroup) {
        // Layout surgery: hide the classic toolbar row and re-anchor `container` to the parent
        // top. Done programmatically here rather than authored into main_bible_view.xml. The
        // original reason -- keeping the classic path, which never called install(), byte-for-byte
        // identical -- died with that path in Z-late; install() is now unconditional, from
        // MainBibleActivity.setupUi, and the layout has exactly one inflation site
        // (MainBibleActivity.kt's MainBibleViewBinding.inflate). NO replacement reason is claimed:
        // this is simply where it has always lived.
        //
        // What the XML could NOT do is delete the row, and that is not what this does.
        // toolbarLayout's children are still WRITTEN TO by MainBibleActivity code that runs on this
        // path, so the views have to exist: binding.syncIcon's visibility (MainBibleActivity's former
        // `CloudSync.runningChanged` subscription and `setupUi`), binding.speakButton's alpha (the `transportBarVisible`
        // setter), binding.strongsButton's image + alpha + tint (`updateStrongsButton`),
        // binding.bibleButton's image (`onCreate`'s `isDiscrete` branch), and
        // binding.pageTitleContainer's touch listener (`setupToolbarFlingDetection`).
        // Those writes are all invisible -- the row is GONE -- but removing the row now would make
        // them NPE, so it stays inflated and merely GONE until Task 11/13 delete the writes, the XML
        // and the Activity together.
        //
        // R6d: spelled as the INTENTION, not as the two `binding` writes. `binding` never enters
        // `ReadingHostActivity` (spec §3.1); `MainBibleActivity` answers this with exactly the two
        // lines that used to sit here, and a host with no classic toolbar row answers with the
        // interface's default no-op.
        activity.hideClassicToolbarRow()
        (container.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            container.layoutParams = params
        }

        val composeView = ComposeView(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // R8: the argument block that used to sit here is [ReadingView], so that the `reading`
            // nav destination composes the SAME arguments instead of a second copy of them.
            setContent { ReadingView() }
        }
        container.addView(composeView)
    }

    /**
     * The reading view, with THIS host's collaborators bound into the common `ReadingHost`'s
     * [ReadingHostDeps] (see [readingHostDeps]) -- what used to be [install]'s `mountComposeView(...)`
     * argument block, and nothing else.
     *
     * **Extracted by reading-host re-typing R8, which is the task that gave it a second caller.**
     * `NavHostComposeActivity`'s `reading` destination composes this directly (its `content` slot
     * was an `error(...)` until R8), and a destination has a composition rather than a
     * [ViewGroup] -- so the ~340 lines of argument building had to stop being reachable only
     * through a function that takes a container. Copying them into the nav host instead would have
     * made two argument lists to keep true, of which only one would ever be edited; this is one.
     *
     * [mountComposeView] is NOT this function's caller and is not meant to be: it is the test seam
     * its host tests use to mount the same [ReadingHostContent] with a hand-built [ReadingHostDeps].
     *
     * **No compiler checks that [readingHostDeps] wires every member.** [ReadingHostDeps] defaults
     * most members to inert (so tests wire only what they exercise), and a member added later but not
     * wired here would silently render nothing on Android. `ReadingHostDepsWiringGuardTest` is the
     * check: it fails when a constructor parameter is not passed by name in [readingHostDeps] and is
     * not allow-listed with a reason.
     */
    @Composable
    fun ReadingView() {
        // Ensure the SSOT reflects the freshly-loaded workspace before first render (repo
        // mutations after this point already route through the 12a notifiers, which keep
        // windowState.layout current, but the very first mount needs an explicit kick).
        //
        // R8 moved it out of [install] and into the composition, so that BOTH mounts get it: a
        // `remember` rather than a `LaunchedEffect`, because an effect runs after the first frame
        // and the first frame is precisely what this exists to populate.
        remember { windowState.refresh(activity.hostWindowRepository) }
        val deps = remember { readingHostDeps() }
        ReadingHostContent(deps, paneFactory)
    }

    /**
     * Today's pane hosting: the window's cached `BibleView` in an `AndroidView`. Provided to the common
     * `ReadingHost` as `LocalReadingPane`; a window that no longer exists renders nothing.
     */
    private val paneFactory: ReadingPaneFactory = { windowId, modifier ->
        val window = activity.hostWindowRepository.getWindow(IdType(windowId))
        if (window != null) {
            // A/B batch 4a whole-batch review I1: classic `BibleFrame.build()` also calls
            // `bibleView.updateBackgroundColor()` (BibleFrame.kt:137), which sets the WebView's
            // OWN background (`BibleView.updateBackgroundColor` -> `setBackgroundColor`). Without
            // this, even with C1 fixed (the pane Box now paints the right colour underneath), a
            // freshly created WebView still draws its platform-default white over that pane
            // background until its first document finishes loading -- same flash, one layer up.
            AndroidView(modifier = modifier, factory = {
                activity.readingCommands.bibleViewFactory.getOrCreateBibleView(window).apply {
                    // Classic parity + safety net: `BibleFrame.build()/recreate()` detach the
                    // BibleView from its old frame before adding it (`BibleFrame.kt:128,147`).
                    // Compose has no equivalent — `AndroidViewHolder.onRelease()` only runs the
                    // release block, and ONLY `onDeactivate()` (reusable nodes) calls
                    // `removeAllViewsInLayout()` — so a cached BibleView stays a child of a
                    // long-released holder forever. Handing it to a second holder then throws
                    // `IllegalStateException: The specified child already has a parent`. The
                    // duplicate-window-id load that produced that crash is fixed at its source
                    // (`WindowRepository.loadingFromDb`); this keeps the pane host itself from
                    // being the thing that turns any such state bug into a hard crash.
                    (parent as? ViewGroup)?.removeView(this)
                    // MATCH_PARENT is REQUIRED here, and not for Android layout reasons --
                    // `Modifier.fillMaxSize()` already gives the WebView an EXACTLY
                    // height MeasureSpec. It is Chromium that reads the layout params:
                    // `AwLayoutSizer` turns on `force_zero_layout_height` for a WebView whose
                    // layout-params HEIGHT is WRAP_CONTENT (the guard that stops a
                    // wrap-content WebView from growing without bound), and that makes CSS
                    // `vh` units inside the page resolve to **0** -- while `innerHeight` and
                    // `documentElement.clientHeight` keep reporting the true size, so nothing
                    // looks wrong from JS. Compose's `AndroidViewHolder` hands every hosted
                    // view WRAP_CONTENT params by default (it drives sizing through measure
                    // specs instead), whereas classic's `BibleFrame` adds the BibleView with
                    // MATCH_PARENT -- which is exactly why `vh` worked in classic and broke
                    // under the Compose host. Two page-wide symptoms came from it:
                    //   - `ModalDialog`'s `--max-height: calc(100vh - ...)` went negative and
                    //     clamped to 0, so EVERY modal's scrolling body (`AmbiguousSelection`,
                    //     `BookmarkModal`, footnotes/xrefs via `Note.vue`, `BookmarkLabelActions`,
                    //     `EditableText`) clipped its content away and rendered header-only.
                    //   - `#bottom { padding-bottom: 200vh }` collapsed to 0, removing the
                    //     end-of-document scroll headroom.
                    // Verified on-device by measuring `100vh` in the page: 0 with
                    // WRAP_CONTENT, 827.8 with MATCH_PARENT.
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    updateBackgroundColor()
                }
            })
        }
    }

    /**
     * THIS host's collaborators bound into the common `ReadingHost`'s deps -- what used to be
     * [ReadingView]'s argument block for the 71-parameter content composable, and nothing else.
     */
    private fun readingHostDeps() = ReadingHostDeps(
        state = hostState,
        controller = controller,
        toolbar = toolbarStateService.toolbar,
        toolbarCallbacks = ReadingToolbarCallbacks(
            onHome = { activity.toggleDrawer() },
            onTitleTap = { activity.readingCommands.composeStartKeyChooser() },
            onTitleLongPress = { activity.readingCommands.composeChooseDocument() },
            onTitleFlingVertical = { showWorkspaceSheet() },
            onTitleFlingHorizontal = { forward -> activity.readingCommands.composeCycleWorkspace(forward) },
            // Short press: swap-* switches documents, otherwise the scoped document quick sheet;
            // see `ReadingCommands.composeBibleClick`. (The overflow options menu is the real
            // Compose `ReadingOverflowMenu` below, see `onOverflow`/`openOverflowMenu`.)
            onBible = { activity.readingCommands.composeBibleClick() },
            onBibleLong = { activity.readingCommands.composeBibleLongClick() },
            onCommentary = { activity.readingCommands.composeCommentaryClick() },
            onCommentaryLong = { activity.readingCommands.composeCommentaryLongClick() },
            // The Strongs refresh now lives inside `composeCycleStrongs`/`composeStrongsLong`,
            // next to their `updateStrongsButton()` call — `StrongsPreference.handle()` posts
            // none of the 5 owner `Events` streams `toolbarStateService` subscribes to, and keeping
            // the refresh at the mutation site also covers the long-press dialog's `onReset`
            // path, which this call site never saw.
            onStrongs = { activity.readingCommands.composeCycleStrongs() },
            onStrongsLong = { activity.readingCommands.composeStrongsLong() },
            onSearch = { activity.readingCommands.composeSearch() },
            onSpeak = { activity.readingCommands.composeToggleSpeak() },
            onSpeakLong = { activity.readingCommands.composeSpeakLong() },
            onWorkspace = { showWorkspaceSheet() },
            onOverflow = { openOverflowMenu() },
        ),
        toolbarIcons = { readingToolbarIcons() },
        nightMode = nightMode,
        monochrome = monochrome,
        generation = generation.state,
        fullScreen = fullScreen,
        overlayText = overlayText,
        activeIsBibleShown = activeIsBibleShown,
        // F59: the live value, straight from the host -- see ReadingHostActivity
        // .imeBottomPaddingPx's kdoc for why MainBibleActivity answers a permanent 0 and the
        // nav host does not.
        imeBottomPaddingPx = activity.imeBottomPaddingPx,
        disableAnimations = { CommonUtils.settings.disableAnimations },
        overflowItems = overflowItems,
        overflowExpanded = overflowExpanded,
        onOverflowItemClick = { id ->
            val stayOpen = activity.readingCommands.handleOptionsMenuItem(id)
            if (stayOpen) {
                overflowItems.value = activity.readingCommands.buildOptionsMenuItems()
            } else {
                overflowExpanded.value = false
            }
        },
        onOverflowDismiss = { overflowExpanded.value = false },
        // Batch Z-early A6: the navigation drawer. Icons are resolved from the `iconKey` the
        // `:sharedCore` model carries (mirroring the menu XML's `android:icon`) through
        // [drawerIconResIdFor] — the same "host resolves, `:sharedUi` stays Android-free" shape
        // as `windowIcon` below, except this one must be `@Composable` because
        // `painterResource` is only callable inside composition. NOT the raw [drawerIconResIds]
        // table: `"ic_logo"` needs the call-time discrete-mode branch (F58 fix round 2 / R13).
        drawerMenu = drawerMenu,
        drawerIcon = { key ->
            val resId = drawerIconResIdFor(key)
            if (resId == null) null else painterResource(resId)
        },
        // Dispatches through the SAME `MenuCommandHandler.handleMenuRequest(itemId)` the classic
        // `NavigationView` listener calls (see `MainBibleActivity.handleDrawerItemClick`), so
        // every row's command behaviour is classic's by construction.
        onDrawerItemClick = { id -> activity.readingCommands.handleDrawerItemClick(DrawerMenuStateBuilder.resIdFor(id)) },
        // Batch Z-early A7: classic `DrawerListener` parity — see the `LaunchedEffect`s in the
        // common `ReadingHost` and the entry points' kdoc on [MainBibleActivity].
        onDrawerInMotion = { activity.showSystemUiTransient() },
        onDrawerIdleClosed = { activity.applyIdleSystemUi() },
        // Round 12b §1: the guard against stealing focus from an open search bar lives INSIDE
        // restorePaneFocus (see drawerShouldRestorePaneFocus), not here — the drawer's
        // own Search row goes through this same callback.
        onDrawerClosed = { activity.restorePaneFocus() },
        paneBackground = { windowId -> paneBackgroundArgbFor(windowId)?.let { Color(it) } },
        windowLabel = ::railWindowLabel,
        windowIcon = { snapshot -> activity.readingCommands.windowIconFor(snapshot.id) },
        // Task 4 (F2b): the rail's tiny top row (classic `topButtonText`) — see
        // `MainBibleActivity.windowTopLabelFor`'s kdoc.
        windowTopLabel = ::railWindowTopLabel,
        handlePaneMenuItem = { windowId, id -> activity.readingCommands.handleWindowPaneMenuItem(windowId, id) },
        // A/B batch 1 F5b: resolves the pane popup menu's and the toolbar overflow menu's
        // `iconKey`s to a `Painter` via the explicit [menuIconResIds] table — the same
        // "host resolves, `:sharedCore`/`:sharedUi` stay Android-free" shape as `drawerIcon`
        // just above.
        menuIcon = { key ->
            // A/B batch 3 F4: the "last used actions" rows carry a text-option drawable name,
            // which lives in its own table (shared with the Text-options screen).
            val resId = menuIconResIds[key] ?: textOptionDrawableRes(key)
            if (resId != null) painterResource(resId) else null
        },
        llmDialog = readingLlmDialogs.state,
        onLlmPromptChosen = readingLlmDialogs::onPromptChosen,
        onLlmToggleFavorite = readingLlmDialogs::onToggleFavorite,
        onLlmCategoryExpandedChanged = readingLlmDialogs::onCategoryExpandedChanged,
        onLlmSpecifySubmitted = readingLlmDialogs::onSpecifySubmitted,
        onLlmModelChosen = readingLlmDialogs::onModelChosen,
        onLlmRegenerateConfirmed = readingLlmDialogs::onRegenerateConfirmed,
        onLlmDismiss = readingLlmDialogs::dismiss,
        speakDialog = speakTransport.dialog,
        onSpeakBookmarkChosen = speakTransport::onSpeakBookmarkChosen,
        onSpeakDialogDismiss = speakTransport::dismissDialog,
        // F6 Task 8a: the toolbar's search mode. Every callback goes to [searchController] or to
        // the host's own menu/settings flags — none of them touches the pane subtree, which is
        // what `ReadingSearchHostTest.openingAndClosingSearchMustNotRebuildThePaneSubtree`
        // guards.
        searchBar = searchState.searchBar,
        searchBarCallbacks = searchState.searchBarCallbacks,
        searchSheetVisible = searchController.sheetVisible,
        onSearchSheetDismissed = { searchController.closeSheet() },
        // Task 8b Step 3: feeds MainBibleActivity.bottomOffsetForWebView's fourth term.
        onSearchSheetOffsetsChanged = { visible, heightPx -> activity.readingInsets.updateSearchSheetOffsets(visible, heightPx) },
        // Task 10: the "<document> cannot be searched" snackbar.
        searchUnavailableDocName = searchUnavailableDocName,
        onSearchUnavailableMessageShown = { searchUnavailableMessageShown() },
        // Batch 12e-B Task 6: the agent-log panel, pre-built here (closing over the live
        // `agentLog` controller) since `install` already owns it — mirrors how the pane factory is
        // provided rather than rebuilt from raw state.
        // `collapsedDp` is `ReadingViewScreen`'s measurement, handed down rather than remembered
        // here: this lambda leaves the composition on every hide, and a panel that auto-shows
        // while still `expanded` never reports a collapsed height, so a local copy would stay 0
        // for that whole showing (fix round 1, Important 1 — see the slot's kdoc).
        agentLogSlot = { applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured ->
            slots.AgentLog(applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured)
        },
        // Batch 12f Task 6: the Speak transport bar, pre-built here (closing over the live
        // `speakTransport` controller) exactly the same pass-through shape as `agentLogSlot`
        // above.
        speakBarSlot = { applyNavBarInset -> slots.SpeakBar(applyNavBarInset) },
        // Round 12b §3: whether the agent-log panel / speak bar will actually render, read as
        // `@Composable` lambdas rather than plain booleans so the flow collection stays inside
        // the composition that needs it (see the whole-branch-review non-restartable-lambda
        // note this replaced, still true here: collecting `agentLog.state`/
        // `speakTransport.state` with `by` directly at a non-Unit-returning lambda's call site
        // would subscribe the nearest enclosing restartable scope -- the `BottomSheetScaffold`
        // content lambda wrapping `key(gen) { ReadingViewScreen(...) }` -- to every log-line/
        // status/cost update during a run). Each collects its own flow as State (no `by`) and
        // folds it into a `derivedStateOf` that only changes value when the resulting boolean
        // actually flips, so only that flip recomposes the outer scope.
        agentLogVisible = {
            val agentLogState = agentLog.state.collectAsState()
            val visible = remember { derivedStateOf { agentLogState.value.visible } }
            visible.value
        },
        speakBarVisible = {
            val speakState = speakTransport.state.collectAsState()
            val visible = remember {
                derivedStateOf {
                    speakBarVisible(
                        fullScreen = fullScreen.value,
                        transportVisible = speakState.value.visible,
                    )
                }
            }
            visible.value
        },
        searchSheetSlot = { slots.SearchSheet() },
        searchSettingsSlot = { slots.SearchSettings() },
        // Settings editor sheets T10: the reading view's in-place text-settings editor.
        textSettingsEditorSlot = { slots.TextSettingsEditor() },
        // Round 13a: the Speak settings sheet.
        speakSettingsSlot = { slots.SpeakSettings() },
        quickSheetContent = { sheet -> slots.QuickSheet(sheet) },
        // Platform-dialog removal Task 18: the reading view's own dialogs (BJI help/delete-confirm).
        readingDialogSlot = { slots.ReadingDialog() },
    )

    companion object : ComposeReadingViewHostHelpers() {
        /**
         * Pure (non-`@Composable`) rail-model derivation used by [mountComposeView] — delegates
         * to [buildWindowTabBar]. Kept as its own callable, rather than inlined at the collection
         * site inside the composable body, so `ComposeReadingViewHostTest` can assert the model
         * the host derives from a given [WindowLayoutState] without a `ComposeTestRule` (this
         * repo's `:app` unit tests have none) — mirrors how
         * [net.bible.android.view.activity.page.MainBibleActivity.buildOptionsMenuItems] factors
         * the Compose overflow menu's item-building out of its own composable call site.
         */
        internal fun buildTabBarModel(layout: WindowLayoutState): WindowTabBarModel = buildWindowTabBar(layout)

        private const val TAG = "ComposeReadingViewHost"

        /** Task 26: the verse-share sheet's toggle pref keys — verbatim from the classic
         *  `ShareWidget`, so an existing user's saved choices carry over unchanged. */

        /**
         * The reading view as production composes it: the app theme (night mode tracked from the host's
         * own `State`, see [AbAppTheme]'s `darkTheme` kdoc), fail-closed link routing, [paneFactory]
         * provided as `LocalReadingPane`, and the common `ReadingHost` rendering [deps].
         *
         * Shared by [ReadingView] (the nav destination and [install]) and [mountComposeView], so the
         * wrapper cannot differ between production and the test seam.
         */
        @Composable
        fun ReadingHostContent(deps: ReadingHostDeps, paneFactory: ReadingPaneFactory) {
            // AbAppTheme's darkTheme override (A/B batch 4b Task 3 fix round 1): this host
            // is long-lived inside MainBibleActivity and is never recreate()d on
            // ScreenSettings.nightModeChanges (including the ambient-light-sensor
            // auto-night-mode flip, which fires with no recreate at all), so it tracks night
            // mode itself in nightMode and passes it through verbatim instead of
            // letting AbAppTheme re-read the static ScreenSettings.nightMode getter (which
            // would only catch up on some unrelated recomposition). See AbAppTheme's KDoc.
            AbAppTheme(darkTheme = deps.nightMode.value) {
                FailClosedLinkRouting {
                    CompositionLocalProvider(LocalReadingPane provides paneFactory) {
                        ReadingHost(deps)
                    }
                }
            }
        }

        /**
         * **TEST-ONLY -- this function has no production caller.** It is the seam the host tests
         * (`ComposeReadingViewHostTest`, `ReadingSearchHostTest`, `AgentLogHostTest`,
         * `ReadingLlmHostTest`, `SearchSheetStructureGuardTest`) use to mount the SAME composition
         * production renders ([ReadingHostContent]) with a [deps] that wires only the slots they
         * exercise, without booting a full reading host.
         *
         * Adds a [ComposeView] rendering the reading view to [container].
         */
        fun mountComposeView(
            container: ViewGroup,
            deps: ReadingHostDeps,
            paneFactory: ReadingPaneFactory = { _, _ -> },
        ) {
            val composeView = ComposeView(container.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setContent { ReadingHostContent(deps, paneFactory) }
            }
            container.addView(composeView)
        }
    }
}

/**
 * F58: the toolbar's Bible icon under discrete mode, mirroring classic `MainBibleActivity.kt:563`
 * and `DocumentBadges.kt`'s `BookCategory.imageResource` (`:63`). A plain function rather than an
 * inline `painterResource` argument so `DiscreteChromeTest` can assert the CHOICE — the rendered
 * painter is not observable in a unit test, and the choice is the behaviour.
 */
internal fun bibleToolbarIconRes(): Int =
    if (CommonUtils.isDiscrete) R.drawable.ic_baseline_menu_book_24 else R.drawable.ic_bible_24dp

/**
 * [ReadingToolbarIcons] for the reading view ([ComposeReadingViewHost.ReadingView]) — the same
 * `main_bible_view.xml` toolbar drawables `ReadingToolbarGoldenTest` uses (a single static icon
 * per action; the classic Strongs button's OT/NT + link-variant icon swap in
 * `MainBibleActivity.updateStrongsButton` is not reproduced here — [net.bible.sharedui.reading.ReadingToolbar]
 * only dims the single Strongs icon via [ToolbarState.strongsMode]).
 */
@Composable
private fun readingToolbarIcons() = ReadingToolbarIcons(
    home = painterResource(R.drawable.ic_menu),
    search = painterResource(R.drawable.ic_search_24dp),
    speak = painterResource(R.drawable.ic_baseline_headphones_24),
    strongs = painterResource(R.drawable.ic_strongs_hebrew),
    bible = painterResource(bibleToolbarIconRes()),
    commentary = painterResource(R.drawable.ic_commentary),
    workspace = painterResource(R.drawable.ic_workspace_solid_24dp),
    overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
    sync = painterResource(R.drawable.ic_syncdb_24dp),
)
