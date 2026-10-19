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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import net.bible.android.view.activity.page.screen.speakBarVisible
import kotlinx.coroutines.CoroutineScope
import net.bible.android.control.document.DocumentControl
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.link.LinkControl
import net.bible.android.control.search.SearchControl
import net.bible.android.view.activity.navigation.GridPassageControllerFactory
import net.bible.service.common.RecentDocumentsStore
import net.bible.service.download.FakeBookFactory
import net.bible.service.history.HistoryManager
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedcore.platform.resolveDisableAnimations
import net.bible.sharedcore.search.BibleSearchService
import net.bible.sharedcore.search.EpubSearchService
import net.bible.sharedcore.search.SearchIndexService
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.workspaces.WorkspaceService
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowStateService
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedui.assembly.IosAppStartup
import net.bible.sharedui.ios.IosPending
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.ExternalLinks
import net.bible.sharedui.ios.app.IosHostShell
import net.bible.sharedui.ios.ports.IosImagePicker
import net.bible.sharedui.ios.ports.IosTextShare
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.host.ReadingHostDeps
import net.bible.sharedui.reading.host.ReadingHostHooks
import net.bible.sharedui.reading.host.ReadingHostState
import net.bible.sharedui.reading.host.slots.ReadingHostSlots
import net.bible.sharedui.reading.host.slots.ReadingSearchServices
import net.bible.sharedui.reading.host.slots.ReadingSearchState
import net.bible.sharedui.reading.nav.ReadingNavDeps
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.webview.PendingIosReadingNavigator
import org.koin.core.Koin
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSince1970
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.VerseFactory

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
    private val strings: () -> Strings = { englishStrings },
    private val openUrl: (String) -> Unit = ::iosOpenUrl,
) {
    private val windowControl: WindowControl = koin.get()
    private val settings: AppSettings = koin.get()
    private val documentControl: DocumentControl = koin.get()

    val hostState: ReadingHostState = ReadingHostState(
        scope = scope,
        settings = settings,
        windowControl = windowControl,
        historyManager = koin.get<HistoryManager>(),
        documentControl = documentControl,
        hooks = ReadingHostHooks(
            closeSpeakSheet = { slots.speakSheet.close() },
            closeTextSettingsEditor = { slots.textSettingsEditor.close() },
            openCompare = { range -> koin.get<LinkControl>().openCompare(range) },
            applyChosenVerse = ::applyChosenVerse,
            formatHistoryTimestamp = { createdAt -> formatHistoryTimestamp(createdAt.getTime()) },
            recentDocumentInitials = { koin.get<RecentDocumentsStore>().read() },
        ),
    )

    /** Android's `ReadingCommands.applyChosenVerse` (non-bookmark arm): a malformed OSIS id only toasts. */
    private fun applyChosenVerse(osisId: String) {
        val verse = try {
            VerseFactory.fromString(koin.get<net.bible.android.control.navigation.NavigationControl>().versification, osisId)!!
        } catch (e: NoSuchVerseException) {
            koin.get<UserNotifier>().showInfo(IosAppStartup.stringTable.text("verse_not_found"), false)
            return
        }
        windowControl.activeWindowPageManager.currentPage.setKey(verse, true)
    }

    private fun formatHistoryTimestamp(epochMillis: Long): String =
        NSDateFormatter().apply { dateFormat = "h:mm a, E d MMM " }
            .stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

    private val agentSessionService: AgentSessionService = koin.get()

    private val agentLog = AgentLogController(
        agentSessionService,
        scope,
        onCompletedToast = { koin.get<UserNotifier>().showInfo(strings().aiTaskCompleted, false) },
        onOpenRawLog = { shell.navigate(NavRoutes.rawLlmLog(workspaceId = agentSessionService.currentWorkspaceId())) },
    )

    private val speakTransport = SpeakTransportController(
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

    private val gridFactory = GridPassageControllerFactory(
        navigationControl = koin.get(),
        windowControl = windowControl,
        settings = settings,
        workspaceName = { windowControl.windowRepository.name },
        isPortrait = ::iosIsPortrait,
    )

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
        quickActions = IosReadingQuickActions({ windowControl }, { koin.get<FakeBookFactory>() }, settings, scope),
        keyChooserPage = IosReadingKeyChooserPage { windowControl },
        navigator = PendingIosReadingNavigator,
        textShare = IosTextShare(),
        clipboard = koin.get<ClipboardText>(),
        toast = { koin.get<UserNotifier>().showInfo(it, false) },
        workspaceName = { windowControl.windowRepository.name },
        activeWindowPosition = { windowControl.activeWindowPosition },
        currentDocumentInitials = { documentControl.currentDocument?.initials },
    )

    /** Ends the slots' own background work (the search index feed); call before cancelling [scope]. */
    fun dispose() = slots.dispose()

    private fun callbacks(): ReadingToolbarCallbacks {
        val navigator = PendingIosReadingNavigator
        return ReadingToolbarCallbacks(
            onHome = { IosPending.log("ReadingToolbar.onHome") },
            onTitleTap = {
                // A tap before the repository holds a window must not throw.
                val page = try {
                    windowControl.activeWindowPageManager.currentPage
                } catch (e: Exception) {
                    null
                }
                if (page == null) IosPending.log("ReadingToolbar.onTitleTap") else navigator.startKeyChooser(page)
            },
            onTitleLongPress = { IosPending.log("ReadingToolbar.onTitleLongPress") },
            onTitleFlingVertical = { IosPending.log("ReadingToolbar.onTitleFlingVertical") },
            onTitleFlingHorizontal = { IosPending.log("ReadingToolbar.onTitleFlingHorizontal") },
            onBible = { IosPending.log("ReadingToolbar.onBible") },
            onBibleLong = { IosPending.log("ReadingToolbar.onBibleLong") },
            onCommentary = { IosPending.log("ReadingToolbar.onCommentary") },
            onCommentaryLong = { IosPending.log("ReadingToolbar.onCommentaryLong") },
            onStrongs = { IosPending.log("ReadingToolbar.onStrongs") },
            onStrongsLong = { IosPending.log("ReadingToolbar.onStrongsLong") },
            onSearch = { navigator.openSearch(null) },
            onSpeak = { IosPending.log("ReadingToolbar.onSpeak") },
            onSpeakLong = { IosPending.log("ReadingToolbar.onSpeakLong") },
            onWorkspace = { IosPending.log("ReadingToolbar.onWorkspace") },
            onOverflow = { IosPending.log("ReadingToolbar.onOverflow") },
        )
    }

    fun deps(icons: ReadingToolbarIcons): ReadingHostDeps = ReadingHostDeps(
        state = hostState,
        controller = ReadingViewController(koin.get<WindowStateService>(), koin.get<WindowCommands>()),
        toolbarCallbacks = callbacks(),
        toolbarIcons = { icons },
        nightMode = nightModeSource ?: mutableStateOf(nightModeState.nightMode),
        disableAnimations = { settings.resolveDisableAnimations(false) },
        agentLogSlot = { applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured ->
            slots.AgentLog(applyNavBarInset, maxHeightDp, collapsedDp, onCollapsedHeightMeasured)
        },
        speakBarSlot = { applyNavBarInset -> slots.SpeakBar(applyNavBarInset) },
        agentLogVisible = {
            val agentLogState = agentLog.state.collectAsState()
            val visible = remember { derivedStateOf { agentLogState.value.visible } }
            visible.value
        },
        speakBarVisible = {
            val speakState = speakTransport.state.collectAsState()
            val visible = remember {
                derivedStateOf { speakBarVisible(fullScreen = false, transportVisible = speakState.value.visible) }
            }
            visible.value
        },
        searchSheetSlot = { slots.SearchSheet() },
        searchSettingsSlot = { slots.SearchSettings() },
        textSettingsEditorSlot = { slots.TextSettingsEditor() },
        speakSettingsSlot = { slots.SpeakSettings() },
        quickSheetContent = { sheet -> slots.QuickSheet(sheet) },
        readingDialogSlot = { slots.ReadingDialog() },
    )

    fun navDeps(content: @Composable () -> Unit): ReadingNavDeps = iosReadingNavDeps(this, shell, IosAppStartup.stringTable, content)
}

/** The reading destination's [ReadingNavDeps]; [hostToken] is the identity the destination registers under. */
@kotlin.native.HiddenFromObjC
fun iosReadingNavDeps(
    hostToken: Any,
    shell: IosHostShell,
    table: net.bible.sharedui.strings.keyed.StringTable,
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
    results = emptyList(),
)
