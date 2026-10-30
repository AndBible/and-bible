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

package net.bible.sharedui.host.reading

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.bible.android.control.PageChange
import net.bible.android.control.PassageChangeMediator
import net.bible.android.control.page.PageControl
import net.bible.android.view.activity.page.bibleOverlayText
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.reading.shouldRestorePaneFocusOnDrawerClose
import net.bible.sharedui.components.OverlayPresence
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedui.reading.toolbar.ReadingDrawerMenu
import net.bible.sharedui.reading.toolbar.ReadingOverflowController
import net.bible.sharedui.reading.toolbar.ReadingToolbarCommands
import net.bible.sharedui.strings.keyed.KeyedReadingMenuStrings
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.download.DownloadControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.page.GlobalTextDisplaySettingsSource
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.search.SearchControl
import net.bible.android.control.speak.SpeakControl
import net.bible.android.control.page.DocumentCategory
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.Selection
import net.bible.service.llm.PromptContext
import net.bible.android.view.activity.navigation.DocRowMapper
import net.bible.android.view.activity.navigation.GridPassageControllerFactory
import net.bible.android.view.activity.page.WindowPaneMenuDispatcher
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.android.view.activity.page.screen.paneBackgroundArgb
import net.bible.android.view.activity.page.screen.paneWindowLabel
import net.bible.android.view.activity.page.screen.paneWindowTopLabel
import net.bible.android.view.activity.page.screen.speakBarVisible
import net.bible.service.common.RecentDocumentsStore
import net.bible.service.download.FakeBookFactory
import net.bible.service.download.hideFromSelector
import net.bible.service.history.HistoryManager
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingLlmDialog
import net.bible.sharedcore.ai.reading.ReadingLlmDialogController
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingOverlayExclusion
import net.bible.sharedcore.nav.ManageLabelsResult
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.nav.WorkspaceResult
import net.bible.sharedcore.navigation.DocRow
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.resolveDisableAnimations
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.service.common.DisplayColorMode
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.settings.RecentDisplaySettings
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.assembly.HostStartup
import net.bible.sharedui.host.app.HostNavShell
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.host.ReadingFullScreen
import net.bible.sharedui.reading.host.ReadingHostDeps
import net.bible.sharedui.reading.host.ReadingHostHooks
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.reading.host.slots.ReadingHostSlots
import net.bible.sharedui.reading.host.slots.ReadingSearchServices
import net.bible.sharedui.reading.host.slots.ReadingSearchState
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.reading.nav.ReadingResultCollector
import net.bible.sharedui.reading.session.ReadingOffsets
import net.bible.sharedui.reading.workspaceToLoad
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.strings.keyed.KeyedPaneMenuStrings
import org.koin.core.Koin

/** The English strings, built once, for a host that was not given the app's. */
private val englishStrings: Strings by lazy { iosStrings("en") }

/**
 * The iOS reading host's dependencies: the common [ReadingHostState] and the [ReadingHostDeps] / [ReadingNavDeps]
 * built from Koin. Construct only after [net.bible.service.startup.ReadingAppInit.createWindowRepository] has run,
 * because [hostState] reads the [WindowControl]'s repository.
 *
 * [slots] is the common [ReadingHostSlots] built over the iOS ports ([HostReadingSlotPorts.kt]); [deps] hands its
 * composables to the screen exactly as Android's `ComposeReadingViewHost` does. [strings] is read per composition;
 * [openUrl] opens a URL through the [platform] (the Speak sheet's system-settings row goes through it). [platform] makes
 * the system calls and builds the platform objects ([ReadingHostPlatform]; iOS: [IosReadingHostPlatform]).
 */
