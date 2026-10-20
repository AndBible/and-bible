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
import net.bible.android.control.page.window.WindowChange
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.speak.SpeakTransportServiceImpl
import net.bible.sharedcore.log.nsLog
import net.bible.sharedcore.event.Subscriptions
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.reading.shouldRestorePaneFocusOnDrawerClose
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
import net.bible.android.database.IdType
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
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
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
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.nav.NavResultChannel
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
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
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

/** The English strings, built once, for a host that was not given the app's. */
private val englishStrings: Strings by lazy { iosStrings("en") }

/**
 * The iOS reading host's dependencies: the common [ReadingHostState] and the [ReadingHostDeps] / [ReadingNavDeps]
 * built from Koin. Construct only after [net.bible.service.startup.ReadingAppInit.createWindowRepository] has run,
 * because [hostState] reads the [WindowControl]'s repository.
 *
 * [slots] is the common [ReadingHostSlots] built over the iOS ports ([IosReadingSlotPorts.kt]); [deps] hands its
 * composables to the screen exactly as Android's `ComposeReadingViewHost` does. [strings] is read per composition;
 * [openUrl] opens a URL through UIKit (the Speak sheet's system-settings row goes through it).
 */
@kotlin.native.HiddenFromObjC
class IosReadingHost(
    private val scope: CoroutineScope,
    private val shell: IosHostShell,
    private val koin: Koin,
    private val nightModeState: NightModeState = koin.get(),
    /** A live night-mode source (see [IosNightModeObserver]); null keeps a snapshot of [nightModeState]. */
    private val nightModeSource: State<Boolean>? = null,
    /** A live monochrome source (colour mode other than NORMAL, see [IosNightModeObserver]); null keeps a snapshot. */
    private val monochromeSource: State<Boolean>? = null,
    private val strings: () -> Strings = { englishStrings },
    private val openUrl: (String) -> Unit = ::iosOpenUrl,
    /** The app's `manageLabels` result channel (the label chooser's answer); a private one when the host stands alone. */
    labelResults: NavResultChannel<ManageLabelsResult> = NavResultChannel { },
    /** Applies the document, key and passage choosers' answers to the window that asked; null: answers are not applied. */
    private val readingResults: IosReadingResults? = null,
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
    ) { IosReadingWindows.offsetsChanged() }

    /** The agent log's last measured collapsed height in dp (the offsets need it while the panel is visible). */
    private var agentLogHeightDp = 0f

    /** The navigator every window session and the toolbar use; shares [hostState] and the label result channel. */
    internal val navigator: IosReadingNavigator by lazy {
        IosReadingNavigator(
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
    )

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
        IosWindowPaneMenuPlatform(
            shell = shell,
            windowControl = { windowControl },
            slots = { slots },
            clipboard = koin.get<ClipboardText>(),
            toast = ::toast,
            copiedMessage = { KeyedPaneMenuStrings(IosAppStartup.stringTable).referenceCopiedToClipboard },
            bridgeFor = { id -> if (IosReadingWindows.hasAssembly(id)) IosReadingWindows.bridgeFor(id) else null },
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
            strings = KeyedPaneMenuStrings(IosAppStartup.stringTable),
            prefItems = koin.get<TextDisplayPrefItems>(),
            recentDisplaySettings = { RecentDisplaySettings(settings).sorted },
            globalTextDisplaySettings = { koin.get<GlobalTextDisplaySettingsSource>().current },
            llmConfigured = { false },
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
        iosChooserTarget(koin, IosAppStartup.stringTable, windowControl.activeWindowPageManager, scope)

    /** Android's `ReadingCommands.applyChosenVerse` (non-bookmark arm): a malformed OSIS id only toasts. */
    private fun applyChosenVerse(osisId: String) = activeChooserTarget().applyVerse(osisId)

    private fun formatHistoryTimestamp(epochMillis: Long): String =
        NSDateFormatter().apply { dateFormat = "h:mm a, E d MMM " }
            .stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

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
        platform = IosReadingSearchPlatform(
            currentDocument = { documentControl.currentDocument },
            currentBookName = { koin.get<SearchControl>().currentBookName },
            windowControl = { windowControl },
            linkControl = { koin.get<LinkControl>() },
            fakeBookFactory = { koin.get<FakeBookFactory>() },
        ),
        indexJobs = IosSearchIndexJobs(),
        services = ReadingSearchServices(
            koin.get<BibleSearchService>(), koin.get<EpubSearchService>(),
            koin.get<SearchResultsCache>(), koin.get<SearchIndexService>(),
        ),
        // The search field lives in the toolbar, which iOS has no full-screen mode to hide yet.
        onLeaveFullScreen = {},
    )

    /**
     * Android's `searchController.activeDocumentChanged()` call sites: search mode outlives a window switch and a document
     * swap, so the panel's target document follows them.
     */
    private val searchSubscriptions = Subscriptions().also { subscriptions ->
        subscriptions.add(
            PassageChangeMediator.changes.subscribeOnMain { change ->
                if (change is PageChange.VerseChanged) searchState.controller.activeDocumentChanged()
            },
        )
        subscriptions.add(
            koin.get<WindowStateServiceImpl>().windowChanges.subscribeOnMain { change ->
                if (change is WindowChange.ActiveWindowChanged) searchState.controller.activeDocumentChanged()
            },
        )
    }

    private val gridFactory = GridPassageControllerFactory(
        navigationControl = koin.get(),
        windowControl = windowControl,
        settings = settings,
        workspaceName = { windowControl.windowRepository.name },
        isPortrait = ::iosIsPortrait,
    )

    private val quickActions: IosReadingQuickActions = IosReadingQuickActions(
        { windowControl }, { koin.get<FakeBookFactory>() }, settings, scope, ::toast,
        onToolbarMayHaveChanged = { toolbarHost.refreshToolbar() },
        chooserTarget = ::activeChooserTarget,
    )

    /**
     * Android's `applyWorkspaceChangedResult`: another workspace is loaded, or the current one reloaded when it changed. The
     * outgoing workspace is not saved ([IosReadingQuickActions.switchToWorkspace]): the selector already saved it, and
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
        fullScreen = { false },
        searchState = searchState,
        textDisplaySettingsService = { koin.get<TextDisplaySettingsService>() },
        scope = scope,
        imagePicker = IosImagePicker(),
        thumbnailFor = { null },
        speakSettingsService = koin.get<SpeakSettingsService>(),
        speakPlatform = IosSpeakSettingsPlatform(openUrl),
        gridPassages = gridFactory,
        workspaceService = { koin.get<WorkspaceService>() },
        readingProgressService = { koin.get<ReadingProgressService>() },
        quickActions = quickActions,
        keyChooserPage = IosReadingKeyChooserPage { windowControl },
        navigator = navigator,
        textShare = IosTextShare(),
        clipboard = koin.get<ClipboardText>(),
        toast = ::toast,
        workspaceName = { windowControl.windowRepository.name },
        activeWindowPosition = { windowControl.activeWindowPosition },
        currentDocumentInitials = { documentControl.currentDocument?.initials },
    )

    /**
     * The toolbar, overflow menu and drawer over the iOS controls ([IosReadingToolbarHost]); the common decision logic
     * and the menus built from it follow.
     */
    internal val toolbarHost: IosReadingToolbarHost = IosReadingToolbarHost(
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
        chooserRequests = readingResults ?: ChooserRequests.None,
        externalLinks = koin.get<ExternalLinks>(),
        windowStateService = { koin.get<WindowStateServiceImpl>() },
        fakeBooks = { koin.get<FakeBookFactory>() },
    )

    private val menuStrings = { KeyedReadingMenuStrings(IosAppStartup.stringTable) }

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

    /** Ends the slots' own background work (the search index feed); call before cancelling [scope]. */
    fun dispose() {
        drawerWatch.cancel()
        searchSubscriptions.cancelAll()
        slots.dispose()
    }

    /**
     * The toolbar callbacks: all sixteen do what Android's `ReadingCommands`/`ComposeReadingViewHost` toolbar callbacks
     * do ([ReadingToolbarCommands]); none is a logged pending. The gaps inside them (full screen, auto assign labels,
     * LLM actions, some drawer rows) are named on [IosReadingToolbarHost] and `ReadingDrawerMenu.OMITTED_ROWS`.
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

    fun deps(icons: ReadingToolbarIcons, paneIcons: IosPaneIcons? = null): ReadingHostDeps {
        // The state service only refreshes on events; the first snapshot is taken here, once the repository exists.
        toolbarHost.refreshToolbar()
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

    private fun buildDeps(icons: ReadingToolbarIcons, paneIcons: IosPaneIcons?): ReadingHostDeps = ReadingHostDeps(
        state = hostState,
        controller = readingViewController,
        toolbar = koin.get<ToolbarStateService>().toolbar,
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
                derivedStateOf { speakBarVisible(fullScreen = false, transportVisible = speakState.value.visible) }
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
        onSearchSheetOffsetsChanged = { visible, heightPx -> offsets.setSearchSheet(visible, heightPx, iosScreenScale()) },
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
    )

    fun navDeps(content: @Composable () -> Unit): ReadingNavDeps =
        iosReadingNavDeps(this, shell, IosAppStartup.stringTable, readingResults?.collectors() ?: emptyList(), content)
}

/** The reading destination's [ReadingNavDeps]; [hostToken] is the identity the destination registers under. */
@kotlin.native.HiddenFromObjC
fun iosReadingNavDeps(
    hostToken: Any,
    shell: IosHostShell,
    table: net.bible.sharedui.strings.keyed.StringTable,
    results: List<ReadingResultCollector<*>> = emptyList(),
    content: @Composable () -> Unit,
): ReadingNavDeps = ReadingNavDeps(
    host = hostToken,
    windowTitle = table.text("app_name_short"),
    onBack = { passThrough -> passThrough() },
    content = content,
    onKey = { false },
    onScreenTurnedOn = {},
    onScreenTurnedOff = {},
    setWindowTitle = shell::setWindowTitle,
    results = results,
)
