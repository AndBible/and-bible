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

import android.content.ClipData
import android.content.Intent
import android.text.format.DateFormat.format
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.FailClosedLinkRouting
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.buildGridStep
import net.bible.android.view.activity.navigation.genbookmap.keyChooserKeys
import net.bible.android.view.activity.navigation.initialGridOptions
import net.bible.android.view.activity.navigation.persistGridOptions
import net.bible.android.view.activity.navigation.pickGridBook
import net.bible.android.view.activity.navigation.pickGridChapter
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.KeyIsNull
import net.bible.android.view.activity.page.ReadingCommands
import net.bible.android.view.activity.page.ReadingHostActivity
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.WORKSPACE_CHANGED
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.android.view.activity.page.androidBibleView
import net.bible.android.view.activity.page.bibleViewBackgroundColorFor
import net.bible.android.view.activity.search.EPUB_SEARCH_TYPE_KEY
import net.bible.android.view.activity.search.epubKeyFor
import net.bible.android.view.activity.search.epubSearchModeFromClassicName
import net.bible.android.view.activity.search.epubSearchRunFor
import net.bible.android.view.activity.search.toClassicSearchTypeName
import net.bible.android.view.activity.settings.BackgroundThumbnailResolver
import net.bible.android.view.activity.settings.buildBackgroundImageChooserLabels
import net.bible.android.view.activity.settings.buildColorSettingsLabels
import net.bible.android.view.activity.settings.buildTextDisplayControllerLabels
import net.bible.android.view.activity.settings.buildTextDisplayScreenLabels
import net.bible.service.common.CommonUtils
import net.bible.service.common.RecentDocumentsStore
import net.bible.service.common.automaticSpeakBookmarkingVideo
import net.bible.service.common.speakHelpVideo
import net.bible.service.device.ScreenSettings
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.hideFromSelector
import net.bible.service.history.HistoryItem
import net.bible.service.history.HistoryManager
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.AgentForegroundService
import net.bible.service.sword.BookAndKey
import net.bible.service.sword.BookAndKeyList
import net.bible.service.sword.SwordDocumentFacade
import net.bible.service.sword.epub.isEpub
import net.bible.service.sword.mydocument.isMyDocument
import net.bible.service.sword.nameWithoutDocument
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.agentPanelHeight
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.ai.reading.ReadingLlmDialogController
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.history.HistoryController
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentQuickTabs
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.KeyRow
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
import net.bible.sharedcore.search.EpubSearchResultsController
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.IndexPollDecision
import net.bible.sharedcore.search.ProgressJob
import net.bible.sharedcore.search.ReadingSearchController
import net.bible.sharedcore.search.ReadingSearchPhase
import net.bible.sharedcore.search.SearchBibleSection
import net.bible.sharedcore.search.SearchDocumentInfo
import net.bible.sharedcore.search.SearchIndexProgressController
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchKind
import net.bible.sharedcore.search.SearchQueryController
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.SearchResultsController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.forEpubOf
import net.bible.sharedcore.search.searchKindFor
import net.bible.sharedcore.search.searchTranslationIds
import net.bible.sharedcore.settings.ColorSettingsController
import net.bible.sharedcore.settings.SettingsEditorPage
import net.bible.sharedcore.settings.SettingsEditorStack
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.SettingsScreenState
import net.bible.sharedcore.settings.TextDisplaySettingsController
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.speak.AdvancedSpeakSettingsController
import net.bible.sharedcore.speak.BibleSpeakSettingsController
import net.bible.sharedcore.speak.PickedVerse
import net.bible.sharedcore.speak.SpeakRangeEditor
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakSheetPage
import net.bible.sharedcore.speak.SpeakSheetStack
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.speak.sleepTimerSelectionFor
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedcore.workspaces.WorkspaceQuickController
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.reading.AgentLogPanel
import net.bible.sharedui.ai.reading.ReadingLlmDialogs
import net.bible.sharedui.components.AbConfirmDialog
import net.bible.sharedui.components.AbErrorDialog
import net.bible.sharedui.components.AbLinkRouting
import net.bible.sharedui.components.AbMessageDialog
import net.bible.sharedui.components.AbQuickSheet
import net.bible.sharedui.components.AbQuickSheetFooterRow
import net.bible.sharedui.components.AbQuickSheetTab
import net.bible.sharedui.history.HistoryListContent
import net.bible.sharedui.progress.AbReadHistorySheet
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.sharedui.navigation.DocumentQuickContent
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.navigation.GridOptionsOverflow
import net.bible.sharedui.navigation.KeyListBody
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.host.LocalReadingPane
import net.bible.sharedui.reading.host.ReadingHost
import net.bible.sharedui.reading.host.ReadingHostDeps
import net.bible.sharedui.reading.host.ReadingHostHooks
import net.bible.sharedui.reading.host.ReadingPaneFactory
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.reading.ShareVersesSheet
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.search.BibleResultsActions
import net.bible.sharedui.search.BibleSearchSettings
import net.bible.sharedui.search.EpubSearchSettings
import net.bible.sharedui.search.SearchIndexPanel
import net.bible.sharedui.search.SearchSettingsSheet
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.search.bibleResultRows
import net.bible.sharedui.search.epubResultRows
import net.bible.sharedui.settings.ColorSettingsEditorSheet
import net.bible.sharedui.settings.GenericSettingsEditorSheet
import net.bible.sharedui.settings.TextSettingRowEditorSheet
import net.bible.sharedui.speak.AdvancedSpeakSettingsContent
import net.bible.sharedui.speak.SleepTimerContent
import net.bible.sharedui.speak.SpeakRangeContent
import net.bible.sharedui.speak.SpeakSettingsContent
import net.bible.sharedui.speak.SpeakSettingsSheet
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.textOptionDrawableRes
import net.bible.sharedui.workspaces.WorkspaceQuickContent
import org.crosswire.common.progress.JobManager
import org.crosswire.common.progress.Progress
import org.crosswire.common.progress.WorkEvent
import org.crosswire.common.progress.WorkListener
import org.crosswire.jsword.book.Book
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.passage.Key
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseFactory
import org.crosswire.jsword.versification.BibleBook
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
            closeSpeakSheet = { speakSheet.close() },
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

    /** Fires the same `Intent.ACTION_SEND` chooser the classic `ShareWidget`'s Share button did. */
    internal fun shareVersesText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val chooserIntent = Intent.createChooser(sendIntent, activity.hostContext.getString(R.string.share_verse_menu_title))
        activity.hostContext.startActivity(chooserIntent)
    }

    /** Copies [text] to the clipboard exactly as the classic `ShareWidget`'s Copy button did (same
     *  toast, via [CommonUtils.copyToClipboard]). [clipLabel] is only the `ClipData`'s invisible
     *  description, not part of the copied text. */
    internal fun copyVersesText(text: String, clipLabel: String?) {
        CommonUtils.copyToClipboard(ClipData.newPlainText(clipLabel, text))
    }

    /**
     * [ReadingQuickSheet.Share]'s `onShare`/`onCopy` wiring (I3, run 3 final-review fix wave): the
     * classic `ShareWidget` dialog's Share and Copy were `AlertDialog` buttons, which dismiss the
     * dialog on click -- these wrap [shareVersesText]/[copyVersesText] with [closeQuickSheet] to
     * restore that. Without it the sheet stayed open after the share chooser returned, or after
     * Copy's toast, across all three entry points (selection menu, bookmark share, JS share).
     *
     * `internal`, not private lambdas inlined at the `QuickSheetContent` call site, purely so
     * `ComposeReadingViewHostTest` can pin the close-after-action behaviour directly -- an open
     * `ModalBottomSheet` must never be idled in a Robolectric test (this run's "Hang protection"
     * rule), so the wiring has to be reachable without rendering the sheet.
     */
    internal fun shareVersesTextAndCloseSheet(text: String) {
        shareVersesText(text)
        closeQuickSheet()
    }

    internal fun copyVersesTextAndCloseSheet(text: String, clipLabel: String?) {
        copyVersesText(text, clipLabel)
        closeQuickSheet()
    }

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

    @Composable
    private fun quickDocTabLabel(tab: DocumentQuickTab): String = when (tab) {
        DocumentQuickTab.RECENT -> LocalStrings.current.documentTabRecent
        DocumentQuickTab.FOR_VERSE -> LocalStrings.current.documentTabForVerse
        DocumentQuickTab.LAST_FILTER -> LocalStrings.current.documentTabLastFilter
        DocumentQuickTab.ALL -> LocalStrings.current.documentTabAll
    }

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

    /** The document shown in the active window — the row the sheet draws bold and inert. */
    private fun currentDocumentInitials(): String? = documentControl.currentDocument?.initials

    /**
     * The full ChooseDocument screen — `MainBibleActivity.composeChooseDocument`'s classic body,
     * repeated rather than delegated because that method now routes BACK here (it is the title
     * long-press's reroute point), so calling it would recurse.
     */
    private fun openChooseDocument(type: String? = null) {
        activity.hostActivity.startActivityForResult(
            NavHostComposeActivity.intentFor(activity.hostContext, NavRoutes.chooseDocument(type)),
            ActivityBase.STD_REQUEST_CODE,
        )
    }

    /**
     * The key list the CURRENTLY OPEN key-chooser sheet is showing, handed in by
     * [showKeyChooserSheet] and never resolved by the sheet itself. See that function for why.
     *
     * A plain `var` rather than snapshot state on purpose: it is written BEFORE the [quickSheet]
     * state that triggers the composition which reads it, so no recomposition ever has to observe
     * it changing.
     */
    private val keyChooserKeys: List<Key> get() = hostState.keyChooserKeys

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

    /**
     * The current row's id for [kind]: the INDEX of the page's key in [keys], or null when the page
     * has no key or its key is not in the list. `ChooseMapKeyComposeActivity`'s `currentRow`
     * verbatim, and identical in the general-book twin.
     */
    private fun currentKeyChooserRowId(kind: KeyChooserKind, keys: List<Key>): String? {
        val current = when (kind) {
            KeyChooserKind.Map -> mapPage.key
            KeyChooserKind.GeneralBook -> generalBookPage.key
            KeyChooserKind.Grid -> null
        } ?: return null
        return keys.indexOf(current).takeIf { it >= 0 }?.toString()
    }

    /**
     * Apply a key picked in a key-chooser sheet, through the SAME `MainBibleActivity` function the
     * `in genBookClasses ->` activity-result arm calls (Task 9 amendment E4).
     *
     * The `Key` goes across as itself, never serialized into extras and parsed back: the sheet is
     * holding the real object, and a round trip through an Intent would be a second code path
     * pretending to be one. Which book it belongs to follows each activity's own `buildResult`: an
     * EPUB table-of-contents entry is a [BookAndKey] carrying its own document, everything else
     * belongs to the page's current document.
     */
    private fun applyKeyChooserKey(kind: KeyChooserKind, key: Key) = when (kind) {
        KeyChooserKind.Map -> activity.readingCommands.applyChosenGenBookKey(mapPage.currentDocument, key)
        KeyChooserKind.GeneralBook ->
            if (key is BookAndKey) {
                activity.readingCommands.applyChosenGenBookKey(key.document, key)
            } else {
                activity.readingCommands.applyChosenGenBookKey(generalBookPage.currentDocument, key)
            }
        KeyChooserKind.Grid -> Unit
    }

    /**
     * The [HistoryEntry] list, mirroring the `controller` of the since-deleted
     * `HistoryComposeActivity` verbatim
     * (`historyManager.getHistory(activeWindow.id)` + the `"h:mm a, E d MMM "` timestamp format
     * both the classic screen and the goldens depend on). [historyItems] is stashed alongside so
     * [revertToHistoryItem] can resolve an entry's `id` (its list index, not a stable key) back to
     * the [HistoryItem] to revert.
     */
    private fun historyEntriesForActiveWindow(): List<HistoryEntry> = hostState.historyEntriesForActiveWindow()

    private fun revertToHistoryItem(id: Int) = hostState.revertToHistoryItem(id)

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

    // ------------------------------------------------------------------------------------------
    // F6 Task 8a — SWORD search inside the reading view (the toolbar's search mode + the sheet).
    // ------------------------------------------------------------------------------------------

    private val bibleSearchService: BibleSearchService by inject()
    private val searchIndexService: SearchIndexService by inject()
    private val searchResultsCache: SearchResultsCache by inject()
    private val searchControl: SearchControl by inject()

    /** F43 Task 4: the EPUB counterpart of [bibleSearchService]. Declared BEFORE [epubSearchResults],
     *  which consumes it at construction time. */
    private val epubSearchService: EpubSearchService by inject()

    /**
     * Query text + the recent-terms MRU. Persistence is host-side under the SAME settings key the
     * search Activities use (`SearchComposeActivity.kt:199-208`), **newline**-separated because a
     * query may contain a comma — unlike the translation list, which is comma-joined.
     */
    private val searchQueries = SearchQueryController(
        persistRecentTerms = { terms ->
            CommonUtils.settings.setString(SEARCH_RECENT_TERMS_KEY, terms.joinToString("\n"))
        },
        loadRecentTerms = { loadRecentSearchTerms() },
    )

    /**
     * The search *settings* (word mode, bible section, translations) and the list of Bibles to
     * choose from. Host-owned Compose `State` rather than a `SearchFormController`: that controller
     * takes `currentBookName` as a constructor value, whereas the reading view's current book moves
     * as the user reads, so the name is read fresh per search instead (see [buildSearchRequest]).
     */
    private val searchType = mutableStateOf(SearchType.ALL_WORDS)
    // `internal`, not `private` — same test-visibility convention as [buildSearchRequest] — so a
    // test can prove Strong's find-all forces `ALL` even when the settings sheet has been left on
    // a non-default section (review item A), without a `ComposeTestRule` to drive the real sheet.
    internal val searchSection = mutableStateOf(SearchBibleSection.ALL)
    private val searchTranslations = mutableStateOf<List<String>>(emptyList())
    private val searchAvailableTranslations = mutableStateOf<List<Pair<String, String>>>(emptyList())

    // ------------------------------------------------------------------------------------------
    // Settings editor sheets T10 — the reading view's in-place text-settings editor, opened over
    // the reading view instead of launching TextDisplaySettingsComposeActivity (which pushed two
    // destinations the user had to back out of separately). Wired to a menu in a later task; this
    // task only builds the callable, tested surface.
    // ------------------------------------------------------------------------------------------

    private val textDisplaySettingsService: TextDisplaySettingsService by inject()

    /** The in-place text-settings editor's page stack — the reading view's own instance,
     *  independent of the settings activity's [SettingsEditorStack]. */
    internal val textSettingsEditor = SettingsEditorStack()

    /** The scope the open editor edits, and what to run after a change lands. Set by
     *  [showTextSettingEditor]; the callback is the caller's own `onReady`, so a future workspace-
     *  menu dispatch site can refresh every window while a pane-menu one refreshes just its own.
     *  `mutableStateOf`, not a plain `var`: [TextSettingsEditorSlot] reads it in composition via
     *  `remember(scope)`, and a plain non-snapshot read is never recomposed for -- the same
     *  "modifier runs before content" class of bug [searchResultsListState] elsewhere in this file
     *  already paid for once, one property away from repeating it here. */
    private var textSettingsScope: SettingsScope? by mutableStateOf(null)
    private var textSettingsOnReady: () -> Unit = {}

    /** One [TextDisplaySettingsController] per visited [SettingsScope] — cached like
     *  `TextDisplaySettingsComposeActivity.controllerCache`, for the same reason: a `collectAsState`
     *  subscriber must not lose its subscription across recompositions. */
    private val textSettingsControllers = mutableMapOf<SettingsScope, TextDisplaySettingsController>()

    private val textSettingsLabels by lazy { buildTextDisplayControllerLabels(activity.hostContext) }
    private val textSettingsScreenLabels by lazy { buildTextDisplayScreenLabels(activity.hostContext) }
    private val colorSettingsLabels by lazy { buildColorSettingsLabels(activity.hostContext) }
    private val backgroundChooserLabels by lazy { buildBackgroundImageChooserLabels(activity.hostContext) }
    private val backgroundThumbnailResolver = BackgroundThumbnailResolver()

    /**
     * Open the in-place editor for one text display setting, over the reading view. Meant to be
     * called from `MainBibleActivity`'s two menu dispatch sites instead of `Preference.openDialog`
     * (a later task), so the user never leaves the reading view and back has nowhere to land but
     * here — the defect this whole feature exists to fix dies by construction rather than being
     * worked around.
     */
    internal fun showTextSettingEditor(
        scope: SettingsScope,
        page: SettingsEditorPage,
        onReady: () -> Unit,
    ) {
        // Total mutual exclusion (see [readingLlmDialogs]'s `onSheetOpening` kdoc for the rule and
        // why it is stated once, purely, in `:sharedCore`): this editor is a modal overlay of the
        // reading view, so opening it closes whatever other one is up. Applied at the 14a/14a-2
        // merge, per round 14a's status entry.
        ReadingOverlayExclusion.closedBy(ReadingOverlay.TextSettingsEditor).forEach(::closeOverlay)
        // Close first: [SettingsEditorStack.open] assigns a `MutableStateFlow`, which conflates an
        // equal value, so opening the SAME page for a DIFFERENT scope right after a previous open
        // would emit nothing and [TextSettingsEditorSlot] would keep rendering the OLD scope's
        // cached controller. Closing unconditionally first guarantees `open`'s value always differs
        // from empty, so it always emits.
        textSettingsEditor.close()
        textSettingsScope = scope
        textSettingsOnReady = onReady
        // The cached controller for this scope (see [textSettingsControllerFor]) only reloads after
        // its OWN writes, so an edit made through a different controller instance — the settings
        // screen's, a second window's, sync, or a classic dialog — would otherwise leave this one
        // showing a stale snapshot: the sheet's slider could start at an old value and, worse, WRITE
        // it back on confirm, undoing the other edit. Mirrors
        // `TextDisplaySettingsComposeActivity.pop()`'s `controllerFor(scope).refresh()` for exactly
        // the same reason.
        textSettingsControllerFor(scope).refresh()
        textSettingsEditor.open(page)
    }

    /** `internal`, not `private` -- same test-visibility convention as [searchSettingsOpen] above:
     *  `:app` has no `ComposeTestRule` to drive [TextSettingsEditorSlot]'s composition, so proving
     *  this construction itself never bumps [generation] means a test calling it directly. */
    internal fun textSettingsControllerFor(scope: SettingsScope): TextDisplaySettingsController =
        textSettingsControllers.getOrPut(scope) {
            TextDisplaySettingsController(
                service = textDisplaySettingsService,
                settingsScope = scope,
                labels = textSettingsLabels,
                // The in-place editor never navigates: every navigating key (the two drill-up
                // links, COLORS, BOOKMARKS_HIDELABELS) is filtered out by textSettingEditorPageFor
                // before showTextSettingEditor is ever reached with this scope's controller.
                onNavigateCallback = { },
            )
        }

    /** NOT cached across editor opens, for the exact reason
     *  `TextDisplaySettingsComposeActivity.colorControllerFor`'s kdoc gives: its state is loaded
     *  once in the constructor, and a whole-scope reset elsewhere (this controller's own `onReset`)
     *  can change colours behind an idle instance — a cached, stale instance would then reopen
     *  showing pre-reset values.
     *
     *  `internal`, not `private` -- same test-visibility reason as [textSettingsControllerFor]
     *  above: a test needs to construct one directly to prove doing so never bumps [generation]. */
    internal fun colorControllerFor(scope: SettingsScope): ColorSettingsController =
        ColorSettingsController(
            service = textDisplaySettingsService,
            scope = scope,
            coroutineScope = hostScope,
            imagePicker = activity.readingCommands.textSettingsImagePicker,
        )

    /**
     * Which of the eight [SettingsEditorPage.Row] keys [net.bible.sharedcore.settings
     * .textSettingEditorPageFor] can hand back are rendered as a [GenericSettingsEditorSheet]
     * list-choice page rather than a [TextSettingRowEditorSheet] numeric/margin page — mirrors
     * `TextDisplaySettingsScreen`'s `SHEET_EDITED_TEXT_SETTING_KEYS` split, inverted: that screen's
     * own [AbSettingsContent] (via `AbListChoiceDialog`) renders these four inline and never routes
     * them through its editor stack at all, but this host has no such native list-choice dialog of
     * its own, so it must render the same page body [GenericSettingsEditorSheet] does.
     */
    private val listChoiceTextSettingKeys = setOf(
        TextSettingType.FONTFAMILY.name,
        TextSettingType.STRONGS.name,
        TextSettingType.PAGE_SCROLL_AMOUNT.name,
        TextSettingType.SCROLL_HELPER_LINE_STYLE.name,
    )

    /** The in-place text-settings editor sheet — mounted as the FIFTH sibling overlay next to
     *  [SearchSettingsSlot] (see its own mounting comment at the `mountComposeView` call site).
     *  Self-hides via [SettingsEditorSheet]/[TextSettingRowEditorSheet]/[ColorSettingsEditorSheet]'s
     *  own null/empty-pages early-return, so a host can render this unconditionally. */
    @Composable
    private fun TextSettingsEditorSlot() {
        val pages by textSettingsEditor.pages.collectAsState()
        val page = pages.lastOrNull() ?: return
        val scope = textSettingsScope ?: return
        val controller = remember(scope) { textSettingsControllerFor(scope) }
        val state by controller.state.collectAsState()

        when (page) {
            is SettingsEditorPage.Row -> {
                if (page.key in listChoiceTextSettingKeys) {
                    GenericSettingsEditorSheet(
                        state = SettingsScreenState(title = state.title, items = state.items),
                        editor = textSettingsEditor,
                        page = page,
                        depth = pages.size,
                        onListChoice = { key, v -> controller.onListChoice(key, v); textSettingsOnReady() },
                        // Neither TextInputRow nor MultiSelectRow ever occurs among text-display
                        // settings (see TextDisplaySettingsController.buildRow's when), so these
                        // two are unreachable here — kept as harmless no-ops rather than throwing,
                        // matching GenericSettingsEditorSheet's own "safe no-op" discipline for a
                        // SettingsItem shape RenderSettingsItem never produces for this row kind.
                        onTextInput = { _, _ -> },
                        onMultiSelectChange = { _, _ -> },
                    )
                } else {
                    TextSettingRowEditorSheet(
                        pages = pages,
                        rows = state.rows,
                        dialogLabels = textSettingsScreenLabels,
                        onNumericChange = { key, v -> controller.onNumericChange(key, v); textSettingsOnReady() },
                        onMarginsChange = { key, l, r, m ->
                            controller.onMarginsChange(key, l, r, m); textSettingsOnReady()
                        },
                        onRevert = { key -> controller.onRevert(key); textSettingsOnReady() },
                        onPop = { textSettingsEditor.pop() },
                        onClose = { textSettingsEditor.close() },
                    )
                }
            }
            is SettingsEditorPage.Colors,
            is SettingsEditorPage.ColorPick,
            is SettingsEditorPage.BackgroundImage -> {
                val colorController = remember(scope) { colorControllerFor(scope) }
                val colorState by colorController.state.collectAsState()
                ColorSettingsEditorSheet(
                    pages = pages,
                    state = colorState,
                    labels = colorSettingsLabels,
                    chooserLabels = backgroundChooserLabels,
                    thumbnailFor = backgroundThumbnailResolver::resolve,
                    importVisible = true,
                    onColorChange = { f, c -> colorController.onColorChange(f, c); textSettingsOnReady() },
                    onWorkspaceColorChange = { c -> colorController.onWorkspaceColorChange(c); textSettingsOnReady() },
                    onWorkspaceColorReset = { colorController.onWorkspaceColorReset(); textSettingsOnReady() },
                    onNoiseChange = { n, v -> colorController.onNoiseChange(n, v); textSettingsOnReady() },
                    onOpacityChange = { n, v -> colorController.onOpacityChange(n, v); textSettingsOnReady() },
                    onSelectBackgroundImage = { n, i ->
                        colorController.onSelectBackgroundImage(n, i); textSettingsOnReady()
                    },
                    onImportBackgroundImage = colorController::onImportBackgroundImage,
                    onRequestDeleteBackgroundImage = colorController::onRequestDeleteBackgroundImage,
                    onConfirmDeleteBackgroundImage = colorController::onConfirmDeleteBackgroundImage,
                    onDismissDeleteConfirm = colorController::onDismissDeleteConfirm,
                    // Final fix wave, Fix 4: reuses the SAME textSettingsScreenLabels resolved
                    // strings the Row-page branch above already passes to TextSettingRowEditorSheet
                    // -- no new string, no new label bundle.
                    onReset = { colorController.onReset(); textSettingsOnReady() },
                    resetConfirmMessage = textSettingsScreenLabels.resetConfirmMessage,
                    confirmLabel = textSettingsScreenLabels.okLabel,
                    cancelLabel = textSettingsScreenLabels.cancelLabel,
                    onPush = { textSettingsEditor.push(it) },
                    onPop = { textSettingsEditor.pop() },
                    onClose = { textSettingsEditor.close() },
                )
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Round 13a — the Speak settings sheet over the reading view. Replaces the two deleted Compose
    // Speak activities (T4); S13 then deleted the classic ones and Batch Z-late's epilogue removed
    // the `Screen.BibleSpeak` enum entry, so this host is the WHOLE Speak settings surface, on
    // either side of the port. Guarded by `SpeakEntryPointGuardTest`.
    // ------------------------------------------------------------------------------------------

    /** The Speak sheet's page stack. One instance per host, like [textSettingsEditor] -- `internal`
     *  for the same reason (test-only reads of which page is open, no `ComposeTestRule` in this
     *  module). */
    internal val speakSheet = SpeakSheetStack()

    /** The repeat-range draft. Deliberately host-lived rather than page-lived: its endpoints are
     *  picked on the PickVerse page, so a draft that died with the RepeatRange page's composition
     *  would lose the endpoint the user just chose. */
    private val speakRangeEditor = SpeakRangeEditor()
    private val speakSettingsController = BibleSpeakSettingsController(speakSettingsService)
    private val advancedSpeakController = AdvancedSpeakSettingsController(speakSettingsService)

    /**
     * Non-null only while the verse-picker page is composed. [GridChoosePassageController.back]
     * returns `false` at its own root, which is exactly "the grid has nothing left to pop" — so the
     * sheet's dismiss can unwind BOOK←CHAPTER←VERSE first and only pop the sheet page once the
     * grid is exhausted. Without this, one dismiss would jump straight out of the picker.
     */
    private var speakGridBack: (() -> Boolean)? = null

    /**
     * Open the Speak settings sheet over the reading view. The only way in: round 13a deleted the
     * Compose Speak activities, S13 the classic ones, and Batch Z-late's epilogue the
     * `Screen.BibleSpeak` enum entry that used to route to them.
     */
    internal fun showSpeakSettings() {
        // Total mutual exclusion, the third and last application site of the rule -- see
        // [readingLlmDialogs]'s `onSheetOpening` kdoc. Applied at the 14a/14a-2 merge, per round
        // 14a's status entry.
        ReadingOverlayExclusion.closedBy(ReadingOverlay.SpeakSheet).forEach(::closeOverlay)
        // `open` assigns `listOf(page)`, so this always lands on the Settings page whatever depth
        // the sheet was left at — reopening never resumes a half-finished range edit. The `close()`
        // is kept for symmetry with [showTextSettingEditor] (and because an explicit empty state
        // before the open is the cheapest way to keep that guarantee obvious); unlike there, no
        // conflation hazard applies here, since the only value `open` could conflate with is
        // `[Settings]` itself, which is already what this method wants.
        speakSheet.close()
        speakSheet.open(SpeakSheetPage.Settings)
    }

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

    /** Moved here verbatim from the deleted `BibleSpeakComposeActivity`, which owned it until 13a
     *  (its `onSystemTtsSettings` lambda — an implicit intent, no extras, no result handling). */
    private fun openSystemTtsSettings() {
        activity.hostActivity.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
    }

    /**
     * Platform-dialog removal Task 18: the Speak/Advanced-Speak help dialogs' shared state — ONE
     * `MutableState` (only one of the two sheet pages that opens either is ever composed at a time),
     * set by [showSpeakHelp]/[showAdvancedSpeakHelp] and rendered inside [SpeakSettingsSlot] as a
     * dialog OVER the sheet ([ReadingOverlayExclusion]'s kdoc: dialog-over-sheet is fine), so this
     * needs no new overlay/slot param — the same reasoning as [ReadingDialog]. `internal`, like
     * [textSettingsControllerFor]/[colorControllerFor], purely for test-only reads (no
     * `ComposeTestRule` in this module).
     */
    internal val speakHelp = mutableStateOf<String?>(null)

    /** The Speak help dialog's content, moved here verbatim from the deleted
     *  `BibleSpeakComposeActivity`'s `showHelp()`. `internal` for the same test-only reason as
     *  [speakHelp]. */
    internal fun showSpeakHelp() {
        speakHelp.value = ("<b>${activity.getString(R.string.speak)}</b><br><br>"
            + "<b><a href=\"$speakHelpVideo\">${activity.getString(R.string.watch_tutorial_video)}</a></b>")
    }

    /**
     * The ADVANCED Speak help dialog's content — the auto-bookmarking / playback-settings
     * explanations, moved here verbatim from the deleted `SpeakSettingsComposeActivity`'s
     * `showHelp()`. A second seam next to [showSpeakHelp] because it is a different dialog with
     * different content: this one explains the two least self-evident switches on the Advanced page
     * (`conf_speak_auto_bookmark` and `conf_save_playback_settings_to_bookmarks`). `internal` for
     * the same test-only reason as [showSpeakHelp].
     */
    internal fun showAdvancedSpeakHelp() {
        speakHelp.value = (
            "<b>${activity.getString(R.string.conf_speak_auto_bookmark)}</b><br><br>"
                + "<b><a href=\"$automaticSpeakBookmarkingVideo\">${activity.getString(R.string.watch_tutorial_video)}</a></b><br><br>"
                + activity.getString(R.string.speak_help_auto_bookmark)
                + "<br><br><b>${activity.getString(R.string.conf_save_playback_settings_to_bookmarks)}</b><br><br>"
                + activity.getString(R.string.speak_help_playback_settings)
                + "<br><br>" + activity.getString(R.string.speak_help_playback_settings_example)
            )
    }

    /**
     * The Speak settings sheet — mounted as the SIXTH sibling overlay next to [TextSettingsEditorSlot]
     * (see its own mounting comment at the [mountComposeView] call site). [SpeakSettingsSheet]
     * self-hides on a null page, so the host renders this unconditionally.
     */
    @Composable
    private fun SpeakSettingsSlot() {
        val pages by speakSheet.pages.collectAsState()
        val page = pages.lastOrNull()
        val playback by speakSettingsController.playback.collectAsState()
        SpeakSettingsSheet(
            page = page,
            depth = pages.size,
            // Grid-aware: the verse-picker page owns an inner BOOK→CHAPTER→VERSE back-stack, and a
            // plain `pop()` would skip every one of its steps. See [speakGridBack].
            onDismiss = {
                val gridBack = speakGridBack
                if (gridBack == null || !gridBack()) speakSheet.pop()
            },
            onClose = { speakSheet.close() },
        ) { current, scroll ->
            when (current) {
                SpeakSheetPage.Settings -> SpeakSettingsContent(
                    playback = playback,
                    onSpeedChange = speakSettingsController::setSpeed,
                    onSpeakChapterChanges = speakSettingsController::setSpeakChapterChanges,
                    onSpeakTitles = speakSettingsController::setSpeakTitles,
                    onSpeakFootnotes = speakSettingsController::setSpeakFootnotes,
                    onOpenRepeatRange = {
                        speakRangeEditor.seed(playback.repeatRangeStart, playback.repeatRangeEnd)
                        speakSheet.push(SpeakSheetPage.RepeatRange)
                    },
                    onOpenSleepTimer = { speakSheet.push(SpeakSheetPage.SleepTimer) },
                    onOpenAdvanced = { speakSheet.push(SpeakSheetPage.Advanced) },
                    onSystemTtsSettings = { openSystemTtsSettings() },
                    onHelp = { showSpeakHelp() },
                    // Round 14b §7.b: the sheet shell owns this state so it can fade the clip.
                    scrollState = scroll,
                )
                SpeakSheetPage.Advanced -> {
                    val advanced by advancedSpeakController.advanced.collectAsState()
                    AdvancedSpeakSettingsContent(
                        advanced = advanced,
                        onSynchronize = advancedSpeakController::setSynchronize,
                        onReplaceDivineName = advancedSpeakController::setReplaceDivineName,
                        onAutoBookmark = advancedSpeakController::setAutoBookmark,
                        onRestoreSettingsFromBookmarks = advancedSpeakController::setRestoreSettingsFromBookmarks,
                        onHelp = { showAdvancedSpeakHelp() },
                        scrollState = scroll,
                    )
                }
                SpeakSheetPage.RepeatRange -> {
                    val start by speakRangeEditor.start.collectAsState()
                    val end by speakRangeEditor.end.collectAsState()
                    val orderError by speakRangeEditor.showOrderError.collectAsState()
                    val canCommit by speakRangeEditor.canCommit.collectAsState()
                    SpeakRangeContent(
                        start = start,
                        end = end,
                        showOrderError = orderError,
                        canCommit = canCommit,
                        onPickStart = { speakSheet.push(SpeakSheetPage.PickVerse(end = false)) },
                        onPickEnd = { speakSheet.push(SpeakSheetPage.PickVerse(end = true)) },
                        onClear = {
                            speakRangeEditor.clearDraft()
                            speakSettingsController.clearRepeatRange()
                            speakSheet.pop()
                        },
                        onConfirm = {
                            // Read the flows, not the collected values: `onConfirm` can fire in the
                            // same frame a pick landed, before this composition has recomposed.
                            // The binding rule is `SpeakRangeEditor.canCommit` — both endpoints set
                            // AND end.ordinal > start.ordinal — and it is read from the same
                            // snapshot as the endpoints. The host must not restate a laxer version
                            // of it: the ordering rule lives in :sharedCore, once.
                            val s = speakRangeEditor.start.value
                            val e = speakRangeEditor.end.value
                            if (speakRangeEditor.canCommit.value && s != null && e != null) {
                                speakSettingsController.setRepeatRange(s.osisId, e.osisId)
                            }
                            speakSheet.pop()
                        },
                        onCancel = { speakSheet.pop() },
                    )
                }
                is SpeakSheetPage.PickVerse -> SpeakVersePickerPage(current.end)
                SpeakSheetPage.SleepTimer -> SleepTimerContent(
                    selection = sleepTimerSelectionFor(playback.sleepTimerMinutes),
                    // A never-set timer has no stored value to open the custom slider on, so fall
                    // back to the last one the user chose (`lastSleepTimer`, classic's own memory).
                    customMinutes = playback.sleepTimerMinutes.takeIf { it > 0 }
                        ?: playback.lastSleepTimerMinutes,
                    onPick = { minutes, closeAfter ->
                        speakSettingsController.setSleepTimerMinutes(minutes)
                        // `closeAfter` comes from the CHIP, not from the value: a custom slider
                        // released on exactly 30 is a preset NUMBER but not a chip tap, and popping
                        // on the number closed the page in the middle of an adjustment. Off and the
                        // presets commit outright; a custom drag stays on the page.
                        if (closeAfter) speakSheet.pop()
                    },
                )
            }
        }
        // A dialog over the sheet (ReadingOverlayExclusion's kdoc) -- see [speakHelp]'s kdoc.
        speakHelp.value?.let { html ->
            AbLinkRouting(askFirst = CommonUtils.isDiscrete, onOpenExternal = { CommonUtils.openLinkNow(it) }) {
                AbMessageDialog(
                    title = null,
                    html = html,
                    confirmText = activity.getString(android.R.string.ok),
                    onConfirm = { speakHelp.value = null },
                    onDismissRequest = { speakHelp.value = null },
                )
            }
        }
    }

    /**
     * Platform-dialog removal Task 18 — the reading view's own dialogs ([ReadingDialog]), an EIGHTH
     * sibling overlay. Unlike the seven above, both arms are plain `AlertDialog`-shaped (via
     * `AbMessageDialog`/`AbConfirmDialog`), which stacks fine over any sheet that happens to be open
     * (`ReadingOverlayExclusion`'s kdoc) — so this needs no exclusion wiring, unlike the sheets below.
     */
    @Composable
    private fun ReadingDialogSlot() {
        when (val dialog = readingDialog.value) {
            null -> Unit
            is ReadingDialog.Help -> AbLinkRouting(askFirst = CommonUtils.isDiscrete, onOpenExternal = { CommonUtils.openLinkNow(it) }) {
                AbMessageDialog(
                    title = dialog.title,
                    html = dialog.html,
                    confirmText = activity.getString(R.string.okay),
                    onConfirm = ::dismissReadingDialog,
                    onDismissRequest = ::dismissReadingDialog,
                )
            }
            is ReadingDialog.ConfirmDeleteDocumentPage -> AbConfirmDialog(
                title = null,
                message = activity.getString(R.string.ai_document_delete_confirmation),
                confirmText = activity.getString(R.string.yes),
                dismissText = activity.getString(R.string.no),
                onConfirm = ::confirmReadingDialog,
                onDismiss = ::dismissReadingDialog,
            )
        }
    }

    /**
     * Round 15b's quick sheets — a SEVENTH sibling overlay, for the same reason as the six above: a
     * `ModalBottomSheet` renders in its own window regardless of where it is composed, so opening
     * one can never re-key the pane subtree and destroy the panes' BibleView WebViews.
     *
     * All four quick sheets mount HERE and nowhere else; `QuickSheetMountGuardTest` enforces it.
     * The common `ReadingHost` decides WHEN one is open (from `hostState.quickSheet`) and calls this
     * with the open [sheet].
     */
    @Composable
    private fun QuickSheetContent(sheet: ReadingQuickSheet) {
        when (sheet) {
            ReadingQuickSheet.History -> {
                // Built per opening, exactly as HistoryComposeActivity.kt:46-64 built it before
                // the Z-late epilogue deleted that file: the
                // controller reads the history once at construction, so a stale instance would show
                // a stale list.
                val controller = remember(sheet) {
                    HistoryController(
                        loadEntries = { historyEntriesForActiveWindow() },
                        onRevert = { id -> revertToHistoryItem(id); closeQuickSheet() },
                    )
                }
                val entries by controller.entries.collectAsState()
                val error by controller.error.collectAsState()
                val listState = rememberLazyListState()
                AbQuickSheet(
                    open = true,
                    // Exactly the arguments HistoryComposeActivity.kt:68 passed before the
                    // Z-late epilogue deleted that file — the format string is
                    // "History (%1$s: Window %2$d)" and the goldens depend on both.
                    title = activity.hostContext.getString(
                        R.string.history_for,
                        activity.hostWindowRepository.name,
                        windowControl.activeWindowPosition + 1,
                    ),
                    onDismiss = { closeQuickSheet() },
                    canScrollForward = { listState.canScrollForward },
                ) {
                    HistoryListContent(entries = entries, onSelect = { controller.onSelect(it) }, listState = listState)
                }
                if (error != null) {
                    AbErrorDialog(
                        message = activity.getString(R.string.error_occurred),
                        confirmText = activity.getString(R.string.okay),
                        onDismiss = { controller.dismissError() },
                    )
                }
            }
            ReadingQuickSheet.Workspaces -> {
                val controller = remember(sheet) {
                    WorkspaceQuickController(workspaceService, activity.lifecycleScope) { id ->
                        closeQuickSheet()
                        // C1: the quick sheet never pauses the activity, so nothing else flushes the
                        // outgoing workspace's windows/page-managers/history before switching. Use
                        // the save-then-switch entry point, never plain switchToWorkspace here.
                        activity.lifecycleScope.launch { activity.readingCommands.quickSwitchToWorkspace(id) }
                    }
                }
                val rows by controller.rows.collectAsState()
                val listState = rememberLazyListState()
                AbQuickSheet(
                    open = true,
                    title = activity.getString(R.string.switch_to_workspace),
                    onDismiss = { closeQuickSheet() },
                    canScrollForward = { listState.canScrollForward },
                    footer = {
                        AbQuickSheetFooterRow(text = LocalStrings.current.manageWorkspaces) {
                            closeQuickSheet()
                            activity.hostActivity.startActivityForResult(
                                NavHostComposeActivity.intentFor(activity.hostContext, NavRoutes.WORKSPACE_SELECTOR),
                                WORKSPACE_CHANGED,
                            )
                        }
                    },
                ) {
                    WorkspaceQuickContent(rows = rows, onSelect = { controller.select(it) }, listState = listState)
                }
            }
            is ReadingQuickSheet.Documents -> {
                val scope = sheet.scope
                // Built OFF the composition pass, unlike History and Workspaces above: enumerating
                // every installed book, asking `downloadControl` for each one's status and sorting
                // the result is a hundreds-of-rows job, and doing it synchronously in composition
                // would stall the first frame of the sheet (Plan A's whole-branch review flagged
                // exactly this for this task).
                val tabsState by produceState<DocumentQuickTabs?>(initialValue = null, sheet) {
                    // Clear first: a direct scope change (BIBLE -> COMMENTARY) re-runs this producer
                    // and must not show the previous scope's tabs under the new title meanwhile.
                    value = null
                    value = withContext(Dispatchers.Default) { buildDocumentQuickTabsForHost(scope) }
                }
                // `null` means STILL LOADING, and is deliberately distinguished from "loaded, and
                // there is nothing to offer" below: routing away on a not-yet-loaded list would send
                // the user to the full screen every time the sheet opened faster than the books.
                val tabs = tabsState
                // D1 (whole-branch review fix wave): rememberSaveable, not remember, so the pick
                // survives this composable being torn down and rebuilt when the user closes and
                // reopens the Documents sheet within the same session (`remember` would not -- a
                // fresh slot means a fresh `null`). It does NOT survive a device rotation, contrary
                // to what an earlier version of this comment claimed -- MainBibleActivity's manifest
                // omits `orientation` from `configChanges`, so rotation recreates the Activity and
                // this whole host, `quickSheet` (a plain field, never itself saved) resets to null,
                // and every quick sheet -- this one included -- simply closes. What restores the
                // tab after THAT is the persisted `document_quick_tab` setting
                // (`persistQuickDocTab`/`restoreQuickDocTab` below), not this state holder.
                // Null until the user taps a tab; the persisted key supplies the initial selection
                // once `visible` is known (it cannot be known at remember time — the load is async).
                var pickedTabId by rememberSaveable(sheet) { mutableStateOf<String?>(null) }
                val listState = rememberLazyListState()
                // Memoised on `tabs`, not recomputed inline: `restoreQuickDocTab` is a synchronous
                // settings (Room) read, and computing it in the composition body would run it on the
                // main thread on EVERY recomposition of this branch until the user taps a tab —
                // the same objection that moved the tab build itself off the composition pass.
                val restoredTabId = remember(scope, tabs) { tabs?.let { restoreQuickDocTab(scope, it.visible) } }
                if (tabs != null && tabs.visible.isEmpty()) {
                    // Nothing to offer — go straight to the full screen rather than showing an
                    // empty sheet with only a footer row.
                    LaunchedEffect(Unit) { closeQuickSheet(); openChooseDocument(scope.chooserType) }
                } else {
                    val selectedTabId = tabs?.let { loaded ->
                        pickedTabId?.takeIf { id -> loaded.visible.any { it.name == id } } ?: restoredTabId
                    }
                    AbQuickSheet(
                        open = true,
                        title = activity.getString(
                            when (scope) {
                                DocumentSheetScope.ALL -> R.string.chooseBook
                                DocumentSheetScope.BIBLE -> R.string.doc_type_bible
                                DocumentSheetScope.COMMENTARY -> R.string.doc_type_commentary
                            },
                        ),
                        onDismiss = { closeQuickSheet() },
                        // Identity-keyed tabs: the enum's own `name` is the stable id, which is what
                        // lets a hidden tab and an async tab list coexist with a persisted selection.
                        tabs = tabs?.visible.orEmpty().map { AbQuickSheetTab(it.name, quickDocTabLabel(it)) },
                        selectedTabId = selectedTabId,
                        onTabSelected = { id -> pickedTabId = id; persistQuickDocTab(scope, id) },
                        canScrollForward = { listState.canScrollForward },
                        footer = {
                            AbQuickSheetFooterRow(text = LocalStrings.current.allDocuments) {
                                closeQuickSheet()
                                openChooseDocument(scope.chooserType)
                            }
                        },
                    ) {
                        // While the load is in flight the shell renders header + footer with an
                        // empty body; the rows appear (and the sheet grows) when it completes.
                        if (tabs != null && selectedTabId != null) {
                            DocumentQuickContent(
                                rows = tabs.rowsByTab.getValue(DocumentQuickTab.valueOf(selectedTabId)),
                                currentDocId = currentDocumentInitials(),
                                onSelect = { docId ->
                                    closeQuickSheet()
                                    // The SAME body the `ChooseDocument` activity-result arm runs —
                                    // the sheet returns no Intent, so it cannot use that arm itself.
                                    activity.readingCommands.applyChosenDocument(docId)
                                },
                                listState = listState,
                            )
                        }
                    }
                }
            }
            is ReadingQuickSheet.KeyChooser -> when (sheet.kind) {
                KeyChooserKind.Grid -> {
                    val gridScope = rememberCoroutineScope()
                    val controller = remember(sheet) {
                        newGridPassageController(
                            scope = gridScope,
                            // The full-screen twin's own default title (GridChoosePassageComposeActivity
                            // reads the same string when no "title" intent extra overrides it) --
                            // NOT R.string.chooseBook, which is the DOCUMENT chooser's caption.
                            baseTitle = activity.getString(R.string.choosePassageBookName),
                            // The Bible/commentary key choosers that route here both pass
                            // isScripture=true (CurrentBiblePage.kt:55, CurrentCommentaryPage.kt:66).
                            isScripture = true,
                            // Decided by the OPENING, not read here: the title tap follows the
                            // user's preference while the JS reference chooser forces verse level
                            // (spec §6.1.1). See `showKeyChooserSheet` and
                            // `openVerseChooserSheetForResult`.
                            navigateToVerse = sheet.navigateToVerse,
                            // Answers a pending JS request if there is one, and otherwise runs
                            // Task 7's ONE parse-and-apply path, shared with the onActivityResult
                            // arm. Never re-parse the verse here.
                            onFinish = ::onGridPassageChosen,
                        )
                    }
                    val ui by controller.ui.collectAsState()
                    val rendered = ui
                    val step by controller.step.collectAsState()
                    val options by controller.options.collectAsState()
                    // I2 (whole-branch review fix wave): the grid is the one sheet that ALWAYS
                    // overflows its 400dp bound (its own golden's arithmetic puts the content at
                    // ~492dp), so it is the one sheet that most needs the bottom fade -- but with no
                    // `canScrollForward` passed here, `AbQuickSheet` defaulted to `{ false }` and the
                    // fade never rendered. The grid's own `LazyGridState` (not `listState`, which this
                    // branch never populates) is what the fade must read.
                    val gridState = rememberLazyGridState()
                    AbQuickSheet(
                        open = true,
                        // Tracks the step: Genesis -> Genesis 1. The Speak sheet discards ui.title;
                        // that is right for "pick a range endpoint" and wrong for a general chooser.
                        title = ui?.title ?: activity.getString(R.string.choosePassageBookName),
                        onDismiss = { closeQuickSheet() },
                        // Still the header's back arrow: it pops a grid step until the stack is empty.
                        canGoBack = { step != GridStep.BOOK },
                        onBack = { if (!controller.back()) closeQuickSheet() },
                        canScrollForward = { gridState.canScrollForward },
                        // Amendment D3: swipe-down and scrim-tap must CLOSE this sheet, as they do
                        // every other sheet in the app -- with the shell's default routing they
                        // would pop a grid step instead and leave the X as the only way out from a
                        // deep step. System back is separated out by the BackHandler below, which
                        // is why this can only be done from :app. DEVICE PASS: "system back inside
                        // the grid pops a step" / "swipe-down and scrim-tap close the grid sheet
                        // from a deep step" -- if the BackHandler loses the race to M3's own
                        // handler, deleting this ONE argument restores the shell's default
                        // (back/swipe/scrim all close; the header's back arrow still pops steps).
                        dismissRoutesToBack = false,
                        actions = if (ui?.step == GridStep.BOOK) {
                            {
                                // The six options are not decoration: GridOption.DEUTEROCANONICAL
                                // rebuilds the book list, so dropping them here would be a
                                // functional regression. AbOverflowMenu is a DropdownMenu, i.e. a
                                // Popup, which stacks over a sheet without difficulty -- the port's
                                // ban is on sheet-over-sheet, not popup-over-sheet.
                                GridOptionsOverflow(requireNotNull(ui), options) { controller.toggle(it) }
                            }
                        } else null,
                    ) {
                        // System back pops a grid step; swipe-down and scrim-tap close the sheet
                        // outright, as they do on every other sheet in the app. M3 cannot tell the
                        // three apart, so the only way to separate them is to intercept back BEFORE
                        // M3 sees it -- which needs a BackHandler, which is why this lives in :app
                        // and why `dismissRoutesToBack = false` is passed above. This wins over M3's
                        // own handler because both land on the ModalBottomSheet DIALOG's dispatcher
                        // (ComponentDialog is the OnBackPressedDispatcherOwner / NavigationEvent-
                        // DispatcherOwner of its own view tree) and androidx's processor stores
                        // handlers with addFirst + resolves the FIRST enabled one, i.e. the most
                        // recently added -- and the sheet's content composes after the dialog's own
                        // constructor ran. Verified against this project's material3 /
                        // androidx.activity / androidx.navigationevent bytecode (Task 8 report).
                        BackHandler(enabled = step != GridStep.BOOK) { controller.back() }
                        GridChoosePassageContent(rendered, { id -> rendered?.let { controller.pick(id, it) } }, Modifier.fillMaxSize(), state = gridState)
                    }
                }
                // Both are flat single-level lists with no page stack, so they take the shell's
                // DEFAULT dismiss contract -- swipe, scrim and back all simply close. The grid
                // branch above is the only sheet in this file that needs `dismissRoutesToBack`.
                KeyChooserKind.Map -> KeyChooserSheet(
                    sheet = sheet,
                    kind = KeyChooserKind.Map,
                    title = activity.getString(R.string.doc_type_map),
                )
                KeyChooserKind.GeneralBook -> KeyChooserSheet(
                    sheet = sheet,
                    kind = KeyChooserKind.GeneralBook,
                    title = activity.getString(R.string.general_book),
                )
            }
            is ReadingQuickSheet.Share -> ShareVersesSheet(
                open = true,
                title = LocalStrings.current.shareSheetTitle,
                options = shareVersesOptions,
                onOptionsChange = ::updateShareVersesOptions,
                input = sheet.input,
                onShare = ::shareVersesTextAndCloseSheet,
                // The clip's invisible description only — the classic ShareWidget used the
                // selection's (ambient-locale) verse-range name for it; `referenceFull` is the
                // same JSword call this input already resolved for the "abbreviate reference"
                // toggle, so no extra JSword lookup is needed here.
                onCopy = { text -> copyVersesTextAndCloseSheet(text, sheet.input.referenceFull) },
                onDismiss = ::closeQuickSheet,
            )
            is ReadingQuickSheet.ReadHistory -> {
                // Loaded once per opening, exactly as classic `ReadHistoryDialog.loadAndShow` loaded
                // before calling `show` — the dialog never appeared while the query ran, so neither
                // does this sheet: `rows == null` renders nothing rather than an empty shell.
                var rows by remember(sheet) { mutableStateOf<List<ReadHistoryRow>?>(null) }
                var cycle by remember(sheet) { mutableIntStateOf(0) }
                LaunchedEffect(sheet) {
                    val activeCycle = readingProgressService.currentCycle()
                    val entries = readingProgressService.readHistoryForChapter(sheet.bookId, sheet.chapter, activeCycle)
                    val versionUnknownText = activity.getString(R.string.reading_progress_history_version_unknown)
                    cycle = activeCycle
                    rows = entries.map { entry ->
                        chapterReadHistoryRow(
                            entry = entry,
                            date = readingProgressService.formatEntryDate(entry.readAt),
                            time = readingProgressService.formatEntryTime(entry.readAt),
                            versionUnknownText = versionUnknownText,
                        )
                    }
                }
                val loadedRows = rows
                if (loadedRows != null) {
                    AbReadHistorySheet(
                        title = activity.hostContext.getString(
                            R.string.reading_progress_history_for,
                            "${readingProgressService.bookShortName(sheet.bookId)} ${sheet.chapter}",
                        ),
                        rows = loadedRows,
                        onApplyDeletes = readHistoryApplyDeletes(activity.lifecycleScope, readingProgressService, cycle),
                        onDismiss = ::closeQuickSheet,
                    )
                }
            }
        }
    }

    /**
     * The map / general-book key chooser as a quick sheet — one host composable for both, since the
     * two differ only in their title and in which page they read (spec §4.6).
     *
     * The key list is resolved ONCE per opening — by `MainBibleActivity.composeStartKeyChooser`,
     * which needed it to decide whether to open a sheet at all — and held for the life of the sheet,
     * because a [KeyRow]'s id is the INDEX into it (`KeyChooserKeys.kt`): re-resolving between
     * building the rows and handling the selection would silently re-number them, and for an EPUB it
     * would also rebuild every `Key` on the UI thread ([showKeyChooserSheet]). `remember(sheet)` is
     * the same per-opening keying the History branch uses, and for the same reason.
     *
     * The body's own [LazyListState] is passed to the shell's `canScrollForward`, which is what the
     * bottom fade is drawn from — a state the body did not scroll would never report anything.
     */
    @Composable
    private fun KeyChooserSheet(sheet: ReadingQuickSheet, kind: KeyChooserKind, title: String) {
        // The list [showKeyChooserSheet] was handed, NOT a fresh resolution -- see its kdoc.
        val keys = remember(sheet) { keyChooserKeys }
        val rows = remember(keys) { keys.mapIndexed { i, k -> KeyRow(i.toString(), k.nameWithoutDocument) } }
        val currentKeyId = remember(keys) { currentKeyChooserRowId(kind, keys) }
        val listState = rememberLazyListState()
        AbQuickSheet(
            open = true,
            title = title,
            onDismiss = { closeQuickSheet() },
            canScrollForward = { listState.canScrollForward },
        ) {
            KeyListBody(
                rows = rows,
                currentKeyId = currentKeyId,
                onSelect = { keyId ->
                    val key = keys.getOrNull(keyId.toIntOrNull() ?: -1)
                    closeQuickSheet()
                    if (key != null) applyKeyChooserKey(kind, key)
                },
                listState = listState,
            )
        }
    }

    /**
     * The passage grid as a sheet page, picking ONE endpoint of the repeat range. A fresh controller
     * per endpoint (spec §6.5 point 4): entering the page always starts at BOOK, so the step
     * back-stack needs no reset logic of its own.
     */
    @Composable
    private fun SpeakVersePickerPage(pickingEnd: Boolean) {
        val strings = LocalStrings.current
        val title = if (pickingEnd) strings.speakEndingOfPassage else strings.speakBeginningOfPassage
        val gridScope = rememberCoroutineScope()
        val controller = remember(pickingEnd) {
            newGridPassageController(
                scope = gridScope,
                baseTitle = title,
                isScripture = true,
                navigateToVerse = true,
                onFinish = { osisId ->
                    val verse = VerseFactory.fromString(navigationControl.versification, osisId)!!
                    speakRangeEditor.set(pickingEnd, PickedVerse(verse.getOsisID(), verse.getName(), verse.ordinal))
                    speakSheet.pop()
                },
            )
        }
        DisposableEffect(controller) {
            speakGridBack = controller::back
            onDispose { speakGridBack = null }
        }
        val ui by controller.ui.collectAsState()
        // The sheet shell bounds the body to 400dp (Task 11), so `fillMaxSize()` here is
        // bounded and safe — the grid sizes its cells against that height.
        val rendered = ui
        GridChoosePassageContent(rendered, { id -> rendered?.let { controller.pick(id, it) } }, Modifier.fillMaxSize())
    }

    /**
     * The seven lambdas `GridChoosePassageComposeActivity` builds, assembled for a sheet page. Every
     * one of them calls into `GridPassageHostSupport.kt` — that activity's own extracted helpers, so
     * there is one implementation behind both hosts rather than a copy here.
     */
    private fun newGridPassageController(
        scope: CoroutineScope,
        baseTitle: String,
        isScripture: Boolean,
        navigateToVerse: Boolean,
        onFinish: (String) -> Unit,
    ): GridChoosePassageController {
        var selectedBookNo = 0
        var selectedChapter = 1
        val v11n = navigationControl.versification
        val workspaceName = activity.hostWindowRepository.name
        return GridChoosePassageController(
            scope = scope,
            initialOptions = initialGridOptions(navigationControl, isScripture),
            buildStep = { step, opts ->
                buildGridStep(step, opts, baseTitle, workspaceName, selectedBookNo, selectedChapter,
                    navigationControl, windowControl)
            },
            onPersistOptions = { persistGridOptions(it, navigationControl) },
            onPickBook = { bookNo ->
                selectedBookNo = bookNo
                pickGridBook(bookNo, navigateToVerse, navigationControl) { selectedChapter = it }
            },
            onPickChapter = { chapter ->
                selectedChapter = chapter
                pickGridChapter(chapter, selectedBookNo, navigateToVerse, navigationControl, windowControl)
            },
            onPickVerse = { verse ->
                Verse(v11n, BibleBook.values()[selectedBookNo], selectedChapter, verse).getOsisID()
            },
            onFinish = onFinish,
        )
    }

    /**
     * Whether the modal search-settings sheet (the toolbar's ⚙/Tune affordance) is open.
     *
     * `internal`, not `private` — same test-visibility convention as [searchSection] below: `:app`
     * has no `ComposeTestRule`, so proving [onSearchModeClosed] clears this (review item 4) means a
     * test setting it directly.
     */
    internal val searchSettingsOpen = mutableStateOf(false)

    /**
     * Whether the toolbar field's recent-terms dropdown is open (state-IN, see [ReadingSearchBarState]).
     * `internal` for the same reason as [searchSettingsOpen] just above.
     */
    internal val searchRecentMenuOpen = MutableStateFlow(false)

    /**
     * Whether the toolbar's search field currently holds focus. Read by `MainBibleActivity` to decide
     * whether the IME padding applies — see spec §4: keying that on search mode being ACTIVE would
     * suppress the padding for a WebView note editor opened while search mode is still on.
     */
    val searchFieldFocused = MutableStateFlow(false)

    /**
     * Which result rows are expanded, and the result list's scroll position — both owned by the
     * HOST rather than remembered inside the sheet's composition, so they survive the sheet being
     * closed and reopened (F25's scroll restore, which the Activity flow did with an Intent extra).
     *
     * The scroll position is held in a `mutableStateOf` **holder** rather than as a single fixed
     * [LazyListState] so that [runSearch] can replace it: surviving a close/reopen is right, but
     * inheriting the previous search's offset into a NEW query's results is not — `SearchResultsScreen`
     * (`:103`) got that for free by creating the state per screen. A holder, not a plain `var`,
     * because the sheet reads it inside composition and a non-snapshot read would never be recomposed
     * for (the "modifier runs before content" class of bug this port has already paid for once).
     */
    private val searchResultsExpanded = mutableStateMapOf<String, Boolean>()
    private val searchResultsListState = mutableStateOf(LazyListState())

    /** Test-only read of the current results-list state — same convention as [searchSelectorPendingIdsForTest]. */
    internal val searchResultsListStateForTest: LazyListState get() = searchResultsListState.value

    /**
     * The SWORD results themselves, constructed exactly as `SearchResultsComposeActivity.kt:72`
     * does (the Koin [BibleSearchService], the activity's `lifecycleScope`, the Koin
     * [SearchResultsCache] single — so the F26 same-request cache is shared with that Activity).
     * [ReadingSearchController] deliberately does not own results; it owns the session (spec §5).
     */
    val searchResults = SearchResultsController(bibleSearchService, activity.lifecycleScope, searchResultsCache)

    /**
     * The EPUB results, the counterpart of [searchResults]. Two deliberate asymmetries with the
     * SWORD side, both inherited from `EpubSearchResultsController` as it stands: there is no
     * result cache (F26 is SWORD-only) and no candidate/translation state, because an EPUB search
     * targets exactly one document.
     */
    val epubSearchResults = EpubSearchResultsController(
        scope = activity.lifecycleScope,
        service = epubSearchService,
        onSelect = ::onEpubSearchResultSelected,
    )

    /**
     * The EPUB word-mode, seeded from the SAME settings key the standalone EPUB search Activities
     * read and write, via the shared wire format [toClassicSearchTypeName]/[epubSearchModeFromClassicName]
     * (`EpubSearchModeWire.kt`), so all three surfaces stay interoperable until those Activities are
     * deleted.
     *
     * The backing [MutableStateFlow] is `private`: every caller must go through
     * [persistEpubSearchMode] so a mode set here always reaches the settings key too, never just the
     * in-memory flow.
     *
     * Seeded here and RE-READ on every [openSearch] — the store is shared, so this seed is only ever
     * a starting value, never the truth (review M3).
     */
    private val _epubSearchMode = MutableStateFlow(loadEpubSearchMode())
    val epubSearchMode: StateFlow<EpubSearchMode> = _epubSearchMode.asStateFlow()

    /**
     * Index-build progress rows, feeding [SearchIndexPanel]'s progress half. Reused verbatim from
     * the Activity path; only `onHide` differs — there is no Activity to `finish()`, so it closes
     * the sheet.
     */
    val searchIndexProgress = SearchIndexProgressController(onHide = { searchController.closeSheet() })

    /**
     * Task 10's snackbar payload: non-null while a "cannot be searched" message is waiting to be
     * shown, holding the JSword display name (`Book.name`, NOT [SearchDocumentInfo] — the portable
     * model carries no name) of the document `onUnavailable` fired for. [DriveSearchUnavailableSnackbar]
     * turns it into a real `Snackbar` via the reading view's own [BottomSheetScaffold] `snackbarHost`
     * slot (there is no second scaffold for this) and calls [searchUnavailableMessageShown] to clear
     * it. Exposed (not test-only) for the same reason [openSearchStrongs]'s EPUB guard is: `:app` has
     * no `ComposeTestRule`, so a test asserting this state is the closest it gets to the real UI event.
     */
    private val _searchUnavailableDocName = MutableStateFlow<String?>(null)
    val searchUnavailableDocName: StateFlow<String?> = _searchUnavailableDocName.asStateFlow()

    /** Clears [searchUnavailableDocName] once [DriveSearchUnavailableSnackbar] has started showing it. */
    fun searchUnavailableMessageShown() {
        _searchUnavailableDocName.value = null
    }

    /**
     * The search session state machine (Task 3). Every effect it needs is injected here, which is
     * what keeps the machine itself Android-free and host-testable:
     * - `resolveDoc` reads the ACTIVE window's document each time, so search always applies to what
     *   is being read now (`searchDocumentInfo` is the JSword→portable mapping, Step 1).
     * - `onLeaveFullScreen` uses the [MainBibleActivity.fullScreen] setter because `toggleFullScreen`
     *   is private; assigning `false` is idempotent there.
     * - `onRunSearch` hands the RAW query plus the real word-mode/section to the service, which
     *   decorates once internally — the Activity pair had to pass an already-decorated string with
     *   identity decorators to avoid decorating twice (`SearchResultsComposeActivity.kt:97-101`).
     * - `onUnavailable` is Task 10's snackbar: `open()` returns without entering search mode or
     *   showing the sheet (there is nothing to search), and this only records the document name for
     *   [DriveSearchUnavailableSnackbar] to show.
     */
    val searchController = ReadingSearchController(
        resolveDoc = { searchDocumentInfo(documentControl.currentDocument) },
        onUnavailable = { _searchUnavailableDocName.value = documentControl.currentDocument?.name.orEmpty() },
        onLeaveFullScreen = { activity.fullScreen = false },
        onStartIndexing = { docId -> startSearchIndexing(docId) },
        // F43 Task 6: every document type now reaches here (see [openSearch]) — an EPUB runs
        // `runEpubSearch`, everything else the SWORD `runSearch`.
        onRunSearch = { docId, query, forEpub -> if (forEpub) runEpubSearch(docId, query) else runSearch(docId, query) },
        // F100 (fix batch 3 §2.1.3): an unindexed translation in the persisted selection prompts, and
        // arms the Task 11 chain ([searchSelectorPendingIds]) for the whole selection, so every
        // unindexed one is built before the search runs — exactly as a results-selector choice does.
        firstUnindexedInSelection = {
            val selection = searchTranslations.value
            bibleSearchService.unindexedAmong(selection).firstOrNull()?.also { searchSelectorPendingIds = selection }
        },
        // Review M1: an F100 prompt that was abandoned (BACK, a pane switch, a new search) must not leave
        // its chain armed for an unrelated later build.
        onSelectionPromptDropped = { searchSelectorPendingIds = null },
        // Typing a reference ("1 joh 3 16") jumps there in the active window, as the classic search
        // screen and the full-screen route do. In-window navigation: no history pop.
        tryOpenReference = { query -> linkControl.tryToOpenRef(query) },
        onReferenceOpened = { leaveSearch() },
        queries = searchQueries,
    )

    /**
     * Phase-derived inputs for [searchBar], pre-combined so [searchBarCore] stays inside `combine`'s
     * five-argument typed overload (it is already full).
     */
    private data class SearchBarPhaseInfo(val resultsAvailable: Boolean, val forEpub: Boolean)

    private val searchBarPhaseInfo: StateFlow<SearchBarPhaseInfo> = searchController.phase
        .map { p ->
            SearchBarPhaseInfo(
                // Deliberately not `&& !sheetVisible`: while the sheet is open the button merely
                // re-raises an already-raised sheet, whereas gating on visibility would make the
                // leading icon change identity every time the sheet is dragged.
                resultsAvailable = p is ReadingSearchPhase.Results,
                forEpub = forEpubOf(p) == true,
            )
        }
        .stateIn(hostScope, SharingStarted.Eagerly, SearchBarPhaseInfo(false, false))

    /** The query/history/IME half of [searchBar] — everything that is not derived from the phase. */
    private val searchBarCore: StateFlow<ReadingSearchBarState?> = combine(
        searchController.searchModeActive,
        searchQueries.query,
        searchQueries.recentTerms,
        searchRecentMenuOpen,
        searchController.imeRequest,
    ) { active, query, recentTerms, recentMenuOpen, imeRequest ->
        if (!active) null
        else ReadingSearchBarState(
            query = query,
            recentTerms = recentTerms,
            recentMenuOpen = recentMenuOpen,
            imeRequest = imeRequest,
        )
    }.stateIn(hostScope, SharingStarted.Eagerly, null)

    /**
     * What the toolbar renders in search mode, or `null` when search mode is off (which is what
     * makes `ReadingToolbar` draw its normal row). Assembled here rather than in `mountComposeView`
     * because most of its inputs are `StateFlow`s owned by [searchController]/[searchQueries] and one
     * is host state; `mountComposeView` just collects the result, the same shape as its
     * `toolbar: StateFlow<ToolbarState>` parameter.
     */
    val searchBar: StateFlow<ReadingSearchBarState?> =
        combine(searchBarCore, searchBarPhaseInfo) { core, info ->
            core?.copy(resultsAvailable = info.resultsAvailable, forEpub = info.forEpub)
        }.stateIn(hostScope, SharingStarted.Eagerly, null)

    /** The JSword index-build feed (Step 5) — see [startSearchIndexing]. */
    private var searchIndexWorkListener: WorkListener? = null
    private val searchIndexFinishedJobs = HashSet<Progress>()
    private var searchIndexPoll: IndexPollDecision? = null
    private var searchIndexDocument: Book? = null

    /**
     * Opens search for the active window's document — the target of every retargeted entry point
     * (`MainBibleActivity.composeSearch()` here in Task 8a; the remaining five in Task 8b).
     * [seedQuery] is for the entry points that bypass the form (text-selection "Search …", Strong's
     * find-all) and runs immediately.
     *
     * [preDecorated] marks [seedQuery] as ALREADY run through [SearchControl.decorateSearchString]
     * (the text-selection "Search…" entry point) rather than a raw user query. Strong's find-all
     * does NOT set this — it seeds the RAW `strong:$ref` and goes through [openSearchStrongs]
     * instead, since it needs its own document-selection restore, not just a decoration override —
     * see [searchPreDecoratedQuery] and [searchStrongsQuery]'s kdoc for why each needs its own flag.
     */
    fun openSearch(seedQuery: String? = null, preDecorated: Boolean = false) {
        refreshSearchTranslations()
        // Review item 7: the MRU store is shared with the classic/EPUB search Activities and this
        // host outlives any single search, so it is re-read here for the same reason the translations
        // are — a search performed there meanwhile would otherwise be clobbered by this host's stale
        // in-memory list on its next `recordRecentTerm`.
        searchQueries.reloadRecentTerms()
        // Review M3: and for the SAME reason, the EPUB word-mode. It is stored under a key the two
        // standalone EPUB search Activities also write ([EPUB_SEARCH_TYPE_KEY]), and this host
        // outlives any single search — so a mode changed on one of those surfaces meanwhile would
        // otherwise be invisible here until the process restarted, and worse, be overwritten by this
        // host's stale value on the next `persistEpubSearchMode`. Read once at construction is only
        // correct for state nothing else owns; this is not that.
        _epubSearchMode.value = loadEpubSearchMode()
        // Review item 5: BOTH one-shot flags are keyed to the query TEXT (see their kdoc), which a
        // seedless open does not change — so only a call that brings a new seed may replace them. An
        // unconditional reset here dropped them for a run they should have covered: Strong's find-all
        // -> results -> a seedless re-entry (Ctrl+F / the SEARCH key / the drawer) -> closing the
        // settings sheet re-ran the very same `strong:H430` text as an ordinary word-mode search.
        if (seedQuery != null) {
            // Review Critical 1: stored TRIMMED — `ReadingSearchController`/`buildSearchRequest` both
            // compare against `queries.query.value.trim()`, and entry point 7's seed always carries a
            // leading space (`SearchControl.decorateSearchString`'s `ALL`-section term joins with a
            // literal `" "`), so an untrimmed store here never matched and the override silently never
            // fired.
            searchPreDecoratedQuery = seedQuery.trim().takeIf { preDecorated }
            searchStrongsQuery = null
        }
        searchController.open(seedQuery)
    }

    /**
     * Strong's find-all (F6 Task 8b entry point 8, [net.bible.android.control.link.LinkControl.showAllOccurrences]).
     * [ref] is the RAW Strong's number/reference, NOT decorated here: [buildSearchRequest] forces
     * [SearchType.ANY_WORDS] for it below (classic's own comment — "the below uses ANY_WORDS because
     * that does not add anything to the search string" — `LinkControl.kt:372`), so decorating the raw
     * `strong:$ref` exactly once, host-side, reproduces classic's query. There is no double-decoration
     * risk to guard against the way [openSearch]'s [preDecorated] path has to, because nothing has
     * decorated [ref] yet.
     *
     * [translationIds] is the Strong's-Bible selection [LinkControl] already resolved (the remembered
     * `SearchControl.STRONGS_SEARCH_TRANSLATIONS_PREF` choice, filtered to installed Strong's-enabled
     * Bibles, else the auto-detected default) — the host only seeds [searchTranslations] with it, it
     * does not re-derive it. `LinkControl` keeps classic's routing for the not-yet-indexed document
     * case (`Screen.SearchIndex`): prompting to index a document other than the active window's is
     * Task 11's machinery, which does not exist yet, so this is only called once the document is
     * already indexed.
     *
     * Returns whether it actually opened the search. `false` means the caller (`LinkControl` via
     * [MainBibleActivity.composeSearchStrongsIfHosted]) MUST fall back to its own classic route —
     * this declines (does nothing) when the active window's document is an EPUB, because Strong's
     * find-all is a Bible concept: it searches Strong's-enabled BIBLES, not the open document, so an
     * EPUB on screen must not swallow the request silently (F43 Task 6 fix round 1 — this used to
     * fall off the end returning `Unit`, which the caller could not distinguish from success).
     * It declines the same way when the active document cannot be searched at all (a dictionary or
     * general book): the find-all link lives in the links window, and tapping it makes that
     * dictionary window active, so the reading-view search would open nothing (E2 EP-11).
     */
    fun openSearchStrongs(ref: String, translationIds: List<String>): Boolean {
        val current = documentControl.currentDocument
        if (current?.isEpub == true) return false
        if (searchKindFor(searchDocumentInfo(current)) == SearchKind.Unavailable) return false
        refreshSearchTranslations()
        // Per-open refresh, exactly as in [openSearch] — see review item 7 there.
        searchQueries.reloadRecentTerms()
        searchTranslations.value = translationIds
        val query = "strong:$ref"
        // `ref` cannot contain whitespace in practice, but `.trim()` here anyway — the SAME
        // defensive normalisation as [openSearch]'s [searchPreDecoratedQuery], so this flag can
        // never go stale the same way for a reason that only shows up later.
        searchStrongsQuery = query.trim()
        searchPreDecoratedQuery = null
        searchController.open(query)
        return true
    }

    /**
     * The three-stage back for search, in one call: after a result tap the first press brings the
     * results list back (F83, [ReadingSearchController.reopenResultsOnBack]); then a press closes the
     * results/index sheet (keeping the query and the results); the last leaves search mode. Returns
     * whether the press was consumed. Called from the reading destination's back chain
     * (`NavHostComposeActivity.readingBackChain`).
     */
    fun closeSearchIfOpen(): Boolean {
        if (searchController.closeSheet()) return true
        if (searchController.reopenResultsOnBack()) return true
        if (searchController.closeSearchMode()) {
            onSearchModeClosed()
            return true
        }
        return false
    }

    /** Leaves search entirely (the toolbar's close affordance, and the index prompt's Cancel). */
    private fun leaveSearch() {
        searchController.closeSheet()
        if (searchController.closeSearchMode()) onSearchModeClosed()
    }

    /** Common tail of leaving search mode (Task 8b): drops the index feed, both one-shot decoration
     *  flags, (Task 11) the pending selector-index chain and (review item 4) the two per-open UI
     *  flags, so a later [openSearch] never inherits a stale override, a cancelled index-prompt chain
     *  leaves nothing armed, and re-entering search mode does not come up with the recent-terms
     *  dropdown already down or the settings sheet already open. */
    private fun onSearchModeClosed() {
        stopSearchIndexFeed()
        searchPreDecoratedQuery = null
        searchStrongsQuery = null
        searchSelectorPendingIds = null
        searchRecentMenuOpen.value = false
        searchSettingsOpen.value = false
        // D7: the rows belong to the session that just ended. The query is cleared by the controller.
        searchResults.clear()
        epubSearchResults.clear()
        // Every exit from search mode routes through here, which is why the field's focus flag is reset
        // HERE and not at the call sites: `closeSearchIfOpen()` (the live back-button path, and the
        // ordinary way out of an empty form) would otherwise leave it true with no field on screen, and
        // the activity keys its IME padding on it. Belt and braces too — removing the field from
        // composition does fire onFocusChanged(false), but the padding must not depend on that.
        searchFieldFocused.value = false
    }

    /**
     * Reloads the Bibles offered by the settings sheet and re-seeds the selection from the
     * persisted choice, falling back to the document being read — `SearchComposeActivity.kt:81-90`
     * (its `onResume` does the same reload, for the same reason: the choice may have changed
     * elsewhere). Called on every [openSearch] rather than once, since the read document changes.
     */
    private fun refreshSearchTranslations() {
        val bibles = SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().sortedBy { it.abbreviation }
        searchAvailableTranslations.value = bibles.map { it.initials to it.abbreviation }
        val fallback = documentControl.currentDocument?.initials?.let { listOf(it) } ?: emptyList()
        searchTranslations.value = loadSelectedSearchTranslations().ifEmpty { fallback }
    }

    /** Applies (and persists) a translation choice made in the settings sheet. */
    private fun setSearchTranslations(ids: List<String>) {
        searchTranslations.value = ids
        // Same comma-joined key classic `Search.saveSelectedTranslations` writes.
        CommonUtils.settings.setString(SEARCH_TRANSLATIONS_KEY, ids.joinToString(","))
    }

    /** `SearchComposeActivity.loadSelectedTranslations` — only initials that still resolve to a Bible. */
    private fun loadSelectedSearchTranslations(): List<String> {
        val saved = CommonUtils.settings.getString(SEARCH_TRANSLATIONS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        val available = SwordDocumentFacade.bibles.filterIsInstance<SwordBook>().map { it.initials }.toSet()
        return saved.split(",").filter { it in available }
    }

    /** `SearchComposeActivity.loadRecentTerms` — newline-separated, see [searchQueries]. */
    private fun loadRecentSearchTerms(): List<String> {
        val saved = CommonUtils.settings.getString(SEARCH_RECENT_TERMS_KEY, null)
        if (saved.isNullOrBlank()) return emptyList()
        return saved.split("\n").filter { it.isNotBlank() }
    }

    /**
     * Set for the duration of a query seeded already-decorated by the caller (entry point 7, the
     * text-selection "Search…" action, `BibleView.kt`) — [buildSearchRequest] then forces IDENTITY
     * decorators ([SearchType.ANY_WORDS] + [SearchBibleSection.ALL], see `LuceneQueryDecorator
     * .decorateAnyWords` — literally "don't need to do anything") instead of the live settings-sheet
     * word-mode/section, so re-decorating reproduces the caller's already-decorated string exactly
     * rather than decorating it twice (a real bug: e.g. re-running `ALL_WORDS.decorate` over an
     * already phrase-quoted multi-word string inserts a stray `+` inside the quotes). Mirrors
     * `SearchResultsComposeActivity`'s identical "the launcher already decorated the query" handling
     * of the same class of entry point (`:97-101`).
     *
     * Matches `query`, not a plain boolean, for the same self-clearing reason as [searchStrongsQuery]
     * below: editing the query in the toolbar field makes it stop matching with no explicit reset
     * needed. Cleared when search mode closes — see [onSearchModeClosed].
     */
    private var searchPreDecoratedQuery: String? = null

    /**
     * Set for the duration of a Strong's find-all query (entry point 8, [openSearchStrongs]) —
     * [buildSearchRequest] compares `query == searchStrongsQuery` to force [SearchType.ANY_WORDS] and
     * set [SearchRequest.isStrongsSearch], so a re-run from the results document selector or the
     * settings sheet (same query text — see [ReadingSearchController.settingsClosed]) keeps behaving
     * like a Strong's search, while editing the query in the toolbar field drops the flag on its own.
     * Cleared when search mode closes — see [onSearchModeClosed].
     */
    private var searchStrongsQuery: String? = null

    /**
     * F6 Task 11: the FULL set of translation ids chosen in the results document selector, held
     * only while the index-prompt chain [onSearchIndexWorkEvent] drives is running for them — i.e.
     * from [onSearchTranslationsChosen] finding at least one unindexed translation in that set until
     * every one of them is indexed (or a build fails). `null` in every other flow, including the
     * plain "the document being read has no index" case ([ReadingSearchController.open]'s own
     * `NeedsIndex`) — [onSearchIndexWorkEvent] gates the chaining on THIS field rather than on
     * whether the chosen set contains an unindexed translation in general. It is armed by a
     * results-selector choice ([onSearchTranslationsChosen]) and, since fix batch 3 (F100), by an
     * ordinary submit or settings close whose persisted selection holds an unindexed translation
     * ([ReadingSearchController]'s `firstUnindexedInSelection`). Cleared in [onSearchModeClosed] so a cancelled session
     * leaves nothing armed.
     */
    private var searchSelectorPendingIds: List<String>? = null

    /** Test-only read of [searchSelectorPendingIds] — same convention as [paneMenuWindowIdForTest]. */
    internal val searchSelectorPendingIdsForTest: List<String>? get() = searchSelectorPendingIds

    // `internal`, not `private`, purely for direct test coverage of the two one-shot overrides above
    // — mirrors this file's own `paneMenuWindowIdForTest`/`buildTabBarModel` test-visibility
    // convention (see `ReadingSearchEntryPointsTest`); every production call site still goes through
    // [runSearch].
    internal fun buildSearchRequest(docId: String, query: String): SearchRequest {
        // Review Critical 1: `query` arrives already `.trim()`med by
        // `ReadingSearchController.enterFormOrResults`/`submit`/`settingsClosed` (all read
        // `queries.query.value.trim()`), but entry point 7's seed always carries a leading space
        // (`SearchControl.decorateSearchString`'s `ALL`-section term is `""`, joined with a literal
        // `" "`) — so a naive `query == searchPreDecoratedQuery` comparison against the UNTRIMMED
        // seed stored by `openSearch` never matched, and the override silently never fired. Both
        // sides are trimmed here (not just at the storage site) so a future caller that hands
        // `buildSearchRequest` an untrimmed query directly (as a test legitimately might) is not a
        // second way to reintroduce the same miss.
        val trimmedQuery = query.trim()
        val preDecorated = trimmedQuery == searchPreDecoratedQuery
        val strongsSearch = trimmedQuery == searchStrongsQuery
        return SearchRequest(
            query = query,
            // Both one-shot modes force ANY_WORDS: the pre-decorated case because it is the
            // identity decorator (see [searchPreDecoratedQuery]'s kdoc), Strong's because that is
            // exactly why classic chose it (`LinkControl.kt:372`: "does not add anything").
            searchType = if (preDecorated || strongsSearch) SearchType.ANY_WORDS else searchType.value,
            // Both one-shot modes now force the section to ALL: the pre-decorated case because ALL
            // is the identity (empty) section term, Strong's because classic's ONLY caller
            // (`BibleView.kt:1465`) always passes `ALL` — applying the live settings-sheet section
            // instead would silently narrow a find-all to whatever OT/NT restriction the user last
            // left in the settings sheet, with no indication in the results (review Important/A).
            bibleSection = if (preDecorated || strongsSearch) SearchBibleSection.ALL else searchSection.value,
            // The persisted selection, plus the document this search is running for when that
            // document is already indexed (F44/B4). Addressing keys (`Book.initials`), never list
            // indices. NB the flag is read for `docId` — after `promptIndexFor` the phase addresses a
            // translation the active window is not showing, so `documentControl.currentDocument`
            // would describe a different book.
            translationIds = searchTranslationIds(
                persisted = searchTranslations.value,
                activeDocId = docId,
                activeIsIndexed = searchDocumentInfo(SwordDocumentFacade.getDocumentByInitials(docId))?.indexDone == true,
            ),
            currentBookName = searchControl.currentBookName,
            isStrongsSearch = strongsSearch,
        )
    }

    // `internal`, not `private` — same test-visibility rationale as [runEpubSearch] below: review M10
    // asked for the BIBLE direction of the routing to be pinned too (a Bible query must leave the EPUB
    // controller untouched), and that assertion has to call this side directly.
    /** Test-only: the request the most recent [runSearch] built (the results controller keeps it private). */
    internal var lastSearchRequestForTest: SearchRequest? = null
        private set

    internal fun runSearch(docId: String, query: String) {
        // A new query's rows are new rows: keep no stale expansion state keyed by reference name.
        searchResultsExpanded.clear()
        // Review I2: nor a stale scroll offset. Only a genuine (re-)run reaches here — reopening the
        // sheet for the query the results already belong to is served by
        // `ReadingSearchController.enterFormOrResults` without calling back — so the F25 scroll
        // survival is untouched, while three hits after scrolling to row 40 no longer open clamped
        // at the bottom and looking empty.
        searchResultsListState.value = LazyListState()
        val request = buildSearchRequest(docId, query)
        lastSearchRequestForTest = request
        // The searched list and the USER's selection are two different things (F44 fix round, I2):
        // `buildSearchRequest` appends the active document when it is indexed (B4), but the results
        // sheet's document selector must keep showing — and, on confirm, persisting — only what the
        // user chose, or the auto-appended document would become part of the saved selection through
        // `selectTranslations` → `persistSelection`. `searchTranslations` is the same list the
        // settings sheet's picker shows, so the two pickers now agree as well. `ifEmpty` covers the
        // one case where there is no user selection at all (nothing persisted and no current
        // document): the searched list is then the only honest thing to show.
        searchResults.run(request, userSelection = searchTranslations.value.ifEmpty { request.translationIds })
    }

    /**
     * An EPUB query — the EPUB counterpart of [runSearch]. Resets the sheet's scroll for the same
     * reason [runSearch] does: new rows are new rows. Deliberately does NOT clear
     * [searchResultsExpanded] the way [runSearch] does: EPUB result rows carry no expansion state
     * (there is no candidate/translation model to expand — see [epubSearchResults]'s kdoc), so there
     * is nothing there to go stale.
     */
    internal fun runEpubSearch(docId: String, query: String) {
        searchResultsListState.value = LazyListState()
        // Review I1: the text-selection "Search '…'" entry point seeds an ALREADY Lucene-decorated
        // query (`BibleView.kt`'s `decorateSearchString(sel, PHRASE, ALL, "")`), and that decoration
        // is meaningless to FTS5 — with the stored mode PHRASE it would be quoted twice into a
        // syntax error, and with a word mode read as one long phrase. The SWORD path honours the
        // flag in [buildSearchRequest]; this is its EPUB counterpart, matched the same trimmed way
        // and for the same self-clearing reason (editing the field drops the override).
        val params = epubSearchRunFor(
            query = query,
            preDecorated = query.trim() == searchPreDecoratedQuery,
            storedMode = epubSearchMode.value,
        )
        epubSearchResults.run(docId, params.query, params.mode)
    }

    /**
     * An EPUB result row tap. The row's [keyId] is the `BookAndKey.osisRef`
     * (`"<initials>:<fragmentId>"`); strip the initials prefix and re-resolve the inner osisRef —
     * the same round-trip `EpubSearchResultsComposeActivity.onSelect` (`:130-146`) performs. Unlike
     * that Activity there is no `startActivity` and no `finish()`: the reading view is already here,
     * so this only navigates the active window and drops the sheet (see [onSearchResultSelected]).
     *
     * [ordinal] is re-attached to the resolved key via [epubKeyFor] — see its kdoc: classic passes the
     * whole `BookAndKey` (`OrdinalRange` included) to `setCurrentDocumentAndKey`, so the reader lands on
     * the hit rather than the top of the fragment.
     */
    internal fun onEpubSearchResultSelected(keyId: String, ordinal: Int) {
        val docId = (searchController.phase.value as? ReadingSearchPhase.Results)?.docId
        if (docId == null) {
            Log.w(TAG, "onEpubSearchResultSelected: dropped '$keyId' — no Results phase to resolve a docId from")
            return
        }
        val book = SwordDocumentFacade.getDocumentByInitials(docId)
        if (book == null) {
            Log.w(TAG, "onEpubSearchResultSelected: dropped '$keyId' — '$docId' is not an installed document")
            return
        }
        try {
            val key = epubKeyFor(book, docId, keyId, ordinal)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            searchController.onResultOpened()
        } catch (e: Exception) {
            Log.e(TAG, "onEpubSearchResultSelected: bad key '$keyId' in $docId", e)
        }
    }

    /**
     * Persist the word-mode via the shared wire format [toClassicSearchTypeName] — mirroring
     * `EpubSearchComposeActivity.saveMode` exactly, so a mode set here is the mode that Activity
     * shows and vice versa.
     */
    internal fun persistEpubSearchMode(mode: EpubSearchMode) {
        _epubSearchMode.value = mode
        CommonUtils.settings.setString(EPUB_SEARCH_TYPE_KEY, mode.toClassicSearchTypeName())
    }

    /** The read half of [persistEpubSearchMode] — see [epubSearchModeFromClassicName]. */
    private fun loadEpubSearchMode(): EpubSearchMode =
        epubSearchModeFromClassicName(CommonUtils.settings.getString(EPUB_SEARCH_TYPE_KEY))

    /**
     * Starts a JSword index build and begins feeding [searchIndexProgress] from `JobManager` —
     * lifted from `SearchIndexProgressComposeActivity` (`:80-123`), whose whole reason for existing
     * was to own this listener. The listener lives only for the `Indexing` phase: it is added here
     * and removed by [stopSearchIndexFeed] once the build resolves (or search mode closes), so a
     * closed sheet keeps no listener alive.
     */
    private fun startSearchIndexing(docId: String) {
        searchIndexDocument = SwordDocumentFacade.getDocumentByInitials(docId)
        searchIndexPoll = IndexPollDecision()
        searchIndexFinishedJobs.clear()
        // Review item 3: the Activity that used to own this controller was created per build; this
        // host owns one for its lifetime, so a previous build's failure the user walked away from
        // (rather than dismissed) would raise its error dialog over this brand-new prompt, on top of
        // the previous build's job rows.
        searchIndexProgress.reset()
        // Listener FIRST, then the build: the Activity pair registered only once the progress
        // screen had been launched, so the first work events of a fast build could arrive with
        // nobody listening.
        if (searchIndexWorkListener == null) {
            val listener = object : WorkListener {
                override fun workProgressed(ev: WorkEvent) = onSearchIndexWorkEvent(ev)
                override fun workStateChanged(ev: WorkEvent) = onSearchIndexWorkEvent(ev)
            }
            searchIndexWorkListener = listener
            JobManager.addWorkListener(listener)
        }
        searchIndexService.createIndex(docId)
        refreshSearchIndexJobs()
        // Review item 6: there is deliberately no `revealNoTasksIfIdle()` timer here. The Activity
        // path shows a "no tasks running" line after ~4 s; `SearchIndexPanel` has no such line and
        // never reads `SearchIndexProgressController.noTasks`, so the delayed reveal set a flag
        // nothing renders while its comment claimed classic parity. (The flag itself stays — the
        // controller is still shared with `SearchIndexProgressComposeActivity`, which does render it.)
    }

    private fun stopSearchIndexFeed() {
        searchIndexWorkListener?.let { JobManager.removeWorkListener(it) }
        searchIndexWorkListener = null
    }

    /**
     * `SearchIndexProgressComposeActivity.onWorkEvent`/`jobFinished` (`:328-336`, `:362-391`), with
     * two deliberate differences: the blocking `pause(2)` loop is a coroutine `delay` around the
     * pure [IndexPollDecision] (Task 7), and the `startActivity`/`finish` branch is NOT ported —
     * what happens after a successful build is [ReadingSearchController.onIndexingFinished]'s
     * decision (auto-run a waiting query, else back to the form), and Task 3's tests pin it.
     *
     * `WorkListener` fires off the main thread, so everything is hopped onto [hostScope] (Main).
     */
    private fun onSearchIndexWorkEvent(ev: WorkEvent) {
        val job = ev.job
        hostScope.launch {
            refreshSearchIndexJobs()
            // Review I1: `JobManager`'s listener is GLOBAL, so `job` is any JSword job — a module
            // download or install finishing mid-build used to resolve the index build early (poll,
            // maybe `showError()`, drop the feed and fall back to `NeedsIndex`) while the real build
            // was still running, leaving the user on an index prompt whose Create deletes the index
            // that was about to succeed. See [shouldResolveIndexBuild] for why this is gated on "no
            // job is still running" rather than on the build's own `Progress`.
            if (!shouldResolveIndexBuild(job.isFinished, isEverySearchIndexJobFinished())) return@launch
            if (!searchIndexFinishedJobs.add(job)) return@launch
            val poll = searchIndexPoll ?: return@launch
            val indexDone = awaitIndexDone(
                poll = poll,
                // Review I2: through [documentIndexDone], NOT a bare `indexStatus` read. This is the
                // completion gate for the index the prompt just built, so an EPUB reaching it via
                // `indexStatus` would re-introduce exactly the dependency spec D3 removed everywhere
                // else — the flag lies for EPUBs, and here a lie means polling to `GaveUp` and
                // landing the user back on the prompt for an index that in fact exists.
                indexDone = { searchIndexDocument?.let { documentIndexDone(it) } == true },
                pause = { delay(SEARCH_INDEX_POLL_INTERVAL_MS) },
            )
            // Classic only reported the failure once nothing else was still running. Re-read rather
            // than reuse the gate's answer above: the ≤12 s poll may have straddled a new job's start.
            if (!indexDone && isEverySearchIndexJobFinished()) searchIndexProgress.showError()
            stopSearchIndexFeed()
            // F6 Task 11: a selection made in the results document selector can hold more than one
            // unindexed translation (classic indexed only the first and silently dropped the rest).
            // While `searchSelectorPendingIds` is set, chain to the next one instead of letting
            // `onIndexingFinished` re-run against only the just-built translation.
            val next = nextSelectorIndexPrompt(searchSelectorPendingIds, indexDone) { bibleSearchService.unindexedAmong(it) }
            if (next != null) {
                searchController.promptIndexFor(next, keepInterceptedSearch = true)
                return@launch
            }
            searchSelectorPendingIds = null
            searchController.onIndexingFinished(indexDone)
        }
    }

    /** `SearchIndexProgressComposeActivity.refreshJobs` (`:338-353`) verbatim. */
    private fun refreshSearchIndexJobs() {
        val snapshot = ArrayList<ProgressJob>()
        val it = JobManager.iterator()
        while (it.hasNext()) {
            val job = it.next()
            snapshot.add(
                ProgressJob(
                    id = System.identityHashCode(job).toString(),
                    label = job.jobName,
                    percent = job.work,
                    indeterminate = job.work == 0,
                )
            )
        }
        searchIndexProgress.setJobs(snapshot)
    }

    /** `SearchIndexProgressComposeActivity.isAllJobsFinished` (`:355-360`) verbatim. */
    private fun isEverySearchIndexJobFinished(): Boolean {
        val it = JobManager.iterator()
        while (it.hasNext()) if (!it.next().isFinished) return false
        return true
    }

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
        stopSearchIndexFeed()
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
        searchBar = searchBar,
        searchBarCallbacks = ReadingSearchBarCallbacks(
            onQueryChange = { searchQueries.setQuery(it) },
            onSubmit = { searchController.submit() },
            onRecentTermsOpen = { searchRecentMenuOpen.value = true },
            onRecentTermsDismiss = { searchRecentMenuOpen.value = false },
            // Parity with `SearchComposeActivity` (`onRecentTermSelected = controller::setQuery`):
            // picking a recent term fills the field, it does not search by itself.
            onRecentTermSelected = { term ->
                searchRecentMenuOpen.value = false
                searchQueries.setQuery(term)
            },
            onOpenSettings = { searchSettingsOpen.value = true },
            onClose = { leaveSearch() },
            onImeRequestHandled = { searchController.imeRequestHandled() },
            onFieldFocusChanged = { searchFieldFocused.value = it },
            onRebuildIndex = { searchController.requestRebuildIndex() },
            onShowResults = { searchController.showResults() },
        ),
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
            val agentLogUiState by agentLog.state.collectAsState()
            AgentLogPanel(
                agentLogUiState,
                animateStatus = !CommonUtils.settings.disableAnimations,
                statusIcon = painterResource(R.drawable.icon_robot),
                applyNavBarInset = applyNavBarInset,
                panelHeightDp = if (agentLogUiState.expanded) {
                    agentPanelHeight(agentLogUiState, collapsedDp, maxHeightDp)
                } else null,   // collapsed lays out intrinsically, exactly as before round 12b
                onHeightDragStarted = agentLog::onHeightDragStarted,
                onHeightDrag = { dragUpDp ->
                    agentLog.onHeightDrag(dragUpDp, collapsedDp, maxHeightDp)
                },
                onCollapsedHeightMeasured = onCollapsedHeightMeasured,
                onToggleExpanded = agentLog::toggleExpanded,
                onStop = agentLog::stop,
                onClose = agentLog::hide,
                onModelSelectorClick = agentLog::onModelSelectorClick,
                onModelChosen = agentLog::onModelChosen,
                onModelPickerDismiss = agentLog::onModelPickerDismiss,
                onRawLogClick = agentLog::onRawLogClick,
            )
        },
        // Batch 12f Task 6: the Speak transport bar, pre-built here (closing over the live
        // `speakTransport` controller) exactly the same pass-through shape as `agentLogSlot`
        // above.
        speakBarSlot = { applyNavBarInset ->
            val speakState by speakTransport.state.collectAsState()
            // Round 14b §6: the agent panel's visibility, narrowed to a boolean through
            // `derivedStateOf` — the SAME idiom as `agentLogVisible` a few lines below, and
            // for the same reason: collecting `agentLog.state` raw here would resubscribe this
            // slot to every log line, status change and cost update of a running agent, when all
            // it needs is the one flip. Reading `.value` inside this restartable lambda means
            // the bar recomposes only when the panel appears or disappears.
            val agentLogState = agentLog.state.collectAsState()
            val agentLogVisible = remember { derivedStateOf { agentLogState.value.visible } }
            // `fullScreen` is the host's own MutableState (fed by fullScreenChanged), read here
            // so the bar recomposes away when fullscreen is entered — see [speakBarVisible].
            if (speakBarVisible(fullScreen = fullScreen.value, transportVisible = speakState.visible)) {
                SpeakTransportBar(
                    speakState,
                    onPlayPause = { speakTransport.togglePlayPause() },
                    onStop = { speakTransport.stop() },
                    onRewind = { speakTransport.rewind() },
                    onForward = { speakTransport.forward() },
                    onPrev = { speakTransport.prevVerse() },
                    onNext = { speakTransport.nextVerse() },
                    onBookmark = { speakTransport.onBookmarkButton() },
                    onConfig = { speakTransport.onConfig() },
                    applyNavBarInset = applyNavBarInset,
                    // Fully qualified, and NOT imported, on purpose: the import block of this
                    // file is the one region round 14a's sibling container also appends to (its
                    // new sheet wrappers), and the 14a/14b fork's whole premise is that no two
                    // hunks in this file meet. One qualified call site costs less than a merge
                    // conflict in a 290-line import block.
                    ownsTopEdge = net.bible.sharedcore.reading.speakBarOwnsTopEdge(agentLogVisible.value),
                )
            }
        },
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
        searchSheetSlot = { SearchSheetSlot() },
        searchSettingsSlot = { SearchSettingsSlot() },
        // Settings editor sheets T10: the reading view's in-place text-settings editor.
        textSettingsEditorSlot = { TextSettingsEditorSlot() },
        // Round 13a: the Speak settings sheet.
        speakSettingsSlot = { SpeakSettingsSlot() },
        quickSheetContent = { sheet -> QuickSheetContent(sheet) },
        // Platform-dialog removal Task 18: the reading view's own dialogs (BJI help/delete-confirm).
        readingDialogSlot = { ReadingDialogSlot() },
    )

    /**
     * What the search sheet holds, per phase: the index prompt/progress panel while the document
     * has no usable index, the results otherwise. `Form` and `Closed` render nothing — the form
     * lives in the toolbar (Task 4) and in the modal settings sheet, not in this sheet.
     *
     * A member `@Composable` (rather than a lambda built inline in [install]) only because it is
     * long; it closes over exactly the same host state either way.
     */
    @Composable
    private fun SearchSheetSlot() {
        val phase by searchController.phase.collectAsState()
        val indexDocId = when (val p = phase) {
            is ReadingSearchPhase.NeedsIndex -> p.docId
            is ReadingSearchPhase.Indexing -> p.docId
            else -> null
        }
        if (indexDocId != null) {
            val jobs by searchIndexProgress.jobs.collectAsState()
            val indexError by searchIndexProgress.error.collectAsState()
            // The name is invariant per document, so it is resolved once rather than on every
            // recomposition of a progressing index.
            val documentName = remember(indexDocId) {
                SwordDocumentFacade.getDocumentByInitials(indexDocId)?.name ?: indexDocId
            }
            // Deliberately NOT remembered: `SearchIndexServiceImpl.createIndex` deletes the existing
            // index up front, before the (transactional, build-to-temp-then-rename-on-success) rebuild
            // even starts. So a failed rebuild leaves the document with NO index, even though it had
            // one when this composable was entered — memoizing on `indexDocId` alone would miss that,
            // since the fail->NeedsIndex retry carries the same docId and never re-enters this branch
            // from outside it. A plain read is a single cheap file-existence/DB check, so recomputing
            // it on every recomposition is not worth trading correctness for.
            val isRebuild = searchIndexService.hasIndex(indexDocId)
            SearchIndexPanel(
                documentName = documentName,
                isRebuild = isRebuild,
                indexing = phase is ReadingSearchPhase.Indexing,
                jobs = jobs,
                error = indexError,
                onCreate = { searchController.acceptIndexing() },
                // Declining the index is declining the search: there is nothing else this session
                // could do with a document Lucene cannot read.
                onCancel = { leaveSearch() },
                onDismissError = searchIndexProgress::dismissError,
                engineChanged = searchIndexService.hasLegacyIndex(indexDocId),
            )
            return
        }
        if (phase !is ReadingSearchPhase.Results) return
        val resultsPhase = phase as ReadingSearchPhase.Results
        if (resultsPhase.forEpub) {
            val loading by epubSearchResults.loading.collectAsState()
            val rows by epubSearchResults.results.collectAsState()
            val error by epubSearchResults.error.collectAsState()
            val docAbbrev = remember(resultsPhase.docId) {
                SwordDocumentFacade.getDocumentByInitials(resultsPhase.docId)?.abbreviation ?: resultsPhase.docId
            }
            // Classic's "+" overflow affordance: the service caps at MAX+1, so size > MAX is
            // detectable — `EpubSearchResultsComposeActivity.kt:117-121` verbatim.
            val amount =
                if (rows.size > SearchControl.MAX_SEARCH_RESULTS) "${SearchControl.MAX_SEARCH_RESULTS}+"
                else rows.size.toString()
            SearchSheetContent(
                countLabel = activity.hostContext.getString(R.string.search_with_results2, amount, docAbbrev),
                loading = loading,
                // `SearchSheetContent.error` is a `String?` (non-null shows a dialog over a sheet
                // that stays open). The SWORD controller already carries a message; the EPUB one
                // carries only a Boolean, so the message is supplied here — the same string the
                // standalone EPUB results Activity toasts (`:105-110`), minus its `finish()`.
                error = if (error) activity.getString(R.string.error_executing_search) else null,
                empty = !loading && rows.isEmpty(),
                listState = searchResultsListState.value,
                onDismissError = epubSearchResults::dismissError,
                actions = {},
            ) {
                epubResultRows(rows = rows, onSelect = epubSearchResults::select)
            }
            return
        }
        val loading by searchResults.loading.collectAsState()
        val results by searchResults.results.collectAsState()
        val rows by searchResults.displayed.collectAsState()
        val scriptureShown by searchResults.scriptureShown.collectAsState()
        val scriptureToggleVisible by searchResults.scriptureToggleVisible.collectAsState()
        val error by searchResults.error.collectAsState()
        val selected by searchResults.selectedTranslations.collectAsState()
        val candidates by searchResults.candidates.collectAsState()
        SearchSheetContent(
            countLabel = activity.hostContext.getString(R.string.multi_search_results, results.total, selected.size),
            loading = loading,
            error = error,
            // "Nothing found" only once a search has actually finished — an empty list while
            // loading is not an empty result.
            empty = !loading && rows.isEmpty(),
            listState = searchResultsListState.value,
            onDismissError = searchResults::dismissError,
            actions = {
                BibleResultsActions(
                    candidates = candidates,
                    selectedIds = selected,
                    selectedAbbreviations = selected
                        .mapNotNull { id -> candidates.firstOrNull { it.id == id }?.abbreviation }
                        .joinToString(", "),
                    scriptureToggleVisible = scriptureToggleVisible,
                    scriptureShown = scriptureShown,
                    onToggleScripture = searchResults::toggleScripture,
                    onOpenInWindow = ::openSearchResultsInWindow,
                    onSelectTranslations = { ids -> onSearchTranslationsChosen(ids) },
                )
            },
        ) {
            bibleResultRows(
                rows = rows,
                expanded = searchResultsExpanded,
                // F27: labelling a single-match card with its translation only says anything when
                // more than one translation was searched.
                labelSingleMatchTranslation = selected.size > 1,
                onSelect = ::onSearchResultSelected,
            )
        }
    }

    /**
     * A result row tap (Task 8b Step 2). Resolves the SWORD key pair exactly as
     * `SearchResultsComposeActivity.onSelect` does (`:171-183`) and navigates the active window
     * directly: `setCurrentDocumentAndKey` posts `PassageChangeMediator.onCurrentPageChanged`
     * synchronously (`CurrentPageManager.kt:197-231`), so navigation is complete at that call —
     * unlike the old Activity pair, there is no Activity here to bring to front or `finish()`.
     *
     * Hides the sheet via [ReadingSearchController.onResultOpened] (not the whole search session),
     * which also arms the F83 reopen on the next back press; it does
     * not `partialExpand()`: the scaffold runs with `sheetPeekHeight = 0.dp`, where M3's
     * `PartiallyExpanded` anchor sits exactly where `Hidden` does (see [DriveSearchSheet]'s kdoc), and
     * poking the M3 sheet state directly would desync it from the session's own `sheetVisible`, which
     * is what actually drives it. Search mode and the `Results` phase stay, so the
     * verse is readable and re-opening search serves the same results without re-running.
     */
    // `internal`, not `private`, for direct test coverage (`ReadingSearchEntryPointsTest`) — same
    // rationale as [buildSearchRequest] above; production only reaches this via `SearchSheetSlot`'s
    // `onSelect`.
    internal fun onSearchResultSelected(referenceName: String, translationId: String?) {
        val book = resolveSearchResultBook(translationId)
        if (book == null) {
            Log.w(TAG, "onSearchResultSelected: dropped '$referenceName' — no book resolved for translationId '$translationId'")
            return
        }
        try {
            val key = book.getKey(referenceName)
            windowControl.activeWindowPageManager.setCurrentDocumentAndKey(book, key)
            searchController.onResultOpened()
        } catch (e: Exception) {
            Log.e(TAG, "onSearchResultSelected: bad key '$referenceName' in ${book.initials}", e)
        }
    }

    /**
     * "Open results in a window" — `SearchResultsComposeActivity.openResultsInAWindow` (`:207-224`)
     * verbatim, minus the `finish()` (there is no Activity here to finish) and closing the sheet
     * afterward instead, for the same reason as [onSearchResultSelected] above.
     */
    // `internal`, not `private` — same test-visibility rationale as [onSearchResultSelected] above.
    internal fun openSearchResultsInWindow() {
        val list = BookAndKeyList()
        for (row in searchResults.displayed.value) {
            for (match in row.matches) {
                val book = resolveSearchResultBook(match.translationId) ?: continue
                val key = try {
                    book.getKey(row.referenceName)
                } catch (e: Exception) {
                    Log.e(TAG, "openSearchResultsInWindow: bad key '${row.referenceName}' in ${book.initials}", e)
                    continue
                }
                list.addAll(BookAndKey(key, book))
            }
        }
        linkControl.showLink(fakeBookFactory.multiDocument, list)
        searchController.closeSheet()
    }

    /** Resolve the target book by initials, falling back to the first selected translation — the
     *  SAME pair `SearchResultsComposeActivity.resolveBook` uses (`:166-168`). */
    private fun resolveSearchResultBook(translationId: String?): SwordBook? =
        (translationId?.let { SwordDocumentFacade.getDocumentByInitials(it) }
            ?: searchResults.selectedTranslations.value.firstOrNull()
                ?.let { SwordDocumentFacade.getDocumentByInitials(it) }) as? SwordBook

    /**
     * The results document selector's choice. Mirrors
     * `SearchResultsComposeActivity.onSelectTranslations` — [SearchResultsController.selectTranslations]
     * persists the choice and re-runs — except when it contains a translation with no usable index:
     * that Activity launched `Screen.SearchIndex` carrying the whole search context; here,
     * [ReadingSearchController.promptIndexFor] (F6 Task 11) redirects the session's own phase to
     * `NeedsIndex` for the FIRST unindexed translation, and [searchSelectorPendingIds] remembers the
     * whole chosen set so [onSearchIndexWorkEvent] can chain to any others once that one is built.
     * `promptIndexFor` is what makes this possible at all: [ReadingSearchController] otherwise only
     * derives `NeedsIndex` from the ACTIVE window's document, which a selector choice need not be.
     */
    // `internal`, not `private` — same test-visibility rationale as [buildSearchRequest]/
    // [onSearchResultSelected] above: `BibleResultsActions`' selector lives behind a Compose sheet
    // this repo's `:app` unit tests cannot drive (no `ComposeTestRule`), so `ReadingSearchHostTest`
    // calls this callback directly instead.
    internal fun onSearchTranslationsChosen(ids: List<String>) {
        searchTranslations.value = ids
        searchResults.selectTranslations(ids) { unindexed, chosenIds ->
            searchSelectorPendingIds = chosenIds
            searchController.promptIndexFor(unindexed.first())
        }
    }

    /** The modal search-settings sheet (the toolbar's Tune affordance) — Task 5's form in Task 5's shell. */
    @Composable
    private fun SearchSettingsSlot() {
        SearchSettingsSheet(
            open = searchSettingsOpen.value,
            // `settingsClosed()` re-runs the search when a query is already in flight (Task 3's
            // logic; this is just the wiring) — the same "translation selector refreshes the cache"
            // shape the old results screen already had.
            onDismiss = {
                searchSettingsOpen.value = false
                searchController.settingsClosed()
            },
        ) {
            if (searchSettingsForEpub()) {
                val mode by epubSearchMode.collectAsState()
                EpubSearchSettings(mode = mode, onMode = ::persistEpubSearchMode)
            } else {
                BibleSearchSettings(
                    searchType = searchType.value,
                    bibleSection = searchSection.value,
                    availableTranslations = searchAvailableTranslations.value,
                    selectedTranslationIds = searchTranslations.value,
                    // Read at composition time rather than collected: this is a plain getter over the
                    // active window's page, and the settings sheet is modal and short-lived.
                    currentBookName = searchControl.currentBookName,
                    onSearchType = { searchType.value = it },
                    onBibleSection = { searchSection.value = it },
                    onTranslations = { ids -> setSearchTranslations(ids) },
                )
            }
        }
    }

    /**
     * Whether the settings sheet must show the EPUB word-mode row rather than the Bible form —
     * decided from the LIVE target, the same `resolveDoc()` reading [ReadingSearchController.submit]
     * and [ReadingSearchController.settingsClosed] use, and what spec §5 asks for.
     *
     * It used to read the PHASE (F44 fix round, M2), which in a `Results` phase is the document the
     * rows came FROM, not what the next search will target: EPUB results → tap a Bible pane → open
     * settings showed the EPUB word-mode row, and closing it ran a Bible search whose word mode and
     * translation selection the user had never been shown. Whatever the sheet offers has to be what
     * `settingsClosed()` will then apply.
     *
     * Read at composition time rather than collected, exactly like `currentBookName` below: the sheet
     * is modal and its content composes only while it is open ([SearchSettingsSheet] returns early
     * when closed), so the active window cannot change under it.
     *
     * `internal`, not `private` — `:app` has no `ComposeTestRule`, so `ReadingSearchEntryPointsTest`
     * pins this decision by calling it directly, the same convention as [buildSearchRequest].
     */
    internal fun searchSettingsForEpub(): Boolean =
        searchDocumentInfo(documentControl.currentDocument)?.isEpub == true

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

        /** Settings keys shared with the search Activities — see [searchQueries]/[setSearchTranslations]. */
        private const val SEARCH_TRANSLATIONS_KEY = "search_selected_translations"
        private const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"

        /** `SearchIndexProgressComposeActivity`'s post-finish poll interval (`:365`'s `pause(2)`).
         *  Its other timing — the ~4 s "no tasks running" reveal — is deliberately not reproduced;
         *  see [startSearchIndexing]. */
        private const val SEARCH_INDEX_POLL_INTERVAL_MS = 2_000L

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