@kotlin.native.HiddenFromObjC
class HostReadingHost(
    private val scope: CoroutineScope,
    private val shell: HostNavShell,
    private val koin: Koin,
    /** The system calls and platform objects of the host (iOS: [IosReadingHostPlatform]). */
    internal val platform: ReadingHostPlatform,
    private val nightModeState: NightModeState = koin.get(),
    /** A live night-mode source (see [HostNightModeObserver]); null keeps a snapshot of [nightModeState]. */
    private val nightModeSource: State<Boolean>? = null,
    /** A live monochrome source (colour mode other than NORMAL, see [HostNightModeObserver]); null keeps a snapshot. */
    private val monochromeSource: State<Boolean>? = null,
    private val strings: () -> Strings = { englishStrings },
    private val openUrl: (String) -> Unit = platform::openUrl,
    /** The app's `manageLabels` result channel (the label chooser's answer); a private one when the host stands alone. */
    labelResults: NavResultChannel<ManageLabelsResult> = NavResultChannel { },
    /** Applies the document, key and passage choosers' answers to the window that asked; null: answers are not applied. */
    private val readingResults: HostReadingResults? = null,
    /** Hides or shows the system status bar (full screen hides it); the platform's [ReadingHostPlatform.setStatusBarHidden] by default. */
    private val statusBarHidden: (Boolean) -> Unit = platform::setStatusBarHidden,
    /** Whether the AI features are configured (Android: `CommonUtils.settings.llmConfigured`); shows the AI rows and menu item. */
    private val llmConfiguredSource: () -> Boolean = defaultLlmConfigured(koin),
    /** The prompt lists and model choices behind the prompt selector dialog. */
    private val llmService: () -> ReadingLlmService = { koin.get() },
    /**
     * Starts an agent run once a prompt (and its specification and model) is chosen; null runs [HostAgentStarter]
     * (Android: `AgentForegroundService.startAgent`).
     */
    private val startAgent: ((promptId: IdType, selection: Selection, userSpecification: String?, modelOverrideId: IdType?) -> Unit)? = null,
    /** Starts a regenerate run (same starter); null runs [HostAgentStarter.startRegenerate]. */
    private val startRegenerate: ((pageId: IdType, targetWindowId: IdType?, instructions: String?, keepPrevious: Boolean, freshRun: Boolean, modelOverrideId: IdType?) -> Unit)? = null,
    /** Shows the "Regenerating…" notice in a window before a regenerate run starts (Android loads an `ErrorDocument`). */
    private val showNotice: (windowId: String, text: String) -> Unit = HostReadingWindows::showNotice,
) {
    private val windowControl: WindowControl = koin.get()
    private val settings: AppSettings = koin.get()
    private val documentControl: DocumentControl = koin.get()

    /**
     * The document offsets of every window ([ReadingOffsets]): the destination reports the safe-area inset, the slots below
     * report the search sheet, agent log and speak bar; a change is pushed to each window as `set_offsets`.
     */
    internal val offsets = ReadingOffsets(
        restoreButtonsVisible = { windowControl.windowRepository.workspaceSettings.restoreButtonsVisible },
    ) { HostReadingWindows.offsetsChanged() }

    /**
     * The reading view's full-screen state, owned here so it outlives a rotation (the host is not rebuilt, only the
     * composition is). Always change it through [setFullScreen] so the status bar follows.
     */
    internal val fullScreen = ReadingFullScreen()

    /**
     * Enters or leaves full screen and hides or shows the status bar with it. The toolbar, tab bar and speak bar follow
     * `fullScreen.state` in common code; the status bar is a platform matter, see [ReadingHostPlatform.setStatusBarHidden].
     */
    internal fun setFullScreen(value: Boolean, byDoubleTap: Boolean = false) {
        fullScreen.set(value, byDoubleTap)
        statusBarHidden(fullScreen.state.value)
    }

    /**
     * Double tap, auto full screen and swipe mode for every pane (see [HostReadingGestures]). Full-screen changes go
     * through [setFullScreen] so the status bar follows; a swipe is carried out by [HostReadingWindows.performSwipe].
     */
    internal val gestures: HostReadingGestures by lazy {
        HostReadingGestures(
            fullScreen = fullScreen,
            settings = koin.get<AppSettings>(),
            isRtl = platform::isRightToLeft,
            onSwipe = HostReadingWindows::performSwipe,
            setFullScreen = ::setFullScreen,
            onPaneTouched = { hostState.onBibleViewTouched() },
            onPaneScrolled = { hostState.onBibleViewScrolled() },
        )
    }

    /** What opening search does to full screen: leaves it, as the search field lives in the toolbar full screen hides. */
    internal val onLeaveFullScreen: () -> Unit = { setFullScreen(false) }

    /** The Bible reference overlay's text; empty without a current key (Android's silent `KeyIsNull` catch). */
    internal val overlayText = mutableStateOf("")

    /** Whether the active window shows a Bible (the overlay is only for Bibles). */
    internal val activeIsBibleShown = mutableStateOf(false)

    /** Re-reads [overlayText] and [activeIsBibleShown]; called on the same events Android's host mirrors them from. */
    internal fun refreshOverlay() {
        // Like `refreshToolbar`: runs from event handlers and `deps()`, where a throw must not break the event stream.
        try {
            overlayText.value = bibleOverlayText(koin.get<PageControl>()) ?: ""
            activeIsBibleShown.value = windowControl.activeWindow.pageManager.isBibleShown
        } catch (e: Exception) {
            nsLog("ReadingOverlay: overlay refresh failed: ${e.stackTraceToString()}")
        }
    }

    /** The agent log's last measured collapsed height in dp (the offsets need it while the panel is visible). */
    private var agentLogHeightDp = 0f

    /** The navigator every window session and the toolbar use; shares [hostState] and the label result channel. */
    internal val navigator: HostReadingNavigator by lazy {
        HostReadingNavigator(
            shell = shell,
            hostState = hostState,
            links = koin.get<ExternalLinks>(),
            labelResults = labelResults,
            downloadControl = { koin.get<DownloadControl>() },
            fakeBooks = { koin.get<FakeBookFactory>() },
            workspaceSettings = { windowControl.windowRepository.workspaceSettings },
            chooserRequests = readingResults ?: ChooserRequests.None,
            // Android's composeSearch: the toolbar field and results sheet of THIS view, not the search screens.
            openReadingSearch = { searchState.openSearch() },
        )
    }

    /** The hooks [hostState] runs on user actions (test-visible: `everySlotIsSupplied` pins which ones are supplied). */
    internal val hooks: ReadingHostHooks = ReadingHostHooks(
            closeSpeakSheet = { slots.speakSheet.close() },
            closeTextSettingsEditor = { slots.textSettingsEditor.close() },
            openCompare = { range -> koin.get<LinkControl>().openCompare(range) },
            applyChosenVerse = ::applyChosenVerse,
            formatHistoryTimestamp = { createdAt -> formatHistoryTimestamp(createdAt.getTime()) },
            installedDocRows = ::installedDocRows,
            recentDocumentInitials = { koin.get<RecentDocumentsStore>().read() },
            paneMenuItemsFor = ::paneMenuItemsFor,
            dismissLlmSheets = ::dismissLlmSheets,
            openPromptSelector = ::openPromptSelector,
            openRegenerate = ::openRegenerate,
    )

    /**
     * The reading view's AI dialogs (prompt selector, specify-before-run, model choice): the same common controller Android's
     * host drives. Opening one of its two sheets closes the other modal overlays first ([ReadingOverlayExclusion]).
     */
    internal val llmDialogs: ReadingLlmDialogController by lazy {
        ReadingLlmDialogController(
            llmService(),
            scope,
            onSheetOpening = { ReadingOverlayExclusion.closedBy(ReadingOverlay.Llm).forEach(hostState::closeOverlay) },
        )
    }

    private val agentStarter: HostAgentStarter by lazy {
        HostAgentStarter(
            scope, windowRepositoryId = ::currentWorkspaceId,
            beginBackgroundTask = platform::beginBackgroundTask, endBackgroundTask = platform::endBackgroundTask,
            invalidTaskId = platform.invalidBackgroundTaskId,
        )
    }

    /** The workspace an agent run starts in (read at start time). */
    internal fun currentWorkspaceId(): IdType = windowControl.windowRepository.id

    /** Dismisses the two SHEET-shaped AI dialogs; the AlertDialog-shaped ones stay (a dialog over a sheet is fine). */
    private fun dismissLlmSheets() {
        when (llmDialogs.state.value.dialog) {
            is ReadingLlmDialog.PromptSelector, is ReadingLlmDialog.ModelSelection -> llmDialogs.dismiss()
            is ReadingLlmDialog.None, is ReadingLlmDialog.SpecifyBeforeRun, is ReadingLlmDialog.Regenerate -> Unit
        }
    }

    /**
     * Android's `ComposeReadingViewHost.showPromptSelector`: opens the prompt selector for [selection] in [context]; choosing
     * a prompt starts the agent with the chosen specification and model.
     */
    internal fun openPromptSelector(selection: Selection, context: PromptContext, category: DocumentCategory?) {
        llmDialogs.openPromptSelector(context.name, category?.name) { promptId, userSpecification, modelOverrideId ->
            val model = modelOverrideId?.let { IdType(it) }
            val start = startAgent ?: agentStarter::start
            start(IdType(promptId), selection, userSpecification, model)
        }
    }

    /**
     * Android's `ComposeReadingViewHost.showRegenerate`: the regenerate confirmation for the AI page [pageId], asked from
     * the window [windowId] (that window shows the result and the "Regenerating…" notice, never the active one by default).
     */
    internal fun openRegenerate(pageId: IdType, windowId: IdType) {
        llmDialogs.openRegenerate(pageId.toString()) { _, instructions, keepPrevious, freshRun, modelOverrideId ->
            showNotice(windowId.toString(), strings().aiDocumentRegenerating)
            val start = startRegenerate ?: agentStarter::startRegenerate
            start(pageId, windowId, instructions, keepPrevious, freshRun, modelOverrideId?.let { IdType(it) })
        }
    }

    /**
     * The selection menu's AI action (Android: `showLlmPromptSelector` with `VERSE_SELECTION`). The document category is the
     * window the selection is in; Android reads the active window, which is the same one when the user selects in it.
     */
    internal fun openSelectionPromptSelector(windowId: String, selection: Selection) {
        val window = windowControl.windowRepository.getWindow(IdType(windowId)) ?: windowControl.windowRepository.activeWindow
        openPromptSelector(selection, PromptContext.VERSE_SELECTION, window.pageManager.currentPage.documentCategory)
    }

    /** Whether the AI features are configured; the one answer the pane menu, overflow menu and selection menu share. */
    internal fun llmConfigured(): Boolean = llmConfiguredSource()

    /** The overflow menu's "AI Actions": no selection, `WORKSPACE_MENU`, any document category (Android's `llmActionsSubMenu`). */
    private fun showWorkspaceLlmActions() {
        val noSelection = Selection(
            bookInitials = null, startOrdinal = -1, startOffset = null, endOrdinal = -1, endOffset = null, bookmarks = emptyList(),
        )
        openPromptSelector(noSelection, PromptContext.WORKSPACE_MENU, null)
    }

    val hostState: ReadingHostState = ReadingHostState(
        scope = scope,
        settings = settings,
        windowControl = windowControl,
        historyManager = koin.get<HistoryManager>(),
        documentControl = documentControl,
        hooks = hooks,
    )

    private val speakControl: SpeakControl by lazy { koin.get() }

    private val paneMenuPlatform by lazy {
        HostWindowPaneMenuPlatform(
            shell = shell,
            windowControl = { windowControl },
            slots = { slots },
            clipboard = koin.get<ClipboardText>(),
            toast = ::toast,
            copiedMessage = { KeyedPaneMenuStrings(HostStartup.stringTable).referenceCopiedToClipboard },
            bridgeFor = { id -> if (HostReadingWindows.hasAssembly(id)) HostReadingWindows.bridgeFor(id) else null },
            openPromptSelector = ::openPromptSelector,
            newDocumentPicker = { platform.newDocumentPicker() },
            newCsvSinkOpener = { platform.newCsvSinkOpener() },
            csvTimestamp = platform::csvTimestamp,
        )
    }

    /**
     * The window (☰) pane menu's rows for [windowId], built by the common [WindowPaneMenuStateBuilder] with the iOS strings,
     * text display prefs and recent settings. Rebuilt per call so the labels follow the app language. Rows iOS cannot run
     * are left out ([IOS_OMITTED_PANE_MENU_ROWS]).
     */
    internal fun paneMenuItemsFor(windowId: String): List<WindowPaneMenuItem>? {
        val window = windowControl.windowRepository.getWindow(IdType(windowId)) ?: return null
        return WindowPaneMenuStateBuilder(
            windowControl = windowControl,
            speakControl = speakControl,
            strings = KeyedPaneMenuStrings(HostStartup.stringTable),
            prefItems = koin.get<TextDisplayPrefItems>(),
            recentDisplaySettings = { RecentDisplaySettings(settings).sorted },
            globalTextDisplaySettings = { koin.get<GlobalTextDisplaySettingsSource>().current },
            llmConfigured = llmConfiguredSource,
            omit = IOS_OMITTED_PANE_MENU_ROWS,
        ).build(window)
    }

    private val paneMenuDispatcher by lazy {
        WindowPaneMenuDispatcher(
            windowControl = windowControl,
            speakControl = speakControl,
            windowRepository = { windowControl.windowRepository },
            controller = { readingViewController },
            prefItems = koin.get<TextDisplayPrefItems>(),
            recentDisplaySettings = { RecentDisplaySettings(settings).sorted },
            globalTextDisplaySettings = { koin.get<GlobalTextDisplaySettingsSource>().current },
            platform = paneMenuPlatform,
        )
    }

    /** Runs a chosen ☰ row (see [WindowPaneMenuDispatcher.handle]); true keeps the menu open. */
    internal fun handlePaneMenuItem(windowId: String, id: String): Boolean = paneMenuDispatcher.handle(windowId, id)

    /** The shared [ChooserTarget] of the active window (one implementation of every chooser answer). */
    private fun activeChooserTarget(): ChooserTarget =
        hostChooserTarget(koin, HostStartup.stringTable, windowControl.activeWindowPageManager, scope)

    /** Android's `ReadingCommands.applyChosenVerse` (non-bookmark arm): a malformed OSIS id only toasts. */
    private fun applyChosenVerse(osisId: String) = activeChooserTarget().applyVerse(osisId)

    private fun formatHistoryTimestamp(epochMillis: Long): String = platform.formatHistoryTimestamp(epochMillis)

    /** Shown as an in-app toast: the iOS `UserNotifier.showInfo` feeds `AppToasts`. */
    private fun toast(message: String) = koin.get<UserNotifier>().showInfo(message, false)

    /** Android's `installedDocRows` hook: installed and pseudo documents through the one shared [DocRowMapper]. */
    private fun installedDocRows(): List<DocRow> {
        val books = SwordDocumentFacade.documents +
            koin.get<FakeBookFactory>().pseudoDocuments.filterNot { it.hideFromSelector }
        val mapper = DocRowMapper(koin.get<DownloadControl>(), books)
        return books.map { mapper.toDocRow(it) }
    }

    /** ONE lazy instance shared by [deps] and the pane menu dispatcher, so every window command goes through one seam. */
    private val readingViewController: ReadingViewController by lazy {
        ReadingViewController(koin.get<WindowStateService>(), koin.get<WindowCommands>())
    }

    private val agentSessionService: AgentSessionService = koin.get()

    private val agentLog = AgentLogController(
        agentSessionService,
        scope,
        onCompletedToast = { koin.get<UserNotifier>().showInfo(strings().aiTaskCompleted, false) },
        onOpenRawLog = { shell.navigate(NavRoutes.rawLlmLog(workspaceId = agentSessionService.currentWorkspaceId())) },
    )

    internal val speakTransport = SpeakTransportController(
        koin.get<SpeakTransportService>(),
        koin.get<SpeakSettingsService>(),
        scope,
        onConfig = { slots.showSpeakSettings() },
    )

    private val searchState = ReadingSearchState(
        settings = settings,
        scope = scope,
        platform = HostReadingSearchPlatform(
            currentDocument = { documentControl.currentDocument },
            currentBookName = { koin.get<SearchControl>().currentBookName },
            windowControl = { windowControl },
            linkControl = { koin.get<LinkControl>() },
            fakeBookFactory = { koin.get<FakeBookFactory>() },
        ),
        indexJobs = HostSearchIndexJobs(),
        services = ReadingSearchServices(
            koin.get<BibleSearchService>(), koin.get<EpubSearchService>(),
            koin.get<SearchResultsCache>(), koin.get<SearchIndexService>(),
        ),
        // The search field lives in the toolbar, which full screen hides: opening search leaves full screen.
        onLeaveFullScreen = onLeaveFullScreen,
    )

    /**
     * Android's `searchController.activeDocumentChanged()` call sites: search mode outlives a window switch and a document
     * swap, so the panel's target document follows them.
     */
    private val searchSubscriptions = Subscriptions().also { subscriptions ->
        subscriptions.add(
            PassageChangeMediator.changes.subscribeOnMain { change ->
                if (change is PageChange.VerseChanged) searchState.controller.activeDocumentChanged()
                // Android's overlay events (`VerseChanged`, active window); `BibleVerseChanged` too, where no `VerseChanged` follows.
                // `ContentLoaded` as well, like the toolbar title: the first page is only current once its content has loaded
                // (an event sent before this host subscribed is lost), and without this the overlay text stayed empty.
                if (change is PageChange.VerseChanged || change is PageChange.BibleVerseChanged || change == PageChange.ContentLoaded) refreshOverlay()
            },
        )
        subscriptions.add(
            koin.get<WindowStateServiceImpl>().windowChanges.subscribeOnMain { change ->
                if (change is WindowChange.ActiveWindowChanged) {
                    refreshOverlay()
                    searchState.controller.activeDocumentChanged()
                }
            },
        )
    }

    private val gridFactory = GridPassageControllerFactory(
        navigationControl = koin.get(),
        windowControl = windowControl,
        settings = settings,
        workspaceName = { windowControl.windowRepository.name },
        isPortrait = platform::isPortrait,
    )

    private val quickActions: HostReadingQuickActions = HostReadingQuickActions(
        { windowControl }, { koin.get<FakeBookFactory>() }, settings, scope, ::toast,
        onToolbarMayHaveChanged = { toolbarHost.refreshToolbar() },
        chooserTarget = ::activeChooserTarget,
    )

    /**
     * Android's `applyWorkspaceChangedResult`: another workspace is loaded, or the current one reloaded when it changed. The
     * outgoing workspace is not saved ([HostReadingQuickActions.switchToWorkspace]): the selector already saved it, and
     * saving after a rename would restore the old name.
     */
    private fun applyWorkspaceResult(result: WorkspaceResult) {
        val target = workspaceToLoad(result.workspaceId, result.changed, windowControl.windowRepository.id.toString())
        if (target == null) {
            nsLog("IosReadingHost: a workspace answer named no change; nothing applied.")
            return
        }
        scope.launch { quickActions.switchToWorkspace(target, saveOutgoing = false) }
    }

    init {
        readingResults?.applyWorkspace = ::applyWorkspaceResult
    }

    /**
     * Every host-built slot of the reading view, in common code. Declared after the collaborators it wraps;
     * [hostState]'s hooks reach it lazily (they run on user actions).
     */
    internal val slots: ReadingHostSlots = ReadingHostSlots(
        hostState = hostState,
        strings = strings,
        externalLinks = koin.get<ExternalLinks>(),
        agentLog = agentLog,
        speakTransport = speakTransport,
        disableAnimations = { settings.resolveDisableAnimations(false) },
        fullScreen = { fullScreen.state.value },
        searchState = searchState,
        textDisplaySettingsService = { koin.get<TextDisplaySettingsService>() },
        scope = scope,
        imagePicker = platform.newImagePicker(),
        thumbnailFor = { null },
        speakSettingsService = koin.get<SpeakSettingsService>(),
        speakPlatform = HostSpeakSettingsPlatform(openUrl, platform.settingsUrl),
        gridPassages = gridFactory,
        workspaceService = { koin.get<WorkspaceService>() },
        readingProgressService = { koin.get<ReadingProgressService>() },
        quickActions = quickActions,
        keyChooserPage = HostReadingKeyChooserPage { windowControl },
        navigator = navigator,
        textShare = platform.newTextShare(),
        clipboard = koin.get<ClipboardText>(),
        toast = ::toast,
        workspaceName = { windowControl.windowRepository.name },
        activeWindowPosition = { windowControl.activeWindowPosition },
        currentDocumentInitials = { documentControl.currentDocument?.initials },
    )

    /** What the toolbar host used to default to: fresh device preferences and a night state over them, built on first use. */
    private val toolbarDevice = platform.newToolbarDeviceState()

    /**
     * The toolbar, overflow menu and drawer over the iOS controls ([HostReadingToolbarHost]); the common decision logic
     * and the menus built from it follow.
     */
    internal val toolbarHost: HostReadingToolbarHost = HostReadingToolbarHost(
        scope = scope,
        shell = shell,
        hostState = hostState,
        navigator = navigator,
        windowControl = { windowControl },
        documentControl = { documentControl },
        settings = settings,
        prefItems = { koin.get<TextDisplayPrefItems>() },
        recentSettings = { RecentDisplaySettings(settings).sorted },
        globalSettings = { koin.get<GlobalTextDisplaySettingsSource>().current },
        toolbarState = { koin.get<ToolbarStateService>() },
        speakControl = { speakControl },
        transport = { koin.get<SpeakTransportServiceImpl>() },
        slots = { slots },
        switchToWorkspace = quickActions::switchToWorkspace,
        searchModeActive = { searchState.searchBar.value != null },
        toggleFullScreen = { setFullScreen(!fullScreen.state.value) },
        chooserRequests = readingResults ?: ChooserRequests.None,
        externalLinks = koin.get<ExternalLinks>(),
        windowStateService = { koin.get<WindowStateServiceImpl>() },
        fakeBooks = { koin.get<FakeBookFactory>() },
        llmConfiguredSource = ::llmConfigured,
        showLlmActionsSource = ::showWorkspaceLlmActions,
        deviceSettings = toolbarDevice.deviceSettings,
        nightModeOn = toolbarDevice.nightModeOn,
        nightModeManual = toolbarDevice.nightModeManual,
        versionNameSource = platform::versionName,
        tiltAvailable = { platform.tiltAvailable() },
        extras = koin.getOrNull<HostReadingExtras>() ?: HostReadingExtras.None,
    )


    private val menuStrings = { KeyedReadingMenuStrings(HostStartup.stringTable) }

    private val toolbarCommands = ReadingToolbarCommands(toolbarHost)

    private val overflowMenu = ReadingOverflowController(toolbarHost, menuStrings).also { menu ->
        toolbarHost.rebuildOverflow = menu::build
    }

    private val drawerMenu = ReadingDrawerMenu(toolbarHost, menuStrings)

    /**
     * The drawer's rows. Rebuilt whenever the toolbar state changes (that is when the Search and Speak rows' enabled
     * flags can change), which is Android's `rebuildDrawer` from `updateActions`.
     */
    internal val drawerMenuState = mutableStateOf(DrawerMenuState.EMPTY)

    private fun rebuildDrawer() {
        try {
            drawerMenuState.value = drawerMenu.build()
        } catch (e: Exception) {
            nsLog("ReadingDrawer: could not build the drawer menu: ${e.stackTraceToString()}")
        }
    }

    private val drawerWatch = scope.launch {
        koin.get<ToolbarStateService>().toolbar.collect { rebuildDrawer() }
    }

    /** Strong's find-all in the reading view's search; false: not handled, the caller falls back to its own route. */
    fun openSearchStrongs(ref: String, documentInitials: List<String>): Boolean = searchState.openSearchStrongs(ref, documentInitials)

    /**
     * The text-selection menu's "Search": the reading view's search opens and runs [text] as a phrase search, decorated
     * exactly as Android's `BibleView` does before it hands the query to the hosted search (`preDecorated = true`).
     */
    fun openSearchWithSelection(text: String) {
        val decorated = koin.get<net.bible.android.control.search.SearchControl>().decorateSearchString(
            text, org.crosswire.jsword.index.search.SearchType.PHRASE,
            net.bible.android.control.search.SearchControl.SearchBibleSection.ALL, "",
        )
        searchState.openSearch(decorated, preDecorated = true)
    }

    /** Ends the slots' own background work (the search index feed); call before cancelling [scope]. */
    fun dispose() {
        if (fullScreen.state.value) setFullScreen(false) // never leave the status bar hidden behind a closed reading view
        drawerWatch.cancel()
        searchSubscriptions.cancelAll()
        slots.dispose()
    }

    /**
     * The toolbar callbacks: all sixteen do what Android's `ReadingCommands`/`ComposeReadingViewHost` toolbar callbacks
     * do ([ReadingToolbarCommands]); none is a logged pending. The gaps inside them (auto assign labels,
     * the drawer's extra rows) are named on [HostReadingToolbarHost]; the extra rows wait for an `HostReadingExtras`.
     */
    internal fun callbacks(): ReadingToolbarCallbacks = toolbarCommands.callbacks()

    /** Every drawer row that [ReadingDrawerMenu] shows; the ReadingHost closes the drawer before calling back. */
    private fun onDrawerItemClick(id: String) = drawerMenu.handle(id)

    /** Runs an overflow row; true keeps the menu open and rebuilds it to show a flipped check. */
    private fun onOverflowItemClick(id: String) {
        if (overflowMenu.handle(id)) {
            toolbarHost.overflowItems.value = overflowMenu.build()
        } else {
            toolbarHost.overflowExpanded.value = false
        }
    }

    fun deps(icons: ReadingToolbarIcons, paneIcons: HostPaneIcons? = null): ReadingHostDeps {
        // The state service only refreshes on events; the first snapshot is taken here, once the repository exists.
        toolbarHost.refreshToolbar()
        refreshOverlay()
        return buildDeps(icons, paneIcons)
    }

    /** The live night-mode and monochrome states (a snapshot of the settings when no live source was given). */
    internal val nightState: State<Boolean> by lazy { nightModeSource ?: mutableStateOf(nightModeState.nightMode) }
    internal val monochromeState: State<Boolean> by lazy {
        monochromeSource ?: mutableStateOf(
            (DisplayColorMode.fromValue(settings.getString("display_color_mode", null)) ?: DisplayColorMode.NORMAL) != DisplayColorMode.NORMAL,
        )
    }

    /** Android's `paneBackgroundArgbFor`: the window's page background, read in composition so a mode switch repaints it. */
    private fun paneBackgroundFor(windowId: String): Color? {
        val colors = windowControl.windowRepository.getWindow(IdType(windowId))?.pageManager?.actualTextDisplaySettings?.colors
        return paneBackgroundArgb(colors, nightState.value, monochromeState.value)?.let { Color(it) }
    }

    /** Android's `restorePaneFocus`: not while the search field is open (it keeps the focus). */
    private fun restorePaneFocus() {
        if (!shouldRestorePaneFocusOnDrawerClose(searchBarOpen = searchState.searchBar.value != null)) return
        windowControl.windowRepository.activeWindow.bibleView?.requestFocus()
    }

    private fun buildDeps(icons: ReadingToolbarIcons, paneIcons: HostPaneIcons?): ReadingHostDeps = ReadingHostDeps(
        state = hostState,
        controller = readingViewController,
        toolbar = koin.get<ToolbarStateService>().toolbar,
        fullScreen = fullScreen.state,
        overlayText = overlayText,
        activeIsBibleShown = activeIsBibleShown,
        toolbarCallbacks = callbacks(),
        toolbarIcons = { icons },
        nightMode = nightState,
        monochrome = monochromeState,
        disableAnimations = { settings.resolveDisableAnimations(false) },
        overflowItems = toolbarHost.overflowItems,
        overflowExpanded = toolbarHost.overflowExpanded,
        onOverflowItemClick = ::onOverflowItemClick,
        onOverflowDismiss = { toolbarHost.overflowExpanded.value = false },
        drawerMenu = drawerMenuState,
        drawerIcon = { key -> rememberIosDrawerIcon(key) },
        onDrawerItemClick = ::onDrawerItemClick,
        onDrawerClosed = ::restorePaneFocus,
        paneBackground = ::paneBackgroundFor,
        agentLogSlot = { applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured ->
            slots.AgentLog(applyNavBarInset, maxHeightDp, collapsedDp) { height ->
                agentLogHeightDp = height
                offsets.setAgentLog(agentLog.state.value.visible, height)
                onCollapsedHeightMeasured(height)
            }
        },
        speakBarSlot = { applyNavBarInset -> slots.SpeakBar(applyNavBarInset) },
        agentLogVisible = {
            val agentLogState = agentLog.state.collectAsState()
            val visible = remember { derivedStateOf { agentLogState.value.visible } }
            SideEffect { offsets.setAgentLog(visible.value, agentLogHeightDp) }
            visible.value
        },
        speakBarVisible = {
            val speakState = speakTransport.state.collectAsState()
            val visible = remember {
                derivedStateOf { speakBarVisible(fullScreen = fullScreen.state.value, transportVisible = speakState.value.visible) }
            }
            SideEffect { offsets.setSpeakBarVisible(visible.value) }
            visible.value
        },
        speakDialog = speakTransport.dialog,
        onSpeakBookmarkChosen = speakTransport::onSpeakBookmarkChosen,
        onSpeakDialogDismiss = speakTransport::dismissDialog,
        searchBar = searchState.searchBar,
        searchBarCallbacks = searchState.searchBarCallbacks,
        searchSheetVisible = searchState.controller.sheetVisible,
        onSearchSheetDismissed = { searchState.controller.closeSheet() },
        searchUnavailableDocName = searchState.searchUnavailableDocName,
        onSearchUnavailableMessageShown = searchState::searchUnavailableMessageShown,
        onSearchSheetOffsetsChanged = { visible, heightPx -> offsets.setSearchSheet(visible, heightPx, platform.screenScale()) },
        searchSheetSlot = { slots.SearchSheet() },
        searchSettingsSlot = { slots.SearchSettings() },
        textSettingsEditorSlot = { slots.TextSettingsEditor() },
        speakSettingsSlot = { slots.SpeakSettings() },
        quickSheetContent = { sheet -> slots.QuickSheet(sheet) },
        readingDialogSlot = { slots.ReadingDialog() },
        windowLabel = { snapshot -> paneWindowLabel(windowControl.windowRepository.getWindow(IdType(snapshot.id))) },
        windowTopLabel = { snapshot -> paneWindowTopLabel(windowControl.windowRepository.getWindow(IdType(snapshot.id))) },
        windowIcon = { snapshot ->
            val category = windowControl.windowRepository.getWindow(IdType(snapshot.id))
                ?.pageManager?.currentPage?.currentDocument?.bookCategory
            paneIcons?.forCategory(category)
        },
        handlePaneMenuItem = ::handlePaneMenuItem,
        menuIcon = { key -> rememberIosPaneMenuIcon(key) },
        llmDialog = llmDialogs.state,
        onLlmPromptChosen = llmDialogs::onPromptChosen,
        onLlmToggleFavorite = llmDialogs::onToggleFavorite,
        onLlmCategoryExpandedChanged = llmDialogs::onCategoryExpandedChanged,
        onLlmSpecifySubmitted = llmDialogs::onSpecifySubmitted,
        onLlmModelChosen = llmDialogs::onModelChosen,
        onLlmRegenerateConfirmed = llmDialogs::onRegenerateConfirmed,
        onLlmDismiss = llmDialogs::dismiss,
    )

    /** Back (Esc on desktop) closes the drawer, the search sheet and the search bar before it passes on; see [ReadingBackChain]. */
    private val backChain = ReadingBackChain(
        isDrawerOpen = { hostState.isDrawerOpen },
        closeDrawer = { hostState.closeDrawer() },
        isSearchSheetVisible = { searchState.controller.sheetVisible.value },
        closeSearchSheet = { searchState.controller.closeSheet() },
        isSearchBarOpen = { searchState.searchBar.value != null },
        closeSearchBar = { searchState.leaveSearch() },
    )

    init {
        // The platform's "an overlay closed: give the keys back to the pane" (desktop) obeys the drawer rule: not while the
        // search field is open and keeps the focus. [restorePaneFocus] is the same rule for the drawer on every platform.
        OverlayPresence.restoreAllowed = {
            shouldRestorePaneFocusOnDrawerClose(searchBarOpen = searchState.searchBar.value != null)
        }
    }

    fun navDeps(content: @Composable () -> Unit): ReadingNavDeps =
        hostReadingNavDeps(
            this,
            shell,
            HostStartup.stringTable,
            readingResults?.collectors() ?: emptyList(),
            backChain::onBack,
            content,
        )

    internal companion object {
        /** The production answer: the prompt service's configured flag; false while the service is not available yet. */
        fun defaultLlmConfigured(koin: Koin): () -> Boolean =
            { runCatching { koin.get<PromptService>().configured.value }.getOrDefault(false) }
    }
}

/** The reading destination's [ReadingNavDeps]; [hostToken] is the identity the destination registers under. */
@kotlin.native.HiddenFromObjC
fun hostReadingNavDeps(
    hostToken: Any,
    shell: HostNavShell,
    table: net.bible.sharedui.strings.keyed.StringTable,
    results: List<ReadingResultCollector<*>> = emptyList(),
    onBack: (passThrough: () -> Unit) -> Unit = { passThrough -> passThrough() },
    content: @Composable () -> Unit,
): ReadingNavDeps = ReadingNavDeps(
    host = hostToken,
    windowTitle = table.text("app_name_short"),
    onBack = onBack,
    content = content,
    onKey = { false },
    onScreenTurnedOn = {},
    onScreenTurnedOff = {},
    setWindowTitle = shell::setWindowTitle,
    results = results,
)
