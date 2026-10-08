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
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
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
import net.bible.sharedcore.ai.reading.ReadingLlmDialogState
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.history.HistoryController
import net.bible.sharedcore.history.HistoryEntry
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.navigation.DocTypeFilter
import net.bible.sharedcore.navigation.DocumentQuickTab
import net.bible.sharedcore.navigation.DocumentQuickTabs
import net.bible.sharedcore.navigation.DocumentSheetScope
import net.bible.sharedcore.navigation.buildDocumentQuickTabs
import net.bible.sharedcore.navigation.GridChoosePassageController
import net.bible.sharedcore.navigation.GridStep
import net.bible.sharedcore.navigation.KeyRow
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.reading.DrawerCloseLatch
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.reading.KeyChooserKind
import net.bible.sharedcore.reading.KeyChooserPage
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.PaneButtonAction
import net.bible.sharedcore.reading.ReadingOverlay
import net.bible.sharedcore.reading.ReadingOverlayExclusion
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ShareVersesOptions
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.reading.bibleReferenceOverlayVisible
import net.bible.sharedcore.reading.paneButtonDragAction
import net.bible.sharedcore.reading.paneButtonFadeMillis
import net.bible.sharedcore.reading.paneButtonHiddenAlpha
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
import net.bible.sharedcore.search.SearchQueryController
import net.bible.sharedcore.search.SearchRequest
import net.bible.sharedcore.search.SearchResultsCache
import net.bible.sharedcore.search.SearchResultsController
import net.bible.sharedcore.search.SearchType
import net.bible.sharedcore.search.forEpubOf
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
import net.bible.sharedcore.speak.SpeakTransportDialog
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.speak.sleepTimerSelectionFor
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedcore.window.shouldShowPinIndicator
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
import net.bible.sharedui.reading.BibleReferenceOverlay
import net.bible.sharedui.navigation.DocumentQuickContent
import net.bible.sharedui.navigation.GridChoosePassageContent
import net.bible.sharedui.navigation.GridOptionsOverflow
import net.bible.sharedui.navigation.KeyListBody
import net.bible.sharedui.reading.ChooseSpeakBookmarkDialog
import net.bible.sharedui.reading.ReadingDrawerContent
import net.bible.sharedui.reading.ReadingDrawerWidth
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.reading.readingRailInsetPadding
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.ShareVersesSheet
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.WindowButton
import net.bible.sharedui.reading.WindowButtonMode
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.search.BibleResultsActions
import net.bible.sharedui.search.BibleSearchSettings
import net.bible.sharedui.search.EpubSearchSettings
import net.bible.sharedui.search.SearchIndexPanel
import net.bible.sharedui.search.SearchSettingsSheet
import net.bible.sharedui.search.SearchSheetContent
import net.bible.sharedui.search.readingSheetInsetPadding
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
import net.bible.sharedui.strings.Strings
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

/** Vertical drag distance on the pane ☰ button that counts as swipe-to-maximise/minimise (Plan B
 * Task 5, §4/§0.1: iOS uses a 28pt threshold; classic's own fling-velocity gesture has no direct
 * dp equivalent, so this Compose port matches the iOS distance threshold instead of reproducing
 * classic's `ViewConfiguration`-based fling detector). */
private val PaneButtonDragThresholdDp = 28.dp

/**
 * Reload-generation counter for [ComposeReadingViewHost]: bumped by `rebuild()` to force a full
 * dispose+recreate of the pane subtree. Needed because each pane is
 * `key(window.id) { AndroidView(factory = { ... }) }` — the factory runs once per window id and
 * is never re-invoked by a plain recomposition. When [MainBibleActivity.currentWorkspaceId]'s
 * setter (or a database restore, `DatabaseContainer.databaseRestored`) reloads the SAME workspace, it does
 * `documentViewManager.removeView()` -> `bibleViewFactory.clear()` (destroys every cached
 * [net.bible.android.view.activity.page.BibleView]) -> `windowRepository.loadFromDb()` ->
 * `documentViewManager.buildView(forceUpdate = true)`; the window ids are unchanged, so without
 * this generation bump Compose would keep the existing `key(window.id)` nodes and never re-run
 * `AndroidView`'s factory, leaving the panes showing destroyed WebViews. Mirrors classic's
 * `SplitBibleArea.update(forceUpdate = true)` recreate.
 *
 * Kept as its own tiny, framework-free (no [KoinComponent]/activity coupling) holder so the
 * "`rebuild()` bumps the counter" behavior is unit-testable without booting a full
 * [MainBibleActivity] or Koin context — see `ComposeReadingViewGenerationTest`.
 */
class ComposeReadingViewGeneration {
    private val mutableState = mutableIntStateOf(0)
    val state: State<Int> get() = mutableState
    fun rebuild() { mutableState.intValue++ }
}

/**
 * Makes the rail's plain (non-observable) per-window labels recompose when they may have changed.
 *
 * `windowLabelFor`/`windowTopLabelFor` read `pageManager` directly, so Compose has nothing to
 * observe and skips the rail while its inputs (layout model + stable lambdas) are unchanged.
 * Routing each label read through [observed] subscribes the calling composition scope to one
 * shared tick; [invalidate] (driven by the host's passage/verse events) bumps it, so exactly the
 * label sites that read it recompose, for every window, synchronised ones included. The window
 * icon (`windowIconFor`) is not wrapped: it is read in the same rail composition scope as the
 * labels, so it is re-evaluated whenever they are.
 *
 * The bump relies on the host's `PageChange.VerseChanged` / `PageChange.BibleVerseChanged` handlers
 * being reached: a document or key change reaches the rail only because `CurrentPageManager` /
 * `PassageChangeMediator` emit one of them. A mutation that emits neither leaves the labels stale;
 * `railLabelsFollowARealDocumentChange` pins the document-swap path.
 *
 * Framework-free like [ComposeReadingViewGeneration], so it is unit-testable without a composition.
 */
class WindowLabelFreshness {
    private val tick = mutableIntStateOf(0)

    /** Reads [read] after subscribing the current composition scope to [invalidate]. */
    fun <T> observed(read: () -> T): T {
        tick.intValue
        return read()
    }

    fun invalidate() { tick.intValue++ }
}

/**
 * Fans a "classic said this may have changed" signal out to the Compose reading view's state
 * (pre-A/B state-freshness spec §1 P3).
 *
 * Every Compose state bridge subscribes to a *guessed set* of owner `Events`
 * streams and rebuilds its snapshot from the domain, so a mutation that posts none of them leaves
 * the UI stale — `ToolbarStateServiceImpl` subscribes to 5 events while classic calls
 * `MainBibleActivity.updateActions()` from 12 places, only 3 of which coincide with one. Rather
 * than guess more events, the classic imperative refresh points push here, mirroring what Batch
 * Z-early A6 already does one line earlier in `updateActions` for the drawer
 * (`composeReadingViewHost?.rebuildDrawer(...)`). The domain-notifier inversion that removes the
 * guessing altogether is Batch Z-late work (spec §5), after the classic files are deleted.
 *
 * [rebuildComposition] additionally bumps [ComposeReadingViewGeneration], which recreates the pane
 * subtree — needed only where the host re-reads values *inside* its composition (the three
 * settings read in `mountComposeView`), i.e. the return-from-Settings path. It is off by default
 * because recreating the subtree re-runs every pane's `AndroidView` factory.
 *
 * Kept as its own framework-free holder (like [ComposeReadingViewGeneration] above) so the fan-out
 * is unit-testable without a [MainBibleActivity] or Koin context — see `HostedStateRefresherTest`.
 */
class HostedStateRefresher(
    private val toolbar: ToolbarStateService,
    private val generation: ComposeReadingViewGeneration,
) {
    fun refresh(rebuildComposition: Boolean = false) {
        toolbar.refresh()
        if (rebuildComposition) generation.rebuild()
    }
}

/**
 * Whether the reading view's Speak transport bar should be shown — the Compose counterpart of
 * classic `MainBibleActivity.updateBottomBars()`'s `if (isFullScreen || !transportBarVisible)`
 * animate-out branch.
 *
 * [transportVisible] arrives through `SpeakTransportServiceImpl.setTransportVisible`, which the
 * `transportBarVisible` **setter** calls with the raw backing field — its getter's
 * `if (isFullScreen) false` mask is NOT applied before the call, and `toggleFullScreen()` emits
 * only `fullScreenChanged`. So without re-applying the fullscreen half here, the bar stayed on
 * screen in fullscreen on the Compose path while classic animated it away (pre-A/B spec §1 P3).
 *
 * A pure function so it is unit-testable — see `SpeakBarVisibilityTest`.
 */
internal fun speakBarVisible(fullScreen: Boolean, transportVisible: Boolean): Boolean =
    !fullScreen && transportVisible

/**
 * The `menuWindowId`/`paneMenuWindowId` a surface should receive: the open window's id only when
 * the open menu is anchored ([openAnchor]) to THAT [surface], else `null` — the pane overlay and the
 * rail (A/B batch 3, F5b) each call this with their own [surface] so exactly one of the two
 * `WindowPaneMenu` instances ever reports itself expanded, keeping "both menus open at once"
 * structurally unrepresentable rather than merely avoided. A pure function, mirroring
 * [speakBarVisible] above, so the gate itself — not just the `openPaneMenu` bookkeeping that feeds
 * it — is unit-testable without a `ComposeTestRule` (this repo's `:app` unit tests have none); see
 * `MenuWindowIdForTest`.
 */
internal fun menuWindowIdFor(surface: PaneMenuAnchor, openAnchor: PaneMenuAnchor, openWindowId: String?): String? =
    if (openAnchor == surface) openWindowId else null

/**
 * Task 27 (platform-dialog removal, run 3) fix round 1: [ReadHistoryEntry] → [ReadHistoryRow]
 * mapping for the reading view's per-chapter read-history sheet
 * (`QuickSheetSlot`'s `is ReadingQuickSheet.ReadHistory ->` branch), extracted as a pure function —
 * mirroring [speakBarVisible]/[menuWindowIdFor] above — so it is unit-testable without
 * Robolectric/Compose; see `ChapterReadHistoryRowTest`. Classic `ReadHistoryDialog.showForChapter`
 * used `showChapterPerRow = false`: `"$date $time"` / version, no chapter reference (the chapter is
 * already named in the sheet's title, unlike the book/day history screens' multi-chapter list,
 * which show a chapter reference per row instead). [date]/[time] arrive ALREADY formatted —
 * `ReadingProgressServiceImpl.formatEntryDate`/`formatEntryTime` are Android `DateFormat` calls and
 * so not themselves portable — this function only assembles the row from them.
 */
internal fun chapterReadHistoryRow(
    entry: ReadHistoryEntry,
    date: String,
    time: String,
    versionUnknownText: String,
): ReadHistoryRow {
    val version = entry.bookInitials.ifEmpty { versionUnknownText }
    return ReadHistoryRow(id = entry.id, primary = "$date $time", secondary = version)
}

/**
 * Task 27 (platform-dialog removal, run 3) fix round 2: the `onApplyDeletes` callback
 * `QuickSheetSlot`'s `is ReadingQuickSheet.ReadHistory ->` branch wires into `AbReadHistorySheet`,
 * extracted as its own function — mirroring [chapterReadHistoryRow] above — so it is unit-testable
 * with a fake [ReadingProgressService] instead of only being exercisable by re-implementing its
 * `ids.isNotEmpty()` guard inside a test harness (review round 1's gap, named again in round 2: the
 * guard AND the `cycle` threading from the `LaunchedEffect`'s load were both unverified against the
 * real production lambda). `ComposeReadingViewHost` now calls this function VERBATIM — see
 * `ReadHistoryApplyDeletesTest`.
 *
 * [service] is typed to the portable [ReadingProgressService] interface, not
 * `ReadingProgressServiceImpl`, specifically so it is fakeable without Room/Robolectric — the same
 * reason `:sharedCore`'s own `ReadingProgressControllerTest.Fake` exists. [cycle] is captured once,
 * at assembly time (the `LaunchedEffect`'s `activeCycle`), not re-read per call — matching classic
 * `ReadHistoryDialog.applyPendingDeletes`, which closed over the SAME `cycle` its `loadAndShow` had
 * already fetched, not a re-queried one.
 */
internal fun readHistoryApplyDeletes(
    coroutineScope: CoroutineScope,
    service: ReadingProgressService,
    cycle: Int,
): (List<String>) -> Unit = { ids ->
    if (ids.isNotEmpty()) {
        coroutineScope.launch { service.deleteReadHistoryEntries(ids, cycle) }
    }
}

/**
 * Auto-hide state for the pane overlay's floating ☰ button (Batch 12b follow-on Plan B Task 5) —
 * the Compose port of classic `SplitBibleArea.resetTouchTimer`/`toggleWindowButtonVisibility`
 * (`screen/SplitBibleArea.kt:511-575`), hoisted to a host-owned field (design spec §9: "hoist it
 * ... not per-recomposition local state") rather than remembered inside the composable, so the 2s
 * hide countdown survives recomposition. Kept as its own tiny, framework-free holder — mirroring
 * [ComposeReadingViewGeneration] — so `ComposeReadingViewHostTest` can assert it without booting a
 * full [MainBibleActivity]/Koin context.
 *
 * [visible] starts `true` (buttons shown on first render, mirroring classic's initial
 * `buttonsVisible = true`, `SplitBibleArea.kt:149`). [onTouch] — wired to
 * [ComposeReadingViewHost.onBibleViewTouched], and also called by
 * [ComposeReadingViewHost.openPaneMenu] (mirroring classic `showPopupMenu`'s
 * `timerTask?.cancel(); toggleWindowButtonVisibility(true)`, `SplitBibleArea.kt:731-732`, so a menu
 * opened while auto-hidden still renders) — sets [visible] `true` and bumps [touchTick] so a
 * `LaunchedEffect(touchTick)` in the composable can restart its 2s hide countdown. [onHideTimeout]
 * (called by that effect after the delay elapses) sets [visible] `false`. Because Compose
 * cancels/reruns a `LaunchedEffect` whenever its key changes, a touch mid-countdown discards the
 * stale timer automatically — no manual `TimerTask.cancel()` bookkeeping is needed here, unlike
 * classic.
 */
class WindowButtonsVisibility {
    private val mutableVisible = mutableStateOf(true)
    private val mutableTouchTick = mutableIntStateOf(0)
    val visible: State<Boolean> get() = mutableVisible
    val touchTick: State<Int> get() = mutableTouchTick

    fun onTouch() {
        mutableVisible.value = true
        mutableTouchTick.intValue++
    }

    fun onHideTimeout() { mutableVisible.value = false }
}

/**
 * Which surface a per-window (☰) pane menu is anchored to (A/B batch 3, F5). Classic anchors its
 * popup to the view that was pressed; the port originally composed the menu only inside the pane
 * overlay, so a rail long-press rendered it at the pane's floating button instead of at the rail
 * tab under the finger.
 */
enum class PaneMenuAnchor { Pane, Rail }

/**
 * Platform-dialog removal Task 18: the reading view's own dialogs, held as ONE host-owned state
 * (`ComposeReadingViewHost.readingDialog`) and rendered through ONE host-private slot
 * (`ReadingDialogSlot`) -- the same "shape 2" pattern `quickSheet`/`QuickSheetSlot` already use
 * (Appendix -- site owners, Q1), rather than the raw-`StateFlow`-plus-many-callbacks shape
 * `readingLlmDialogs` needs (nothing outside this host observes either of these).
 *
 * **Not a [net.bible.sharedcore.reading.ReadingOverlay]**: both arms are plain `AlertDialog`-shaped
 * (via `AbMessageDialog`/`AbConfirmDialog`), and a dialog over a sheet is fine
 * (`ReadingOverlayExclusion`'s kdoc) -- so opening one never needs to close a sheet, and no
 * exclusion wiring/enum member is needed.
 */
sealed interface ReadingDialog {
    /** `BibleJavascriptInterface.helpDialog`/`.helpBookmarks`'s plain-text or HTML help body -- a
     *  single OK button, no negative action. */
    data class Help(val title: String?, val html: String) : ReadingDialog

    /** `BibleJavascriptInterface.deleteMyDocumentPage`'s confirm; [onConfirm] is the caller's
     *  deletion (+ window bookkeeping), captured at open time -- only the QUESTION lives here. */
    data class ConfirmDeleteDocumentPage(val onConfirm: () -> Unit) : ReadingDialog
}

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
    private val windowState: WindowStateServiceImpl by inject()
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
    private val speakControl: SpeakControl by inject()
    private val linkControl: LinkControl by inject()

    /** Owns [readingLlmDialogs]' coroutine work (dialog open/execute/dismiss). Cancelled in
     *  [dispose] — one host per activity (re-)creation, mirroring the [subscriptions]
     *  lifecycle right below. */
    private val hostScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
    private fun closeOverlay(overlay: ReadingOverlay) {
        when (overlay) {
            ReadingOverlay.Llm -> when (readingLlmDialogs.state.value.dialog) {
                is ReadingLlmDialog.PromptSelector, is ReadingLlmDialog.ModelSelection -> readingLlmDialogs.dismiss()
                is ReadingLlmDialog.None, is ReadingLlmDialog.SpecifyBeforeRun, is ReadingLlmDialog.Regenerate -> Unit
            }
            ReadingOverlay.SpeakSheet -> speakSheet.close()
            ReadingOverlay.TextSettingsEditor -> textSettingsEditor.close()
            ReadingOverlay.QuickSheet -> closeQuickSheet()
        }
    }

    /**
     * Close every modal reading-view overlay (spec Task 5): called from `AppDialogOverlay`'s
     * `onSheetOpening` when an app-wide dialog/sheet is about to be shown, so it never stacks under
     * (or is stacked under by) one of these.
     */
    internal fun closeModalOverlays() {
        ReadingOverlay.entries.forEach(::closeOverlay)
    }

    /**
     * Platform-dialog removal Task 18: the reading view's own dialogs (see [ReadingDialog]'s kdoc
     * for why this is not a [ReadingOverlay]). ONE state, rendered through the one host-private
     * slot [ReadingDialogSlot] — the same shape as [quickSheet]/[QuickSheetSlot].
     */
    internal val readingDialog = mutableStateOf<ReadingDialog?>(null)

    /** `BibleJavascriptInterface.helpDialog`/`.helpBookmarks`'s entry point. */
    internal fun showHelp(title: String?, html: String) {
        readingDialog.value = ReadingDialog.Help(title, html)
    }

    /** `BibleJavascriptInterface.deleteMyDocumentPage`'s entry point — see [ReadingDialog
     *  .ConfirmDeleteDocumentPage]'s kdoc for why [onConfirm] is the caller's job. */
    internal fun showDeleteDocumentPageConfirm(onConfirm: () -> Unit) {
        readingDialog.value = ReadingDialog.ConfirmDeleteDocumentPage(onConfirm)
    }

    /**
     * Runs the showing dialog's confirm action (if it has one) and clears it. A no-op when nothing
     * is showing, so a second/duplicate confirm (e.g. a fast double-tap) never re-runs the deletion.
     */
    internal fun confirmReadingDialog() {
        val dialog = readingDialog.value ?: return
        readingDialog.value = null
        if (dialog is ReadingDialog.ConfirmDeleteDocumentPage) dialog.onConfirm()
    }

    /** Dismisses whatever [ReadingDialog] is showing, running no action. A no-op when nothing is
     *  showing. */
    internal fun dismissReadingDialog() {
        readingDialog.value = null
    }

    /**
     * Round 15b: which quick sheet is open over the reading view, if any. ONE state for all four
     * (see [ReadingQuickSheet]) — which is why the exclusion rule needs one member and this file
     * one slot.
     */
    internal val quickSheet = mutableStateOf<ReadingQuickSheet?>(null)

    /**
     * The verse an out-of-process caller — today only `BibleJavascriptInterface.refChooserDialog`,
     * via [openVerseChooserSheetForResult] — is waiting for, or `null` when the Grid sheet is
     * simply navigating the reading view as usual.
     *
     * A plain `var`, not snapshot state, for the same reason as [keyChooserKeys]: it is written
     * before the [quickSheet] state that triggers the composition, and nothing ever recomposes off
     * it. Every exit from the sheet MUST resolve it — a request left pending leaks a JS promise for
     * the life of the page — which is why both [closeQuickSheet] and [showQuickSheet] abandon it.
     */
    private var pendingChosenVerse: CompletableDeferred<String?>? = null

    /**
     * Open the Grid quick sheet to pick ONE verse and hand it back, instead of navigating to it.
     *
     * nav-graph slice 7 §6.3: this is what `refChooserDialog` calls now, replacing a full-screen
     * `Screen.GridChoosePassageBook` round-trip through `awaitIntent`. Two things the Intent forced
     * are forced here instead — `navigateToVerse` (spec §6.1.1's trap: the ordinary sheet reads a
     * preference that is off by default, and a reference chooser that stops at chapter level cannot
     * answer the call) and scripture-only books, which the Grid arm already hard-codes because both
     * of its other callers want it.
     *
     * The returned [CompletableDeferred] completes with the chosen verse's osisID, or with `null`
     * if the sheet is dismissed or superseded.
     */
    internal fun openVerseChooserSheetForResult(): CompletableDeferred<String?> {
        val pending = CompletableDeferred<String?>()
        // Claim the slot AFTER opening: showQuickSheet abandons whatever request was outstanding.
        showQuickSheet(ReadingQuickSheet.KeyChooser(KeyChooserKind.Grid, navigateToVerse = true))
        pendingChosenVerse = pending
        return pending
    }

    /**
     * The Grid sheet's one exit with a verse in hand, shared by both of its openings: it answers an
     * outstanding [openVerseChooserSheetForResult] request if there is one, and otherwise navigates
     * the reading view through Task 7's single parse-and-apply path. Taking ownership of the pending
     * request BEFORE closing is what stops [closeQuickSheet]'s abandon from resolving it with `null`.
     */
    internal fun onGridPassageChosen(osisId: String) {
        val pending = pendingChosenVerse
        pendingChosenVerse = null
        closeQuickSheet()
        if (pending != null) pending.complete(osisId) else activity.readingCommands.applyChosenVerse(osisId)
    }

    /** Answer an outstanding verse request with "no verse"; a no-op when there is none. */
    private fun abandonPendingChosenVerse() {
        val pending = pendingChosenVerse ?: return
        pendingChosenVerse = null
        pending.complete(null)
    }

    /** Open a quick sheet, closing every other modal overlay first (spec §4.1). */
    internal fun showQuickSheet(sheet: ReadingQuickSheet) {
        abandonPendingChosenVerse()
        ReadingOverlayExclusion.closedBy(ReadingOverlay.QuickSheet).forEach(::closeOverlay)
        quickSheet.value = sheet
    }

    internal fun closeQuickSheet() {
        quickSheet.value = null
        abandonPendingChosenVerse()
    }

    /** Round 15b: the reading view's History list as a quick sheet (spec §4.3). */
    internal fun showHistorySheet() = showQuickSheet(ReadingQuickSheet.History)

    private val workspaceService: WorkspaceService by inject()

    /** Round 15b: the workspace QUICK switch (spec §4.4). The full selector is the footer row. */
    internal fun showWorkspaceSheet() = showQuickSheet(ReadingQuickSheet.Workspaces)

    private val downloadControl: DownloadControl by inject()

    /** Round 15b: the document QUICK switch (spec §4.5). ChooseDocument is the footer row. */
    internal fun showDocumentSheet() = showQuickSheet(ReadingQuickSheet.Documents())

    /**
     * Task 26 (platform-dialog removal, run 3): the verse-share sheet's toggle state, loaded once
     * from prefs and kept live for this host's whole life. [updateShareVersesOptions] is the only
     * writer — every switch in [ShareVersesSheet] calls it, which both updates this state (so the
     * preview redraws) and persists it (so it survives a reopen, exactly as the classic
     * `ShareWidget`'s per-click `CommonUtils.settings.setBoolean(...)` did).
     */
    internal var shareVersesOptions by mutableStateOf(loadShareVersesOptions())

    /**
     * `BibleView`'s "Share" selection action and `BibleJavascriptInterface`'s two share entry
     * points (bookmark share, JS selection share) all resolve a [ShareVersesInput] via
     * `SwordContentFacade.buildShareVersesInput` and call this.
     */
    internal fun showShareSheet(input: ShareVersesInput) = showQuickSheet(ReadingQuickSheet.Share(input))

    internal fun updateShareVersesOptions(options: ShareVersesOptions) {
        shareVersesOptions = options
        persistShareVersesOptions(options)
    }

    private val readingProgressService: ReadingProgressServiceImpl by inject()

    /**
     * Task 27 (platform-dialog removal, run 3): the reading view's per-chapter read-history sheet.
     * `BibleJavascriptInterface.openChapterReadHistory` is the only caller — it replaces classic
     * `ReadHistoryDialog.showForChapter`, which it already resolves the tapped verse's KJV book/
     * chapter for.
     */
    internal fun showReadHistorySheet(bookId: String, chapter: Int) =
        showQuickSheet(ReadingQuickSheet.ReadHistory(bookId, chapter))

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
     * `internal`, not private lambdas inlined at the `QuickSheetSlot` call site, purely so
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
    internal fun buildDocumentQuickTabsForHost(scope: DocumentSheetScope): DocumentQuickTabs {
        val books = SwordDocumentFacade.documents +
            FakeBookFactory.pseudoDocuments.filterNot { it.hideFromSelector }
        val mapper = DocRowMapper(downloadControl, books)
        val rows = books.map { mapper.toDocRow(it) }
        // `initials`, NOT `osisID`: DocRow.docId is the book's initials and so is the MRU's key —
        // only the document SEARCH path keys on osisId. The two collapse to the same value for most
        // real modules, so getting this wrong would show up only for the modules where they differ.
        // The This-verse tab follows the sheet's scope: Bibles for the Bible sheet, commentaries for
        // the Commentary sheet, both for the title sheet.
        val forVerseBooks = when (scope) {
            DocumentSheetScope.BIBLE -> documentControl.biblesForVerse
            DocumentSheetScope.COMMENTARY -> documentControl.commentariesForVerse
            DocumentSheetScope.ALL -> documentControl.biblesForVerse + documentControl.commentariesForVerse
        }
        val forVerseIds = forVerseBooks.map { it.initials }.toSet()
        // The two keys ChooseDocument itself persists (its sticky-language seam and its type-filter
        // spinner), read here so the "Last filter" tab reproduces what the user last looked at.
        // KNOWN: `selected_document_filter_no` is written by the Download destination too
        // (`NavHostComposeActivity.persistTypeFilter`), so "Last filter" can reflect a filter the
        // user last set on the DOWNLOAD screen.
        // That is the classic key's existing behaviour, shared by both document screens — not a bug
        // to fix here, and not a second key to invent.
        val lastLanguageCode = CommonUtils.settings.getString("selected_language_code", null)
        val lastLanguage = lastLanguageCode?.let { code -> rows.firstOrNull { it.language.code == code }?.language }
        val lastTypeFilter = DocTypeFilter.entries.getOrElse(
            CommonUtils.settings.getInt("selected_document_filter_no", 0),
        ) { DocTypeFilter.ALL }
        return buildDocumentQuickTabs(
            // Every mapped row, unfiltered: dropping the ones a sheet cannot action (locked modules,
            // AND_BIBLE pseudo-documents) is [buildDocumentQuickTabs]' own job — see its kdoc, and
            // `DocumentQuickTabsTest` for the guard. Note `lastLanguage` above is deliberately
            // resolved against this FULL list, so a language whose only document is locked does not
            // silently lose the user's saved filter.
            installed = rows,
            recentInitials = RecentDocumentsStore.read(),
            forVerseIds = forVerseIds,
            lastLanguage = lastLanguage,
            lastTypeFilter = lastTypeFilter,
            scope = scope,
        )
    }

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
    internal fun restoreQuickDocTab(scope: DocumentSheetScope, visible: List<DocumentQuickTab>): String? {
        val saved = CommonUtils.settings.getString(scope.tabSettingKey, null)
        return visible.firstOrNull { it.name == saved }?.name ?: visible.firstOrNull()?.name
    }

    internal fun persistQuickDocTab(scope: DocumentSheetScope, tabId: String) =
        CommonUtils.settings.setString(scope.tabSettingKey, tabId)

    /** The document shown in the active window — the row the sheet draws bold and inert. */
    private fun currentDocumentInitials(): String? = documentControl.currentDocument?.initials

    /** Loads the verse-share sheet's toggle state from the same pref keys the classic
     *  `ShareWidget` read, with the same defaults. */
    private fun loadShareVersesOptions(): ShareVersesOptions = ShareVersesOptions(
        showVerseNumbers = CommonUtils.settings.getBoolean(SHARE_VERSE_NUMBERS_KEY, true),
        advertiseApp = CommonUtils.settings.getBoolean(SHARE_SHOW_ADD_KEY, true),
        showReference = CommonUtils.settings.getBoolean(SHARE_SHOW_REFERENCE_KEY, true),
        abbreviateReference = CommonUtils.settings.getBoolean(SHARE_ABBREVIATE_REFERENCE_KEY, true),
        showVersion = CommonUtils.settings.getBoolean(SHARE_SHOW_VERSION_KEY, true),
        showNotes = CommonUtils.settings.getBoolean(SHARE_SHOW_NOTES_KEY, true),
        showSelectionOnly = CommonUtils.settings.getBoolean(SHARE_SHOW_SELECTION_ONLY_KEY, true),
        showEllipsis = CommonUtils.settings.getBoolean(SHARE_SHOW_ELLIPSIS_KEY, true),
        showReferenceAtFront = CommonUtils.settings.getBoolean(SHARE_SHOW_REFERENCE_AT_FRONT_KEY, true),
        showQuotes = CommonUtils.settings.getBoolean(SHARE_SHOW_QUOTES_KEY, false),
        separateVersesWithNewlines = CommonUtils.settings.getBoolean(SHARE_SEPARATE_VERSES_NEWLINES_KEY, false),
    )

    /** Persists every toggle back to the same keys [loadShareVersesOptions] reads — the classic
     *  `ShareWidget` wrote all eleven on every single click (`updateSelectionOptions`); this does
     *  the same on every [updateShareVersesOptions] call. */
    private fun persistShareVersesOptions(options: ShareVersesOptions) {
        CommonUtils.settings.apply {
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
    private var keyChooserKeys: List<Key> = emptyList()

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
    internal fun showKeyChooserSheet(kind: KeyChooserKind, keys: List<Key>) {
        keyChooserKeys = keys
        showQuickSheet(
            ReadingQuickSheet.KeyChooser(
                kind,
                // The same source the full-screen twin falls back to when nothing overrides it
                // (`GridChoosePassageComposeActivity`) -- never a hard-coded true, which would force
                // a verse step the user turned off. Read HERE rather than in the sheet's composable
                // arm precisely so the other opening ([openVerseChooserSheetForResult]) can differ:
                // spec §6.1.1. Meaningless for the two flat list kinds, which have no verse step.
                navigateToVerse = kind == KeyChooserKind.Grid &&
                    CommonUtils.settings.getBoolean("navigate_to_verse_pref", false),
            ),
        )
    }

    private val mapPage: CurrentMapPage get() = windowControl.activeWindowPageManager.currentMap
    private val generalBookPage: CurrentGeneralBookPage get() = windowControl.activeWindowPageManager.currentGeneralBook

    /**
     * Maps the active page onto [KeyChooserPage]; null when nothing here recognises it.
     *
     * Mirrors `CurrentPage.startKeyChooser`'s own dispatch, including its ORDER.
     * [CurrentMyNotePage] extends [CurrentCommentaryPage] and does not override `startKeyChooser`,
     * so the my-note check must come FIRST — see [KeyChooserPage]'s kdoc, and
     * `KeyChooserDispatchGuardTest`, which fails if these two lines are ever swapped back.
     * The general-book four-way test is `CurrentGeneralBookPage.startKeyChooser`'s own, in its own
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
                FakeBookFactory.journalDocument -> KeyChooserPage.GENERAL_BOOK_STUDY_PAD
                FakeBookFactory.multiDocument -> KeyChooserPage.GENERAL_BOOK_MULTI_DOCUMENT
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
    internal fun resolveKeyChooserKeys(kind: KeyChooserKind): List<Key> = when (kind) {
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

    private val historyManager: HistoryManager by inject()

    /**
     * The [HistoryEntry] list, mirroring the `controller` of the since-deleted
     * `HistoryComposeActivity` verbatim
     * (`historyManager.getHistory(activeWindow.id)` + the `"h:mm a, E d MMM "` timestamp format
     * both the classic screen and the goldens depend on). [historyItems] is stashed alongside so
     * [revertToHistoryItem] can resolve an entry's `id` (its list index, not a stable key) back to
     * the [HistoryItem] to revert.
     */
    private var historyItems: List<HistoryItem> = emptyList()

    private fun historyEntriesForActiveWindow(): List<HistoryEntry> {
        historyItems = historyManager.getHistory(windowControl.activeWindow.id)
        return historyItems.mapIndexed { index, item ->
            HistoryEntry(
                id = index,
                title = item.description.toString(),
                timestamp = format("h:mm a, E d MMM ", item.createdAt).toString(),
            )
        }
    }

    private fun revertToHistoryItem(id: Int) {
        historyItems[id].revertTo()
    }

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
     */
    @Composable
    private fun QuickSheetSlot() {
        when (val sheet = quickSheet.value) {
            null -> Unit
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
                        SharedActivityState.currentWorkspaceName,
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
                    WorkspaceQuickController(workspaceService) { id ->
                        closeQuickSheet()
                        // C1: the quick sheet never pauses the activity, so nothing else flushes the
                        // outgoing workspace's windows/page-managers/history before switching. Use
                        // the save-then-switch entry point, never plain switchToWorkspace here.
                        activity.readingCommands.quickSwitchToWorkspace(id)
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
                    val controller = remember(sheet) {
                        newGridPassageController(
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
                        title = ui.title,
                        onDismiss = { closeQuickSheet() },
                        // Still the header's back arrow: it pops a grid step until the stack is empty.
                        canGoBack = { ui.step != GridStep.BOOK },
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
                        actions = if (ui.step == GridStep.BOOK) {
                            {
                                // The six options are not decoration: GridOption.DEUTEROCANONICAL
                                // rebuilds the book list, so dropping them here would be a
                                // functional regression. AbOverflowMenu is a DropdownMenu, i.e. a
                                // Popup, which stacks over a sheet without difficulty -- the port's
                                // ban is on sheet-over-sheet, not popup-over-sheet.
                                GridOptionsOverflow(ui, options) { controller.toggle(it) }
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
                        BackHandler(enabled = ui.step != GridStep.BOOK) { controller.back() }
                        GridChoosePassageContent(ui, controller::pick, Modifier.fillMaxSize(), state = gridState)
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
        val controller = remember(pickingEnd) {
            newGridPassageController(
                baseTitle = title,
                isScripture = true,
                navigateToVerse = true,
                onFinish = { osisId ->
                    val verse = VerseFactory.fromString(navigationControl.versification, osisId)
                    speakRangeEditor.set(pickingEnd, PickedVerse(verse.osisID, verse.name, verse.ordinal))
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
        GridChoosePassageContent(ui, controller::pick, Modifier.fillMaxSize())
    }

    /**
     * The seven lambdas `GridChoosePassageComposeActivity` builds, assembled for a sheet page. Every
     * one of them calls into `GridPassageHostSupport.kt` — that activity's own extracted helpers, so
     * there is one implementation behind both hosts rather than a copy here.
     */
    private fun newGridPassageController(
        baseTitle: String,
        isScripture: Boolean,
        navigateToVerse: Boolean,
        onFinish: (String) -> Unit,
    ): GridChoosePassageController {
        var selectedBookNo = 0
        var selectedChapter = 1
        val v11n = navigationControl.versification
        val workspaceName = SharedActivityState.currentWorkspaceName
        return GridChoosePassageController(
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
                Verse(v11n, BibleBook.values()[selectedBookNo], selectedChapter, verse).osisID
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
     */
    fun openSearchStrongs(ref: String, translationIds: List<String>): Boolean {
        if (documentControl.currentDocument?.isEpub == true) return false
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
     * whether the press was consumed. Wired into `MainBibleActivity.onBackPressed` by Task 9 — inert
     * until then.
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
    private val windowButtonsVisibility = WindowButtonsVisibility()

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
    private val paneMenuWindowId = mutableStateOf<String?>(null)
    private val paneMenuItems = mutableStateOf(emptyList<WindowPaneMenuItem>())

    /**
     * Which surface ([PaneMenuAnchor]) the currently-open [paneMenuWindowId] menu is anchored to —
     * set by [openPaneMenu]'s caller (the pane overlay's ☰-button tap passes [PaneMenuAnchor.Pane],
     * the rail's `onWindowLongPress` passes [PaneMenuAnchor.Rail]). [mountComposeView] gates each
     * surface's own `paneMenuWindowId` on this so exactly one of the two `WindowPaneMenu` instances
     * ever reports itself expanded — see the pane overlay's `paneOverlay` lambda and the rail's
     * `tabBar` lambda in [mountComposeView].
     */
    private val paneMenuAnchor = mutableStateOf(PaneMenuAnchor.Pane)

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
     * [openOverflowMenu]), or `onBackPressed` — is reflected here. There is
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
    private val drawerOpen = mutableStateOf(false)

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
    fun toggleDrawer() { drawerOpen.value = !drawerOpen.value }

    /**
     * Whether the Compose drawer is open. Reads the same request flag `mountComposeView` keeps in
     * two-way sync with the real `DrawerState` (Batch Z-early A7 fix D made that sync symmetric, so
     * this is true for a drawer opened by ANY route, not just [toggleDrawer]).
     *
     * Read by [MainBibleActivity]'s key/back handlers, which separately ask
     * `drawerLayout.isDrawerVisible(GravityCompat.START)` on the native `DrawerLayout` — always
     * `false` there, since `setupUi` locks it as soon as the host is installed.
     */
    val isDrawerOpen: Boolean get() = drawerOpen.value

    /** Opens the Compose drawer (idempotent) — see [isDrawerOpen]. */
    fun openDrawer() { drawerOpen.value = true }

    /** A tap on one of this host's BibleViews: re-show the window buttons (classic SplitBibleArea.kt:203-205). */
    fun onBibleViewTouched() = windowButtonsVisibility.onTouch()

    /** Closes the Compose drawer (idempotent) — see [isDrawerOpen]. */
    fun closeDrawer() { drawerOpen.value = false }

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
        agentSessionService.refresh()
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
    fun openPaneMenu(windowId: String, anchor: PaneMenuAnchor) {
        windowButtonsVisibility.onTouch()
        val window = activity.hostWindowRepository.getWindow(IdType(windowId)) ?: return
        paneMenuItems.value = paneMenuStateBuilder.build(window)
        paneMenuAnchor.value = anchor
        paneMenuWindowId.value = windowId
    }

    /** Closes whichever per-window ☰ menu is open (`onDismiss`, or after a non-toggle item acts). */
    fun closePaneMenu() { paneMenuWindowId.value = null }

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
     * The reading view, with THIS host's collaborators bound into every one of
     * [ReadingViewContent]'s parameters -- what used to be [install]'s `mountComposeView(...)`
     * argument block, and nothing else.
     *
     * **Extracted by reading-host re-typing R8, which is the task that gave it a second caller.**
     * `NavHostComposeActivity`'s `reading` destination composes this directly (its `content` slot
     * was an `error(...)` until R8), and a destination has a composition rather than a
     * [ViewGroup] -- so the ~340 lines of argument building had to stop being reachable only
     * through a function that takes a container. Copying them into the nav host instead would have
     * made two argument lists to keep true, of which only one would ever be edited; this is one.
     *
     * [mountComposeView] is NOT this function's caller and is not meant to be: it keeps the inert
     * per-parameter defaults its five host tests pass around explicit collaborators, and its 71
     * explicit forwards to [ReadingViewContent] stay the compile-time check that the test seam and
     * the content cannot drift apart. This function is the PRODUCTION binding of the same
     * parameters, and both are checked by the same compiler for the same reason.
     */
    @OptIn(ExperimentalMaterial3Api::class)
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
        ReadingViewContent(
            windowState = windowState,
            commands = commands,
            nightModeState = nightMode,
            generationState = generation.state,
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
            fullScreenState = fullScreen,
            overlayTextState = overlayText,
            activeIsBibleShownState = activeIsBibleShown,
            overflowItemsState = overflowItems,
            overflowExpandedState = overflowExpanded,
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
            // as `windowIcon` above, except this one must be `@Composable` because
            // `painterResource` is only callable inside composition. NOT the raw [drawerIconResIds]
            // table: `"ic_logo"` needs the call-time discrete-mode branch (F58 fix round 2 / R13).
            drawerState = drawerMenu,
            drawerOpenState = drawerOpen,
            drawerIcon = { key ->
                val resId = drawerIconResIdFor(key)
                if (resId == null) null else painterResource(resId)
            },
            // Dispatches through the SAME `MenuCommandHandler.handleMenuRequest(itemId)` the classic
            // `NavigationView` listener calls (see `MainBibleActivity.handleDrawerItemClick`), so
            // every row's command behaviour is classic's by construction.
            onDrawerItemClick = { id -> activity.readingCommands.handleDrawerItemClick(DrawerMenuStateBuilder.resIdFor(id)) },
            // Batch Z-early A7: classic `DrawerListener` parity — see the three `LaunchedEffect`s
            // in `mountComposeView` and the entry points' kdoc on [MainBibleActivity].
            monochromeState = monochrome,
            onDrawerInMotion = { activity.showSystemUiTransient() },
            onDrawerIdleClosed = { activity.applyIdleSystemUi() },
            // Round 12b §1: the guard against stealing focus from an open search bar lives INSIDE
            // restorePaneFocus (see drawerShouldRestorePaneFocus), not here — the drawer's
            // own Search row goes through this same callback.
            onDrawerClosed = { activity.restorePaneFocus() },
            pane = { windowId ->
                val window = activity.hostWindowRepository.getWindow(IdType(windowId))
                if (window != null) {
                    // A/B batch 4a whole-batch review I1: classic `BibleFrame.build()` also calls
                    // `bibleView.updateBackgroundColor()` (BibleFrame.kt:137), which sets the WebView's
                    // OWN background (`BibleView.updateBackgroundColor` -> `setBackgroundColor`). Without
                    // this, even with C1 fixed (the pane Box now paints the right colour underneath), a
                    // freshly created WebView still draws its platform-default white over that pane
                    // background until its first document finishes loading -- same flash, one layer up.
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = {
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
                            // `Modifier.fillMaxSize()` above already gives the WebView an EXACTLY
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
            },
            paneBackground = { windowId -> paneBackgroundArgbFor(windowId)?.let { Color(it) } },
            windowLabel = ::railWindowLabel,
            windowIcon = { snapshot -> activity.readingCommands.windowIconFor(snapshot.id) },
            // Task 4 (F2b): the rail's tiny top row (classic `topButtonText`) — see
            // `MainBibleActivity.windowTopLabelFor`'s kdoc.
            windowTopLabel = ::railWindowTopLabel,
            controller = controller,
            windowButtonsVisibleState = windowButtonsVisibility.visible,
            touchTickState = windowButtonsVisibility.touchTick,
            onWindowButtonsHideTimeout = windowButtonsVisibility::onHideTimeout,
            paneMenuWindowIdState = paneMenuWindowId,
            paneMenuItemsState = paneMenuItems,
            paneMenuAnchorState = paneMenuAnchor,
            onOpenPaneMenu = ::openPaneMenu,
            onPaneMenuItemClick = { windowId, id ->
                val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(windowId, id)
                if (stayOpen) {
                    activity.hostWindowRepository.getWindow(IdType(windowId))?.let {
                        paneMenuItems.value = paneMenuStateBuilder.build(it)
                    }
                } else {
                    closePaneMenu()
                }
            },
            onPaneMenuDismiss = ::closePaneMenu,
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
            // Batch 12e-B Task 6: the agent-log panel, pre-built here (closing over the live
            // `agentLog` controller) since `install` already owns it — mirrors how `pane` above is
            // threaded straight through `mountComposeView` rather than rebuilt from raw state.
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
            // above — `install` already owns it, so it's threaded straight through
            // `mountComposeView` rather than rebuilt from raw state.
            speakBarSlot = { applyNavBarInset ->
                val speakState by speakTransport.state.collectAsState()
                // Round 14b §6: the agent panel's visibility, narrowed to a boolean through
                // `derivedStateOf` — the SAME idiom as `agentLogVisibleState` a few lines below, and
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
            agentLogVisibleState = {
                val agentLogState = agentLog.state.collectAsState()
                val visible = remember { derivedStateOf { agentLogState.value.visible } }
                visible.value
            },
            speakBarVisibleState = {
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
            speakDialogState = speakTransport.dialog,
            onSpeakBookmarkChosen = speakTransport::onSpeakBookmarkChosen,
            onSpeakDialogDismiss = speakTransport::dismissDialog,
            llmDialogState = readingLlmDialogs.state,
            onLlmPromptChosen = readingLlmDialogs::onPromptChosen,
            onLlmToggleFavorite = readingLlmDialogs::onToggleFavorite,
            onLlmCategoryExpandedChanged = readingLlmDialogs::onCategoryExpandedChanged,
            onLlmSpecifySubmitted = readingLlmDialogs::onSpecifySubmitted,
            onLlmModelChosen = readingLlmDialogs::onModelChosen,
            onLlmRegenerateConfirmed = readingLlmDialogs::onRegenerateConfirmed,
            onLlmDismiss = readingLlmDialogs::dismiss,
            // F6 Task 8a: the toolbar's search mode. Every callback goes to [searchController] or to
            // the host's own menu/settings flags — none of them touches the pane subtree, which is
            // what `ReadingSearchHostTest.openingAndClosingSearchMustNotRebuildThePaneSubtree`
            // guards.
            searchBarState = searchBar,
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
            searchSheetVisibleState = searchController.sheetVisible,
            onSearchSheetDismissed = { searchController.closeSheet() },
            searchSheetSlot = { SearchSheetSlot() },
            searchSettingsSlot = { SearchSettingsSlot() },
            // Settings editor sheets T10: the reading view's in-place text-settings editor.
            textSettingsEditorSlot = { TextSettingsEditorSlot() },
            // Round 13a: the Speak settings sheet.
            speakSettingsSlot = { SpeakSettingsSlot() },
            quickSheetSlot = { QuickSheetSlot() },
            // Platform-dialog removal Task 18: the reading view's own dialogs (BJI help/delete-confirm).
            readingDialogSlot = { ReadingDialogSlot() },
            // Task 8b Step 3: feeds MainBibleActivity.bottomOffsetForWebView's fourth term.
            onSearchSheetOffsetsChanged = { visible, heightPx -> activity.readingInsets.updateSearchSheetOffsets(visible, heightPx) },
            // Task 10: the "<document> cannot be searched" snackbar.
            searchUnavailableDocNameState = searchUnavailableDocName,
            onSearchUnavailableMessageShown = { searchUnavailableMessageShown() },
            // F59: the live value, straight from the host -- see ReadingHostActivity
            // .imeBottomPaddingPx's kdoc for why MainBibleActivity answers a permanent 0 and the
            // nav host does not.
            imeBottomPaddingPxState = activity.imeBottomPaddingPx,
        )
    }

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
        linkControl.showLink(FakeBookFactory.multiDocument, list)
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
        private const val SHARE_VERSE_NUMBERS_KEY = "share_verse_numbers"
        private const val SHARE_SHOW_ADD_KEY = "share_show_add"
        private const val SHARE_SHOW_REFERENCE_KEY = "share_show_reference"
        private const val SHARE_ABBREVIATE_REFERENCE_KEY = "share_abbreviate_reference"
        private const val SHARE_SHOW_VERSION_KEY = "share_show_version"
        private const val SHARE_SHOW_NOTES_KEY = "show_notes"
        private const val SHARE_SHOW_SELECTION_ONLY_KEY = "show_selection_only"
        private const val SHARE_SHOW_ELLIPSIS_KEY = "show_ellipsis"
        private const val SHARE_SHOW_REFERENCE_AT_FRONT_KEY = "share_show_reference_at_front"
        private const val SHARE_SHOW_QUOTES_KEY = "share_show_quotes"
        private const val SHARE_SEPARATE_VERSES_NEWLINES_KEY = "share_separate_verses_newlines"

        /** Settings keys shared with the search Activities — see [searchQueries]/[setSearchTranslations]. */
        private const val SEARCH_TRANSLATIONS_KEY = "search_selected_translations"
        private const val SEARCH_RECENT_TERMS_KEY = "search_recent_terms"

        /** `SearchIndexProgressComposeActivity`'s post-finish poll interval (`:365`'s `pause(2)`).
         *  Its other timing — the ~4 s "no tasks running" reveal — is deliberately not reproduced;
         *  see [startSearchIndexing]. */
        private const val SEARCH_INDEX_POLL_INTERVAL_MS = 2_000L

        /**
         * **TEST-ONLY since reading-host re-typing R8 -- this function has no production caller.**
         * R8 moved [install]'s argument block into [ReadingView] so the `reading` nav destination
         * could compose the same arguments with no [ViewGroup], and [install] now builds its
         * [ComposeView] over [ReadingView] directly (byte-identical construction, same layout
         * params, same container). Nothing else called this.
         *
         * It is KEPT, deliberately, as the explicit-collaborator seam its five callers use
         * (`ComposeReadingViewHostTest`, `ReadingSearchHostTest`, `AgentLogHostTest`,
         * `ReadingLlmHostTest`, `SearchSheetStructureGuardTest`): they mount [ReadingViewContent] --
         * the SAME composition production renders -- with inert defaults for the slots they do not
         * exercise, which is the only way to drive it without booting a full reading host. Its 71
         * forwards are all identity forwards and stay explicit, so this list and [ReadingViewContent]
         * still cannot drift apart without a compile error here.
         *
         * Adds a [ComposeView] rendering [ReadingViewScreen] to [container].
         */
        @OptIn(ExperimentalMaterial3Api::class)
        fun mountComposeView(
            container: ViewGroup,
            windowState: WindowStateServiceImpl,
            commands: WindowCommands,
            // A `State` (not a plain `Boolean`) so the host's [ScreenSettings.nightModeChanges]
            // subscription (or a test) can flip it and drive a real recomposition instead of a
            // value frozen at mount time.
            nightModeState: State<Boolean>,
            // Defaults to a fresh, never-bumped state for the unit test (which mounts with
            // `pane = {}` and never attaches the ComposeView, so composition never runs).
            generationState: State<Int> = mutableIntStateOf(0),
            toolbar: StateFlow<ToolbarState> = MutableStateFlow(ToolbarState.EMPTY).asStateFlow(),
            toolbarCallbacks: ReadingToolbarCallbacks = noopToolbarCallbacks,
            // A `State` for the same reactivity reason as [nightModeState]: [install] mirrors
            // [MainBibleActivity.fullScreen] here via [SharedActivityState.fullScreenChanged] instead
            // of passing a one-shot snapshot.
            fullScreenState: State<Boolean> = mutableStateOf(false),
            // Batch 12g Task 3 additions: the fullscreen bible-reference overlay's text + the
            // active window's bible-shown flag, `State`s for the same reactivity reason as
            // `fullScreenState` above — [ComposeReadingViewHost.install] mirrors both from
            // `PageChange.VerseChanged`/`WindowChange.ActiveWindowChanged` (see its `init` block) rather
            // than passing a one-shot snapshot. Defaulted (empty text / bible not shown) so
            // `ComposeReadingViewHostTest` (which never renders the overlay) is unaffected.
            overlayTextState: State<String> = mutableStateOf(""),
            activeIsBibleShownState: State<Boolean> = mutableStateOf(false),
            // The Compose overflow options menu's item list / expanded flag (Batch 12b-C Task 3),
            // `State`s for the same reason as [nightModeState]/[fullScreenState]: [install] owns
            // mutable backing state (`overflowItems`/`overflowExpanded`) that its
            // `ReadingToolbarCallbacks.onOverflow`/`onOverflowItemClick`/`onOverflowDismiss` mutate
            // in response to activity/menu events, not a one-shot snapshot. Defaulted (empty/
            // collapsed/no-op) so `ComposeReadingViewHostTest` (which never opens the menu) is
            // unaffected.
            overflowItemsState: State<List<OptionsMenuItem>> = mutableStateOf(emptyList()),
            overflowExpandedState: State<Boolean> = mutableStateOf(false),
            onOverflowItemClick: (id: String) -> Unit = {},
            onOverflowDismiss: () -> Unit = {},
            // Batch Z-early A6: the navigation drawer. The host owns the item list and the
            // open/closed request as `State`s (same reactivity reason as `overflowExpandedState`);
            // `ModalNavigationDrawer`'s own `DrawerState` is created inside `setContent` and kept in
            // sync with `drawerOpenState` both ways (a user swipe-close must clear the request, else
            // the next `toggleDrawer()` would see a stale `true` and do nothing). `drawerIcon` is
            // `@Composable` because the host resolves it with `painterResource` (see `install`).
            // Defaulted (empty menu / closed / no icons / no-op click) so `ComposeReadingViewHostTest`
            // and friends — which never open the drawer — are unaffected.
            drawerState: State<DrawerMenuState> = mutableStateOf(DrawerMenuState.EMPTY),
            drawerOpenState: MutableState<Boolean> = mutableStateOf(false),
            drawerIcon: @Composable (iconKey: String) -> Painter? = { null },
            onDrawerItemClick: (id: String) -> Unit = {},
            // Batch Z-early A7: classic `DrawerLayout.DrawerListener` parity. `monochromeState` is a
            // `State` for the same reactivity reason as `nightModeState` (the host mirrors
            // `CommonUtils.settings.monochromeMode` into it) and only drives the scrim colour, which
            // classic sets once in `setupUi`. The three callbacks are the derived listener edges:
            //   in-motion    <- STATE_SETTLING / STATE_DRAGGING  -> showSystemUI(false)
            //   idle-closed  <- STATE_IDLE at slide offset 0f    -> hide/showSystemUI()
            //   closed       <- onDrawerClosed                   -> activeWindow.bibleView.requestFocus()
            // `DrawerState` cannot tell a user drag from an animated settle — accepted, because
            // classic does the very same thing in both of those branches. Defaulted to no-ops so
            // `ComposeReadingViewHostTest` and friends are unaffected.
            monochromeState: State<Boolean> = mutableStateOf(false),
            onDrawerInMotion: () -> Unit = {},
            onDrawerIdleClosed: () -> Unit = {},
            onDrawerClosed: () -> Unit = {},
            pane: @Composable (windowId: String) -> Unit,
            // A/B batch 4a F5: per-pane reader background, forwarded to `SplitContent`. Defaulted to
            // "no background" so `ComposeReadingViewHostTest` (which mounts with `pane = {}` and
            // never composes) and every golden fixture render exactly as before.
            paneBackground: (windowId: String) -> Color? = { null },
            // Host-supplied per-window label/icon for the restore rail (Task 7) — plain,
            // non-`@Composable` lambdas, matching `WindowTabBar`'s `windowLabel`/`windowIcon`
            // parameter types (so composable calls like `painterResource` can't sneak into them;
            // see `MainBibleActivity.windowIconFor`'s kdoc for how it builds a `Painter` without
            // one). Defaulted (blank label, no icon) so `ComposeReadingViewHostTest` — which never
            // renders the rail itself — is unaffected.
            windowLabel: (WindowSnapshot) -> String = { "" },
            windowIcon: (WindowSnapshot) -> Painter? = { null },
            // Task 4 (F2b): the rail's tiny top row (classic `topButtonText`,
            // `pageManager.titleText`) — same non-`@Composable`, host-supplied shape as
            // `windowLabel`/`windowIcon` above, `null` meaning "render no top row" (see
            // `MainBibleActivity.windowTopLabelFor`'s kdoc). Defaulted to `{ null }` for the same
            // reason as `windowLabel`/`windowIcon`.
            windowTopLabel: (WindowSnapshot) -> String? = { null },
            // Batch 12b follow-on Plan B Task 5 additions — all `State`s / no-op-defaulted for the
            // same reason as the `overflow*`/`fullScreenState` params above: [ComposeReadingViewHost]
            // owns the real backing state, a test (or an omitted call site) gets an inert default.
            controller: ReadingViewController = ReadingViewController(windowState, commands),
            windowButtonsVisibleState: State<Boolean> = mutableStateOf(true),
            touchTickState: State<Int> = mutableIntStateOf(0),
            onWindowButtonsHideTimeout: () -> Unit = {},
            paneMenuWindowIdState: State<String?> = mutableStateOf(null),
            paneMenuItemsState: State<List<WindowPaneMenuItem>> = mutableStateOf(emptyList()),
            // A/B batch 3 F5b: which surface (pane overlay vs. rail) the currently-open pane menu
            // is anchored to — see [PaneMenuAnchor]. Defaulted to a fixed `Pane` state for the same
            // reason as `paneMenuWindowIdState`/`paneMenuItemsState` above.
            paneMenuAnchorState: State<PaneMenuAnchor> = mutableStateOf(PaneMenuAnchor.Pane),
            onOpenPaneMenu: (windowId: String, anchor: PaneMenuAnchor) -> Unit = { _, _ -> },
            onPaneMenuItemClick: (windowId: String, id: String) -> Unit = { _, _ -> },
            onPaneMenuDismiss: () -> Unit = {},
            // A/B batch 1 F5b: resolves a [WindowPaneMenuItem.iconKey]/[OptionsMenuItem.iconKey] to
            // a `Painter` for BOTH the pane popup menu and the toolbar's overflow menu — the same
            // "host resolves, `:sharedCore`/`:sharedUi` stay Android-free" shape as `drawerIcon`
            // above, `@Composable` because `painterResource` is only callable inside composition.
            // Defaulted to always-`null` so `ComposeReadingViewHostTest` and friends (which never
            // resolve a menu icon) are unaffected.
            menuIcon: @Composable (iconKey: String) -> Painter? = { null },
            // Batch 12e-A Task 6 additions: the reading-view LLM dialogs (prompt selector,
            // specify-before-run, model selection, regenerate-confirm), rendered as a sibling of
            // `ReadingViewScreen` below. A `StateFlow` (not a plain `State`), mirroring the
            // `toolbar` param above — [ComposeReadingViewHost.install] passes
            // `readingLlmDialogs.state` directly and this collects it via `collectAsState()`, same
            // pattern as `toolbarState`. Defaulted to an always-`None` flow + no-op callbacks so
            // `ComposeReadingViewHostTest` (which never opens an LLM dialog) is unaffected.
            llmDialogState: StateFlow<ReadingLlmDialogState> = MutableStateFlow(ReadingLlmDialogState()).asStateFlow(),
            onLlmPromptChosen: (promptId: String) -> Unit = {},
            onLlmToggleFavorite: (promptId: String) -> Unit = {},
            onLlmCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit = { _, _ -> },
            onLlmSpecifySubmitted: (text: String) -> Unit = {},
            onLlmModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit = { _, _ -> },
            onLlmRegenerateConfirmed: (instructions: String?, keepPrevious: Boolean, freshRun: Boolean) -> Unit = { _, _, _ -> },
            onLlmDismiss: () -> Unit = {},
            // Batch 12e-B Task 6 addition: the reading-view agent-log panel (`AgentLogPanel`),
            // rendered as `ReadingViewScreen`'s `agentLog` slot (Task 5). Unlike the LLM-dialog
            // params above (raw `StateFlow` + individual callbacks, assembled into a concrete
            // composable INSIDE this function's body), this is a pre-built `@Composable` lambda —
            // [ComposeReadingViewHost.install] already owns the live `agentLog` controller and
            // closes over it directly when building this, the same pass-through shape as [pane].
            // Defaulted to an inert no-op composable so `ComposeReadingViewHostTest`/
            // `ReadingLlmHostTest` (which never render an agent-log panel) are unaffected.
            agentLogSlot: (@Composable (
                applyNavBarInset: Boolean,
                maxHeightDp: Float,
                collapsedHeightDp: Float,
                onCollapsedHeightMeasured: (Float) -> Unit,
            ) -> Unit)? = { _, _, _, _ -> },
            // Batch 12f Task 6 additions: the reading-view Speak transport bar. `speakBarSlot` is a
            // pre-built `@Composable` lambda (same pass-through shape as `agentLogSlot` right
            // above — [ComposeReadingViewHost.install] already owns the live `speakTransport`
            // controller and closes over it directly). The bookmark-chooser dialog instead mirrors
            // the LLM-dialog shape (raw `StateFlow` + individual callbacks, assembled into a
            // concrete composable inside this function's body) since — like `ReadingLlmDialogs` —
            // it's rendered as a sibling overlay of `ReadingViewScreen`, not inside a slot. Defaulted
            // to inert no-ops so `ComposeReadingViewHostTest` (which never renders the bar/dialog)
            // is unaffected.
            speakBarSlot: (@Composable (applyNavBarInset: Boolean) -> Unit)? = { },
            speakDialogState: StateFlow<SpeakTransportDialog> = MutableStateFlow<SpeakTransportDialog>(SpeakTransportDialog.None).asStateFlow(),
            onSpeakBookmarkChosen: (id: String) -> Unit = {},
            onSpeakDialogDismiss: () -> Unit = {},
            // F6 Task 8a additions — the reading-view search. Appended at the END with inert
            // defaults, the convention every earlier slot followed, so `ComposeReadingViewHostTest`
            // and `AgentLogHostTest` keep compiling unchanged.
            //
            // `searchBarState` is a `StateFlow` for the same reason as `toolbar` above:
            // [ComposeReadingViewHost.searchBar] already combines the session's flows into it, and
            // this only collects it. A `null` value means "not in search mode", which is what makes
            // `ReadingToolbar` draw its normal row (Task 4).
            searchBarState: StateFlow<ReadingSearchBarState?> = MutableStateFlow<ReadingSearchBarState?>(null).asStateFlow(),
            searchBarCallbacks: ReadingSearchBarCallbacks? = null,
            // Drives the (unconditional, see below) `BottomSheetScaffold`'s sheet open/closed.
            searchSheetVisibleState: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow(),
            // A user drag that puts the sheet away must reach the session, or `sheetVisible` would
            // stay true and the next `open()` for the same query would change nothing at all.
            onSearchSheetDismissed: () -> Unit = {},
            // Pre-built `@Composable` lambdas, the same pass-through shape as `agentLogSlot`/
            // `speakBarSlot`: [ComposeReadingViewHost.install] already owns the live controllers and
            // closes over them. `searchSheetSlot` is what goes INSIDE the sheet (index prompt/
            // progress, or results); `searchSettingsSlot` is the separate modal settings sheet,
            // composed as a sibling overlay like the dialogs.
            searchSheetSlot: @Composable () -> Unit = { },
            searchSettingsSlot: @Composable () -> Unit = { },
            // Settings editor sheets T10: the reading view's in-place text-settings editor, a fifth
            // sibling overlay next to `searchSettingsSlot` above (see its mounting call site below
            // for why). Defaulted to a no-op so every existing `mountComposeView` caller/test keeps
            // compiling unchanged.
            textSettingsEditorSlot: @Composable () -> Unit = { },
            // Round 13a: the Speak settings sheet — a sixth sibling overlay, for the same reason as
            // the five above: a `ModalBottomSheet` renders in its own window regardless of where it
            // is composed, so opening it can never re-key the pane subtree and destroy the panes'
            // BibleView WebViews. It self-hides when closed, so this stays unconditional too.
            speakSettingsSlot: @Composable () -> Unit = { },
            // Round 15b: the quick sheets — a seventh sibling overlay. Defaulted to a no-op so every
            // existing `mountComposeView` caller/test keeps compiling unchanged.
            quickSheetSlot: @Composable () -> Unit = { },
            // Platform-dialog removal Task 18: the reading view's own dialogs ([ReadingDialog]) — an
            // eighth sibling overlay, defaulted to a no-op for the same reason as the seven above.
            readingDialogSlot: @Composable () -> Unit = { },
            // Task 8b Step 3: reports the search sheet's live (visible, measured-height-in-px) pair
            // so [ComposeReadingViewHost.install] can feed `MainBibleActivity.bottomOffsetForWebView`
            // — see [MainBibleActivity.updateSearchSheetOffsets]'s kdoc for why the height must be
            // MEASURED rather than a constant guess (the sheet is self-sizing under a 60% ceiling).
            // Defaulted to a no-op so `ComposeReadingViewHostTest` (which never renders the sheet) is
            // unaffected.
            onSearchSheetOffsetsChanged: (visible: Boolean, heightPx: Int) -> Unit = { _, _ -> },
            // Task 10: the "<document> cannot be searched" snackbar. `searchUnavailableDocNameState`
            // mirrors [ComposeReadingViewHost.searchUnavailableDocName] (non-null while a message is
            // waiting); `onSearchUnavailableMessageShown` mirrors [ComposeReadingViewHost
            // .searchUnavailableMessageShown]. Defaulted to an always-null flow + no-op so
            // `ComposeReadingViewHostTest` (which never triggers `onUnavailable`) is unaffected.
            searchUnavailableDocNameState: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow(),
            onSearchUnavailableMessageShown: () -> Unit = {},
            /**
             * Whether the agent-log panel will render, and whether the speak bar will — read as
             * `@Composable` lambdas rather than plain booleans so the flow collection stays inside
             * the composition that needs it. `ReadingViewScreen` uses them for navigation-bar inset
             * ownership (round 12b §3) and, from Task 6, for the overlay's in-flow reservation.
             */
            agentLogVisibleState: @Composable () -> Boolean = { false },
            speakBarVisibleState: @Composable () -> Boolean = { false },
            // F59: same reactivity reason as `fullScreenState` above -- [ComposeReadingViewHost
            // .ReadingView] passes the live `activity.imeBottomPaddingPx`. Defaulted to a fixed 0 so
            // `ComposeReadingViewHostTest` and friends (which never dispatch IME insets) are unaffected.
            imeBottomPaddingPxState: State<Int> = mutableIntStateOf(0),
        ) {
            val composeView = ComposeView(container.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                // Nav-graph slice 7 Task 6: the content itself is [ReadingViewContent] now, so the
                // reading DESTINATION can render it with no ViewGroup. Every parameter is forwarded
                // EXPLICITLY and that function defaults none of them, so a parameter added to one
                // list and not the other is a compile error here rather than a silently inert slot.
                setContent {
                    ReadingViewContent(
                        windowState = windowState,
                        commands = commands,
                        nightModeState = nightModeState,
                        generationState = generationState,
                        toolbar = toolbar,
                        toolbarCallbacks = toolbarCallbacks,
                        fullScreenState = fullScreenState,
                        overlayTextState = overlayTextState,
                        activeIsBibleShownState = activeIsBibleShownState,
                        overflowItemsState = overflowItemsState,
                        overflowExpandedState = overflowExpandedState,
                        onOverflowItemClick = onOverflowItemClick,
                        onOverflowDismiss = onOverflowDismiss,
                        drawerState = drawerState,
                        drawerOpenState = drawerOpenState,
                        drawerIcon = drawerIcon,
                        onDrawerItemClick = onDrawerItemClick,
                        monochromeState = monochromeState,
                        onDrawerInMotion = onDrawerInMotion,
                        onDrawerIdleClosed = onDrawerIdleClosed,
                        onDrawerClosed = onDrawerClosed,
                        pane = pane,
                        paneBackground = paneBackground,
                        windowLabel = windowLabel,
                        windowIcon = windowIcon,
                        windowTopLabel = windowTopLabel,
                        controller = controller,
                        windowButtonsVisibleState = windowButtonsVisibleState,
                        touchTickState = touchTickState,
                        onWindowButtonsHideTimeout = onWindowButtonsHideTimeout,
                        paneMenuWindowIdState = paneMenuWindowIdState,
                        paneMenuItemsState = paneMenuItemsState,
                        paneMenuAnchorState = paneMenuAnchorState,
                        onOpenPaneMenu = onOpenPaneMenu,
                        onPaneMenuItemClick = onPaneMenuItemClick,
                        onPaneMenuDismiss = onPaneMenuDismiss,
                        menuIcon = menuIcon,
                        llmDialogState = llmDialogState,
                        onLlmPromptChosen = onLlmPromptChosen,
                        onLlmToggleFavorite = onLlmToggleFavorite,
                        onLlmCategoryExpandedChanged = onLlmCategoryExpandedChanged,
                        onLlmSpecifySubmitted = onLlmSpecifySubmitted,
                        onLlmModelChosen = onLlmModelChosen,
                        onLlmRegenerateConfirmed = onLlmRegenerateConfirmed,
                        onLlmDismiss = onLlmDismiss,
                        agentLogSlot = agentLogSlot,
                        speakBarSlot = speakBarSlot,
                        speakDialogState = speakDialogState,
                        onSpeakBookmarkChosen = onSpeakBookmarkChosen,
                        onSpeakDialogDismiss = onSpeakDialogDismiss,
                        searchBarState = searchBarState,
                        searchBarCallbacks = searchBarCallbacks,
                        searchSheetVisibleState = searchSheetVisibleState,
                        onSearchSheetDismissed = onSearchSheetDismissed,
                        searchSheetSlot = searchSheetSlot,
                        searchSettingsSlot = searchSettingsSlot,
                        textSettingsEditorSlot = textSettingsEditorSlot,
                        speakSettingsSlot = speakSettingsSlot,
                        quickSheetSlot = quickSheetSlot,
                        readingDialogSlot = readingDialogSlot,
                        onSearchSheetOffsetsChanged = onSearchSheetOffsetsChanged,
                        searchUnavailableDocNameState = searchUnavailableDocNameState,
                        onSearchUnavailableMessageShown = onSearchUnavailableMessageShown,
                        agentLogVisibleState = agentLogVisibleState,
                        speakBarVisibleState = speakBarVisibleState,
                        imeBottomPaddingPxState = imeBottomPaddingPxState,
                    )
                }
            }
            container.addView(composeView)
        }

        /**
         * The reading view itself, as a composable -- everything [mountComposeView] used to hold
         * inside its `setContent { }`, and nothing else. Extracted by nav-graph slice 7 Task 6 so that
         * the `reading` DESTINATION (`ReadingNavGraph.kt` in `:sharedUi`) can render the reading view
         * with no [ViewGroup] anywhere in sight: a destination has a composition, not a container, and
         * [mountComposeView]'s only uses of its `container` were `ComposeView(container.context)` and
         * `container.addView(...)`.
         *
         * **A move, not a restructure** -- the plan's Global Constraints and design §10 keep
         * `install`/`mountComposeView` out of scope for this batch. The body below is
         * [mountComposeView]'s former `setContent` body verbatim, `AbAppTheme` wrapper included, and
         * [mountComposeView] still creates the same [ComposeView], gives it the same layout params and
         * adds it to the same container. What changed is who calls the content.
         *
         * **No parameter here has a default, deliberately.** [mountComposeView] keeps the inert
         * defaults its test callers rely on and forwards all 71 explicitly, so the two lists cannot
         * drift apart without a compile error at that call. That call site also stays the
         * authoritative documentation of what each slot is for: the per-parameter comments live on
         * [mountComposeView] and are deliberately NOT copied here, since a copy is a second place to
         * keep true.
         */
        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun ReadingViewContent(
            windowState: WindowStateServiceImpl,
            commands: WindowCommands,
            nightModeState: State<Boolean>,
            generationState: State<Int>,
            toolbar: StateFlow<ToolbarState>,
            toolbarCallbacks: ReadingToolbarCallbacks,
            fullScreenState: State<Boolean>,
            overlayTextState: State<String>,
            activeIsBibleShownState: State<Boolean>,
            overflowItemsState: State<List<OptionsMenuItem>>,
            overflowExpandedState: State<Boolean>,
            onOverflowItemClick: (id: String) -> Unit,
            onOverflowDismiss: () -> Unit,
            drawerState: State<DrawerMenuState>,
            drawerOpenState: MutableState<Boolean>,
            drawerIcon: @Composable (iconKey: String) -> Painter?,
            onDrawerItemClick: (id: String) -> Unit,
            monochromeState: State<Boolean>,
            onDrawerInMotion: () -> Unit,
            onDrawerIdleClosed: () -> Unit,
            onDrawerClosed: () -> Unit,
            pane: @Composable (windowId: String) -> Unit,
            paneBackground: (windowId: String) -> Color?,
            windowLabel: (WindowSnapshot) -> String,
            windowIcon: (WindowSnapshot) -> Painter?,
            windowTopLabel: (WindowSnapshot) -> String?,
            controller: ReadingViewController,
            windowButtonsVisibleState: State<Boolean>,
            touchTickState: State<Int>,
            onWindowButtonsHideTimeout: () -> Unit,
            paneMenuWindowIdState: State<String?>,
            paneMenuItemsState: State<List<WindowPaneMenuItem>>,
            paneMenuAnchorState: State<PaneMenuAnchor>,
            onOpenPaneMenu: (windowId: String, anchor: PaneMenuAnchor) -> Unit,
            onPaneMenuItemClick: (windowId: String, id: String) -> Unit,
            onPaneMenuDismiss: () -> Unit,
            menuIcon: @Composable (iconKey: String) -> Painter?,
            llmDialogState: StateFlow<ReadingLlmDialogState>,
            onLlmPromptChosen: (promptId: String) -> Unit,
            onLlmToggleFavorite: (promptId: String) -> Unit,
            onLlmCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit,
            onLlmSpecifySubmitted: (text: String) -> Unit,
            onLlmModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit,
            onLlmRegenerateConfirmed: (instructions: String?, keepPrevious: Boolean, freshRun: Boolean) -> Unit,
            onLlmDismiss: () -> Unit,
            agentLogSlot: (@Composable (applyNavBarInset: Boolean, maxHeightDp: Float, collapsedHeightDp: Float, onCollapsedHeightMeasured: (Float) -> Unit) -> Unit)?,
            speakBarSlot: (@Composable (applyNavBarInset: Boolean) -> Unit)?,
            speakDialogState: StateFlow<SpeakTransportDialog>,
            onSpeakBookmarkChosen: (id: String) -> Unit,
            onSpeakDialogDismiss: () -> Unit,
            searchBarState: StateFlow<ReadingSearchBarState?>,
            searchBarCallbacks: ReadingSearchBarCallbacks?,
            searchSheetVisibleState: StateFlow<Boolean>,
            onSearchSheetDismissed: () -> Unit,
            searchSheetSlot: @Composable () -> Unit,
            searchSettingsSlot: @Composable () -> Unit,
            textSettingsEditorSlot: @Composable () -> Unit,
            speakSettingsSlot: @Composable () -> Unit,
            quickSheetSlot: @Composable () -> Unit,
            readingDialogSlot: @Composable () -> Unit,
            onSearchSheetOffsetsChanged: (visible: Boolean, heightPx: Int) -> Unit,
            searchUnavailableDocNameState: StateFlow<String?>,
            onSearchUnavailableMessageShown: () -> Unit,
            agentLogVisibleState: @Composable () -> Boolean,
            speakBarVisibleState: @Composable () -> Boolean,
            // F59: the keyboard shrink the host's inset ledger computes
            // (`ReadingHostActivity.imeBottomPaddingPx`), a `State<Int>` for the same reactivity
            // reason as `fullScreenState` above. No default -- see this function's kdoc on why no
            // parameter here has one; [mountComposeView] carries the inert default.
            imeBottomPaddingPxState: State<Int>,
        ) {
            // AbAppTheme's darkTheme override (A/B batch 4b Task 3 fix round 1): this host
            // is long-lived inside MainBibleActivity and is never recreate()d on
            // ScreenSettings.nightModeChanges (including the ambient-light-sensor
            // auto-night-mode flip, which fires with no recreate at all), so it tracks night
            // mode itself in nightModeState and passes it through verbatim instead of
            // letting AbAppTheme re-read the static ScreenSettings.nightMode getter (which
            // would only catch up on some unrelated recomposition). See AbAppTheme's KDoc.
            AbAppTheme(darkTheme = nightModeState.value) {
              FailClosedLinkRouting {
                    val layout by controller.layout.collectAsState()
                    val toolbarState by toolbar.collectAsState()
                    val gen by generationState
                    val fullScreen by fullScreenState
                    val imeBottomPaddingPx by imeBottomPaddingPxState
                    val overflowItems by overflowItemsState
                    val overflowExpanded by overflowExpandedState
                    val tabBarModel = buildTabBarModel(layout)
                    val touchTick by touchTickState
                    val paneMenuWindowId by paneMenuWindowIdState
                    val paneMenuItems by paneMenuItemsState
                    val paneMenuAnchor by paneMenuAnchorState
                    // F6 Task 8a: `null` outside search mode, which is what makes
                    // `ReadingToolbar` draw its normal row.
                    val searchBar by searchBarState.collectAsState()
                    // Classic `resetTouchTimer`'s 2s hide countdown (`SplitBibleArea.kt:511-524`),
                    // restarted on every `touchTick` bump (a real touch, or `openPaneMenu`).
                    // Suppressed entirely while a pane menu is open (mirrors classic
                    // `showPopupMenu`/`menuHelper.setOnDismissListener` cancelling the timer
                    // for the popup's lifetime, `SplitBibleArea.kt:731-732, 854-855`) — Compose
                    // cancels/reruns this effect whenever either key changes, so closing the
                    // menu starts a fresh 2s countdown with no manual bookkeeping.
                    LaunchedEffect(touchTick, paneMenuWindowId) {
                        if (paneMenuWindowId == null) {
                            delay(2000)
                            onWindowButtonsHideTimeout()
                        }
                    }
                    val windowButtonsVisible by windowButtonsVisibleState
                    val overlayText by overlayTextState
                    val activeIsBibleShown by activeIsBibleShownState
                    // Batch 12g Task 3: pure gate (`:sharedCore`) mirroring classic
                    // `SplitBibleArea.updateBibleReferenceOverlay` — visible only fullscreen,
                    // with the active window showing a Bible, while the (auto-hiding) window
                    // buttons are shown, and the user hasn't disabled the overlay.
                    val overlayVisible = bibleReferenceOverlayVisible(
                        fullScreen = fullScreen,
                        activeIsBibleShown = activeIsBibleShown,
                        buttonsShown = windowButtonsVisible,
                        hideSetting = CommonUtils.settings.getBoolean("hide_bible_reference_overlay", false),
                    )
                    // Hard gates (classic `BibleFrame.addWindowButton`'s early-outs, screen/BibleFrame.kt:157-158):
                    // these genuinely remove the button. `windowButtonsVisible` is the 2s IDLE TIMER, which classic
                    // expresses as an ALPHA fade, not a removal (SplitBibleArea.kt:528-575) — so it is passed
                    // separately as `autoHidden` and must NOT be folded back in here.
                    //
                    // (never shown while `hide_window_buttons` is set, or while a window is maximised — the
                    // floating button, specifically; the per-window MENU can still open via the rail's
                    // unmaximise-button long-press; see `PaneWindowButtonOverlay`, which composes
                    // `WindowPaneMenu` unconditionally).
                    val showPaneButtons = layout.maximizedWindowId == null &&
                        !CommonUtils.settings.getBoolean("hide_window_buttons", false)
                    val paneButtonsAutoHidden = !windowButtonsVisible
                    // Classic `SplitBibleArea`'s fullscreen auto-hide
                    // (`autoHideWindowButtonBarInFullScreen`, `full_screen_hide_buttons_pref`,
                    // default ON, `SplitBibleArea.kt:166-171`/365-366) — in fullscreen with the
                    // pref on, drop the rail entirely (not merely collapsed, which is what
                    // `tabBarModel.showButtons` already handles for the non-fullscreen
                    // collapse toggle).
                    val hideTabBarInFullScreen = fullScreen &&
                        CommonUtils.settings.getBoolean("full_screen_hide_buttons_pref", true)
                    // Batch Z-early A6: the Compose navigation drawer. The wrap sits OUTSIDE
                    // `key(gen)`/`ReadingViewScreen` — never inside either — so (a) a
                    // `rebuild()` generation bump disposes only the pane subtree and not the
                    // drawer's own `DrawerState`, and (b) opening/closing the drawer cannot
                    // re-key or re-parent any pane's `AndroidView`, which would destroy and
                    // recreate every `BibleView` WebView. Same "add as an outer/sibling slot,
                    // never inside the pane subtree" rule the 12e agent-log and 12f speak-bar
                    // slots follow.
                    val md3DrawerState = rememberDrawerState(DrawerValue.Closed)

                    // Host request -> drawer.
                    LaunchedEffect(drawerOpenState.value) {
                        if (drawerOpenState.value) md3DrawerState.open() else md3DrawerState.close()
                    }
                    // Drawer -> host request, BOTH ways (Batch Z-early A7 fix D): a
                    // swipe/scrim/back close must clear the request, else the next
                    // `toggleDrawer()` would see a stale `true` and do nothing — and an open
                    // that did NOT come from the request (predictive-back cancel, any future
                    // gesture) must set it, else the next ☰ tap would re-request `true`, see
                    // no change, and look dead until pressed twice. `snapshotFlow` only emits
                    // on a CHANGE, so mirroring the settled value here cannot undo the
                    // `open()` above while the open animation is still running.
                    LaunchedEffect(md3DrawerState) {
                        snapshotFlow { md3DrawerState.isClosed }
                            .collect { closed -> drawerOpenState.value = !closed }
                    }
                    // Classic DrawerListener parity (Batch Z-early A7): in-motion vs
                    // idle-closed drive the system UI, and the transition TO closed restores
                    // focus into the active pane's BibleView.
                    LaunchedEffect(md3DrawerState) {
                        snapshotFlow { md3DrawerState.currentValue != md3DrawerState.targetValue }
                            .collect { inMotion -> if (inMotion) onDrawerInMotion() }
                    }
                    LaunchedEffect(md3DrawerState) {
                        snapshotFlow {
                            md3DrawerState.currentValue == md3DrawerState.targetValue &&
                                md3DrawerState.currentValue == DrawerValue.Closed
                        }.collect { idleClosed -> if (idleClosed) onDrawerIdleClosed() }
                    }
                    // Edge-detect the close: `DrawerCloseLatch` seeds from the initial
                    // snapshot and latches, so a (re)subscription replay cannot re-fire
                    // `requestFocus`. This is the banked one-shot-side-effect lesson from the
                    // earlier batches (a raw `collect {}` re-fires on every resubscribe).
                    LaunchedEffect(md3DrawerState) {
                        val latch = DrawerCloseLatch(
                            initiallyOpen = md3DrawerState.currentValue == DrawerValue.Open)
                        snapshotFlow { md3DrawerState.currentValue }.collect { value ->
                            if (latch.observe(value == DrawerValue.Open)) onDrawerClosed()
                        }
                    }

                    ModalNavigationDrawer(
                        drawerState = md3DrawerState,
                        // Batch Z-early A7 fix E: no drag-to-OPEN. Classic opens on a
                        // ~20dp EDGE drag; M3's `gesturesEnabled` drags anywhere over the
                        // content, which is a NEW gesture rather than parity — and the panes'
                        // WebViews swallow horizontal drags, so it would only ever work over
                        // the Compose chrome (toolbar, tab rail, inter-pane gaps), i.e.
                        // unpredictably. Gate on the open state rather than hardcoding
                        // `false`: in M3 1.4.0 `gesturesEnabled` ALSO gates the scrim's
                        // dismiss handler (its `onClose` lambda short-circuits on this flag),
                        // so disabling it outright silently kills tap-outside-to-dismiss too
                        // (classic's `DrawerLayout` always dismisses on a scrim tap). With
                        // `md3DrawerState.isOpen`, gestures stay off while closed (no
                        // drag-to-open) but turn on once open, restoring scrim-tap-to-dismiss
                        // and swipe-to-close parity with classic.
                        gesturesEnabled = md3DrawerState.isOpen,
                        // Classic `setupUi`: `if (monochromeMode) drawerLayout.setScrimColor(TRANSPARENT)`
                        // — no dimming on e-ink.
                        scrimColor = if (monochromeState.value) Color.Transparent
                                     else DrawerDefaults.scrimColor,
                        drawerContent = {
                            // A/B batch 1 F6: classic's `NavigationView` is `wrap_content`
                            // (~300dp in practice), while M3's `ModalDrawerSheet` defaults to
                            // a fixed 360dp — see `ReadingDrawerWidth`'s kdoc for why a fixed
                            // classic-scale width is used instead of reproducing wrap_content.
                            ModalDrawerSheet(modifier = Modifier.width(ReadingDrawerWidth)) {
                                ReadingDrawerContent(
                                    state = drawerState.value,
                                    icon = drawerIcon,
                                    onItemClick = { id ->
                                        drawerOpenState.value = false
                                        onDrawerItemClick(id)
                                    },
                                )
                            }
                        },
                    ) {
                        // F6 Task 0: the search sheet's scaffold. UNCONDITIONALLY present
                        // and ENCLOSING `key(gen)` — the same "outer wrap, never inside the
                        // pane subtree" rule the drawer above follows. Conditionality, not
                        // the wrapper, is what would re-parent every pane's `AndroidView`
                        // and so destroy and recreate every `BibleView` WebView on each
                        // search open/close. `skipHiddenState = false` is what makes
                        // "always present" and "fully closable" compatible: the sheet
                        // reaches `SheetValue.Hidden` while the scaffold never goes away.
                        // Task 8a fills `sheetContent` (the index prompt/progress panel, or
                        // the results) and drives the sheet from the session's
                        // `sheetVisible` — see [DriveSearchSheet], which also carries why
                        // `expand()` and not `partialExpand()` is what shows it.
                        val searchSheetState = rememberBottomSheetScaffoldState(
                            bottomSheetState = rememberStandardBottomSheetState(
                                initialValue = SheetValue.Hidden,
                                skipHiddenState = false,
                            ),
                        )
                        val searchSheetVisible by searchSheetVisibleState.collectAsState()
                        DriveSearchSheet(searchSheetState, searchSheetVisible, onSearchSheetDismissed)
                        // Task 10: the "<document> cannot be searched" snackbar. Uses the
                        // reading view's own scaffold's `snackbarHost` slot below — the one
                        // `BottomSheetScaffold` this host mounts — rather than a second one
                        // built just for this. `LocalStrings.current` is available here
                        // because this whole tree sits inside `AbAppTheme`'s
                        // `ProvideAppLocals`; `onUnavailable` itself fires outside
                        // composition, which is why it only records the document name and
                        // leaves formatting to this composable.
                        val searchSnackbarHostState = remember { SnackbarHostState() }
                        val searchUnavailableDocName by searchUnavailableDocNameState.collectAsState()
                        DriveSearchUnavailableSnackbar(
                            snackbarHostState = searchSnackbarHostState,
                            docName = searchUnavailableDocName,
                            strings = LocalStrings.current,
                            onShown = onSearchUnavailableMessageShown,
                        )
                        // Task 8b Step 3: the sheet is self-sizing (see the 1.dp-floor/60%-
                        // ceiling comment below), so its height for `bottomOffsetForWebView`
                        // must be MEASURED off the real content, not guessed as a constant —
                        // `onSizeChanged` reports each piece in px. Review Important 3: the
                        // on-screen sheet is the CONTENT plus M3's own drag-handle band, which
                        // `BottomSheetScaffold` renders as a SEPARATE composable above
                        // `sheetContent` — measuring only the content under-counted the sheet
                        // by the handle's height, leaving that much of the reading text hidden
                        // behind it. Rather than hardcode the default handle's height (a
                        // constant M3 could change under us), `sheetDragHandle` below is
                        // supplied explicitly so its own real height is measured the same way.
                        var searchSheetContentHeightPx by remember { mutableIntStateOf(0) }
                        var searchSheetHandleHeightPx by remember { mutableIntStateOf(0) }
                        LaunchedEffect(searchSheetVisible, searchSheetContentHeightPx, searchSheetHandleHeightPx) {
                            onSearchSheetOffsetsChanged(
                                searchSheetVisible,
                                searchSheetContentHeightPx + searchSheetHandleHeightPx,
                            )
                        }
                        BottomSheetScaffold(
                            scaffoldState = searchSheetState,
                            sheetPeekHeight = 0.dp,
                            // BottomSheetScaffold applies no window insets (material3 1.4.0) and this tree is
                            // edge-to-edge: clear the nav bar / cutout ourselves.
                            snackbarHost = { SnackbarHost(searchSnackbarHostState, Modifier.readingSheetInsetPadding()) },
                            sheetDragHandle = {
                                Box(Modifier.onSizeChanged { size -> searchSheetHandleHeightPx = size.height }) {
                                    BottomSheetDefaults.DragHandle()
                                }
                            },
                            sheetContent = {
                                // The sheet is exactly as tall as its content — a short
                                // index prompt stays short — but never taller than
                                // [SearchSheetMaxHeightFraction] of the window, so the
                                // reading text it was opened from stays visible above it.
                                // `BoxWithConstraints` supplies that ceiling in Dp without
                                // reaching for the (Android-only) configuration.
                                //
                                // The 1.dp FLOOR is load-bearing: an empty sheet measures to
                                // zero, and M3 publishes no `Expanded` anchor for a
                                // zero-height sheet at all — so an `expand()` that lands
                                // before the content's first layout pass would be a silent
                                // no-op. Keeping a hairline sheet keeps the anchor alive at
                                // all times (invisible: at 1.dp the sheet sits a pixel off
                                // the bottom edge, and `Hidden` is where it rests anyway).
                                //
                                // The nav-bar/cutout padding is OUTSIDE the measured Box on purpose:
                                // `searchSheetContentHeightPx` feeds `bottomOffsetForWebView`, which
                                // already adds the nav bar (ReadingInsets), so measuring it here would
                                // reserve it twice.
                                BoxWithConstraints(Modifier.fillMaxWidth().readingSheetInsetPadding()) {
                                    Box(
                                        Modifier
                                            .heightIn(
                                                min = 1.dp,
                                                max = maxHeight * SearchSheetMaxHeightFraction,
                                            )
                                            .onSizeChanged { size -> searchSheetContentHeightPx = size.height }
                                    ) { searchSheetSlot() }
                                }
                            },
                        ) { _ ->
                            // Keying the whole screen on `gen` forces every pane's `AndroidView`
                            // factory to re-run on `rebuild()` — see the `generation` kdoc above.
                            key(gen) {
                                ReadingViewScreen(
                                    layout = layout,
                                    toolbar = toolbarState,
                                    toolbarIcons = readingToolbarIcons(),
                                    toolbarCallbacks = toolbarCallbacks,
                                    fullScreen = fullScreen,
                                    // F59: the keyboard shrink the host's inset ledger computed,
                                    // applied as a plain bottom padding on the reading column (spec
                                    // §3.3) -- see ReadingHostActivity.imeBottomPaddingPx's kdoc.
                                    // Threaded in as a State parameter (imeBottomPaddingPxState),
                                    // same shape as fullScreenState above: this composable is the
                                    // companion object's, with no `activity` of its own to read.
                                    imeBottomPadding = with(LocalDensity.current) {
                                        imeBottomPaddingPx.toDp()
                                    },
                                    onWindowActivated = controller::onWindowActivated,
                                    onSeparatorCommitted = controller::onSeparatorCommitted,
                                    pane = pane,
                                    paneBackground = paneBackground,
                                    // F107: read in composition, so it follows the active window
                                    // and night/monochrome as the panes do.
                                    edgeBackground = paneBackground(layout.activeWindowId),
                                    // F6 Task 8a: a non-null pair replaces the toolbar's
                                    // normal row with the search field (Task 4).
                                    searchBar = searchBar,
                                    searchBarCallbacks = searchBarCallbacks,
                                    overflowItems = overflowItems,
                                    overflowExpanded = overflowExpanded,
                                    onOverflowItemClick = onOverflowItemClick,
                                    onOverflowDismiss = onOverflowDismiss,
                                    overflowIcon = menuIcon,
                                    paneOverlay = { windowId ->
                                        val window = layout.windows.firstOrNull { it.id == windowId }
                                        PaneWindowButtonOverlay(
                                            windowId = windowId,
                                            window = window,
                                            isActive = windowId == layout.activeWindowId,
                                            showButton = showPaneButtons,
                                            autoHidden = paneButtonsAutoHidden,
                                            autoPin = layout.autoPin,
                                            // Currently unreachable as true: `showPaneButtons` (passed as
                                            // `showButton` below) is a SINGLE value shared by every window
                                            // and is false whenever ANY window is maximised, so
                                            // `PaneWindowButtonOverlay` never composes `WindowButton` (and
                                            // therefore never evaluates `shouldShowPinIndicator` with this
                                            // argument) while `layout.maximizedWindowId == windowId` could
                                            // hold. Harmless, and kept per-window (not hard-coded false)
                                            // because it is the correct value if a future change ever composes
                                            // pane buttons during maximise.
                                            isMaximised = layout.maximizedWindowId == windowId,
                                            nightMode = nightModeState.value,
                                            disableAnimations = CommonUtils.settings.disableAnimations,
                                            monochrome = monochromeState.value,
                                            // A/B batch 3 F5b: this surface only reports a menu open
                                            // when it (not the rail) is the anchor — see [menuWindowIdFor].
                                            paneMenuWindowId = menuWindowIdFor(PaneMenuAnchor.Pane, paneMenuAnchor, paneMenuWindowId),
                                            paneMenuItems = paneMenuItems,
                                            controller = controller,
                                            onOpenPaneMenu = onOpenPaneMenu,
                                            onPaneMenuItemClick = onPaneMenuItemClick,
                                            onPaneMenuDismiss = onPaneMenuDismiss,
                                            icon = menuIcon,
                                        )
                                    },
                                    agentLog = agentLogSlot,
                                    speakBar = speakBarSlot,
                                    agentLogVisible = agentLogVisibleState(),
                                    speakBarVisible = speakBarVisibleState(),
                                    bottomOverlay = { BibleReferenceOverlay(visible = overlayVisible, text = overlayText) },
                                    tabBar = if (hideTabBarInFullScreen) null else {
                                        { applyNavBarInset ->
                                            WindowTabBar(
                                                // Fix batch 2 (F66/F67): ReadingViewScreen passes true only when the
                                                // strip is the bottom-most surface and the column has no IME padding.
                                                modifier = Modifier.readingRailInsetPadding(applyNavBarInset),
                                                model = tabBarModel,
                                                onRestore = controller::onRestore,
                                                // Plan B Task 5: a rail long-press now opens the SAME
                                                // per-window ☰ menu as tapping the floating pane button.
                                                // Final-review fix: activate the window first, mirroring
                                                // classic `SplitBibleArea.showPopupMenu`'s
                                                // `if (window.isVisible) windowControl.activeWindow = window`
                                                // and the floating ☰ button's own gestures (both of which
                                                // activate before opening the menu).
                                                onWindowLongPress = { id ->
                                                    controller.onWindowActivated(id)
                                                    onOpenPaneMenu(id, PaneMenuAnchor.Rail)
                                                },
                                                // A/B batch 3 F5b: the rail renders its OWN anchored
                                                // `WindowPaneMenu` (Task 11) — only when the rail (not
                                                // the pane overlay) is the anchor. See [menuWindowIdFor].
                                                menuWindowId = menuWindowIdFor(PaneMenuAnchor.Rail, paneMenuAnchor, paneMenuWindowId),
                                                menuItems = paneMenuItems,
                                                onMenuItemClick = onPaneMenuItemClick,
                                                onMenuDismiss = onPaneMenuDismiss,
                                                menuIcon = menuIcon,
                                                onAddWindow = { controller.onAddWindow(layout.activeWindowId) },
                                                onUnMaximise = controller::onUnMaximise,
                                                onToggleCollapse = {
                                                    controller.onSetRestoreButtonsVisible(!layout.restoreButtonsVisible)
                                                },
                                                windowLabel = windowLabel,
                                                windowIcon = windowIcon,
                                                windowTopLabel = windowTopLabel,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                    // Sibling of `ReadingViewScreen` (not nested inside `key(gen)`, which
                    // only needs to scope the panes' `AndroidView` factories) — an
                    // `AlertDialog` AND a `ModalBottomSheet` each overlay regardless of
                    // where in the tree they are composed, and there is at most one
                    // non-`None` arm at a time. Round 14a made two of the four arms sheets,
                    // so this is now one of the reading view's modal-sheet overlays and is
                    // subject to `ReadingOverlayExclusion` — applied where
                    // `readingLlmDialogs` is constructed, not here.
                    val llmDialog by llmDialogState.collectAsState()
                    ReadingLlmDialogs(
                        dialog = llmDialog.dialog,
                        onPromptChosen = onLlmPromptChosen,
                        onToggleFavorite = onLlmToggleFavorite,
                        onCategoryExpandedChanged = onLlmCategoryExpandedChanged,
                        onSpecifySubmitted = onLlmSpecifySubmitted,
                        onModelChosen = onLlmModelChosen,
                        onRegenerateConfirmed = onLlmRegenerateConfirmed,
                        onDismiss = onLlmDismiss,
                    )
                    // Sibling overlay next to `ReadingLlmDialogs` above (same reasoning:
                    // not nested inside `key(gen)`, at most one non-`None` dialog at a
                    // time) — the Batch 12f speak-from-bookmark chooser.
                    val speakDialog by speakDialogState.collectAsState()
                    (speakDialog as? SpeakTransportDialog.ChooseSpeakBookmark)?.let { d ->
                        ChooseSpeakBookmarkDialog(
                            rows = d.rows,
                            onChoose = onSpeakBookmarkChosen,
                            onDismiss = onSpeakDialogDismiss,
                        )
                    }
                    // F6 Task 8a: the modal search-settings sheet — a fourth sibling
                    // overlay, for the same reason as the three above: a `ModalBottomSheet`
                    // renders in its own window regardless of where it is composed, so
                    // opening it can never re-key the pane subtree. It self-hides when
                    // closed (`SearchSettingsSheet` returns early), so this stays
                    // unconditional here.
                    searchSettingsSlot()
                    // Settings editor sheets T10: the in-place text-settings editor sheet —
                    // a fifth sibling overlay, for the same reason as the four above: a
                    // `ModalBottomSheet` renders in its own window regardless of where it is
                    // composed, so opening it can never re-key the pane subtree and destroy
                    // the panes' BibleView WebViews. It self-hides when closed, so this
                    // stays unconditional here too.
                    textSettingsEditorSlot()
                    // Round 13a: the Speak settings sheet — a sixth sibling overlay, same
                    // reason as the five above, and self-hiding when closed.
                    speakSettingsSlot()
                    // Round 15b: the quick sheets — a seventh sibling overlay, same reason
                    // as the six above, and self-hiding when closed.
                    quickSheetSlot()
                    // Platform-dialog removal Task 18: the reading view's own dialogs — an
                    // eighth sibling overlay, self-hiding when closed like the seven above.
                    readingDialogSlot()
              }
            }
        }
    }
}

/**
 * How much of the window the reading-view search sheet may take at most (F6 Task 8a). Its content
 * is otherwise self-sizing, so a short index prompt does not become a half-screen panel.
 */
private const val SearchSheetMaxHeightFraction = 0.6f

/** Bound for [DriveSearchSheet]'s expand retry — ~10 × 50ms, i.e. well under a second. */
private const val SearchSheetExpandAttempts = 10
private const val SearchSheetExpandRetryMillis = 50L

/**
 * Keeps the (always-present) search sheet in step with the session's `sheetVisible`, both ways
 * (F6 Task 8a Step 3).
 *
 * Its own function rather than two `LaunchedEffect`s inline in `mountComposeView` for two reasons:
 * the conditional inside it would otherwise sit directly above the `BottomSheetScaffold` call,
 * which `SearchSheetStructureGuardTest` reads as the scaffold becoming conditional; and the two
 * non-obvious mechanics below deserve one place to be explained.
 *
 * **`expand()`, not `partialExpand()`.** With `sheetPeekHeight = 0.dp` the `PartiallyExpanded`
 * anchor sits at `layoutHeight - peekHeight`, i.e. exactly where `Hidden` sits — so a partial
 * expand animates to an invisible sheet, and `show()` (which prefers `PartiallyExpanded` whenever
 * that anchor exists) would do the same. `Expanded` puts the sheet at its own content height, which
 * with the cap in `sheetContent` is what makes both the short index panel and the tall result list
 * look right. (The plan's snippet said `partialExpand()`; it would have shown nothing.)
 *
 * **The expand is retried.** The `Expanded` anchor is published while the sheet is MEASURED, and
 * this effect can run before that layout pass, in which case the animation is a no-op and the sheet
 * stays hidden with the session believing otherwise. Bounded retries close that window; the 1.dp
 * floor on the sheet content (see `sheetContent`) makes it a narrow one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DriveSearchSheet(
    scaffoldState: BottomSheetScaffoldState,
    visible: Boolean,
    onDismissedByUser: () -> Unit,
) {
    val sheet = scaffoldState.bottomSheetState
    LaunchedEffect(visible) {
        if (visible) {
            repeat(SearchSheetExpandAttempts) {
                if (sheet.currentValue == SheetValue.Expanded) return@LaunchedEffect
                sheet.expand()
                if (sheet.currentValue != SheetValue.Expanded) delay(SearchSheetExpandRetryMillis)
            }
        } else if (sheet.currentValue != SheetValue.Hidden) {
            sheet.hide()
        }
    }
    // A sheet dragged away must reach the session too: otherwise `sheetVisible` stays true, the
    // effect above never re-fires, and the next `open()` for the same query looks dead. Anything
    // other than `Expanded` is invisible here (see above), so it all counts as dismissed — and the
    // initial `Hidden` emission is harmless (`closeSheet()` returns false when nothing is open).
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.currentValue }.collect { if (it != SheetValue.Expanded) onDismissedByUser() }
    }
}

/**
 * Task 10: turns [ComposeReadingViewHost.searchUnavailableDocName] into a real `Snackbar`. [docName]
 * transitions null -> name -> null (host clears it via [onShown] the moment this effect starts, not
 * when the snackbar is dismissed) so a second `open()` on the same kind of document — even with the
 * identical name — still restarts the effect and shows again, the same way [SnackbarHostState] itself
 * replaces a currently-shown snackbar with a newly requested one.
 */
@Composable
private fun DriveSearchUnavailableSnackbar(
    snackbarHostState: SnackbarHostState,
    docName: String?,
    strings: Strings,
    onShown: () -> Unit,
) {
    LaunchedEffect(docName) {
        if (docName == null) return@LaunchedEffect
        onShown()
        snackbarHostState.showSnackbar(ComposeReadingViewHost.searchUnavailableMessage(docName, strings))
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
 * [ReadingToolbarIcons] for [ComposeReadingViewHost.mountComposeView] — the same
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

/**
 * No-op [ReadingToolbarCallbacks] used only as the default parameter value for
 * [ComposeReadingViewHost.mountComposeView] — keeps host-agnostic tests (e.g.
 * `ComposeReadingViewHostTest`, which never exercises button/gesture interaction) compiling
 * without having to build a full [ReadingToolbarCallbacks]. [ComposeReadingViewHost.install]
 * always supplies the real, activity-wired callbacks instead.
 */
private val noopToolbarCallbacks = ReadingToolbarCallbacks(
    onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
    onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
    onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
    onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
)

/**
 * One pane's floating ☰ button + its [WindowPaneMenu] popup — the `paneOverlay` content
 * [ComposeReadingViewHost.mountComposeView] passes to [ReadingViewScreen] (invoked inside each
 * visible pane's `Box`, see `SplitContent`'s kdoc). Compose port of classic `BibleFrame`'s
 * per-pane window button (`screen/BibleFrame.kt:156-194`) + `WindowButtonGestureListener`
 * (`:43-83`).
 *
 * The button itself is gated by [showButton] (`hide_window_buttons` / maximised — see
 * `showPaneButtons` at the call site) — those are classic's two HARD removals
 * (`screen/BibleFrame.kt:157-158`) — but [WindowPaneMenu] is ALWAYS composed as its sibling: a menu
 * opened via the always-visible restore rail's long-press must still be able to render anchored to
 * this pane even when the floating button itself is hidden (see
 * [ComposeReadingViewHost.openPaneMenu]'s kdoc).
 *
 * The 2s idle timer ([autoHidden]) is NOT a third hard gate: classic fades the button to
 * [net.bible.sharedcore.reading.paneButtonHiddenAlpha] rather than removing it
 * (`SplitBibleArea.toggleWindowButtonVisibility`, `SplitBibleArea.kt:528-575`), so it stays
 * present and tappable while faded — [Modifier.alpha] only, never a size/visibility change.
 *
 * Gestures (classic `WindowButtonGestureListener` dispatch, `screen/BibleFrame.kt:185-191`): tap →
 * [onOpenPaneMenu]; long-press → minimise; swipe-up → maximise; swipe-down → minimise. Every
 * gesture activates the pane first (classic + iOS `performPaneWindowButtonAction`).
 */
@Composable
private fun BoxScope.PaneWindowButtonOverlay(
    windowId: String,
    window: WindowSnapshot?,
    isActive: Boolean,
    showButton: Boolean,
    autoHidden: Boolean,
    autoPin: Boolean,
    isMaximised: Boolean,
    nightMode: Boolean,
    disableAnimations: Boolean,
    monochrome: Boolean,
    paneMenuWindowId: String?,
    paneMenuItems: List<WindowPaneMenuItem>,
    controller: ReadingViewController,
    onOpenPaneMenu: (windowId: String, anchor: PaneMenuAnchor) -> Unit,
    onPaneMenuItemClick: (windowId: String, id: String) -> Unit,
    onPaneMenuDismiss: () -> Unit,
    // A/B batch 1 F5b: resolves each row's `WindowPaneMenuItem.iconKey` to a `Painter` — same
    // host-lambda seam as `drawerIcon` (see [mountComposeView]'s `menuIcon` param).
    icon: @Composable (iconKey: String) -> Painter? = { null },
) {
    Box(Modifier.align(Alignment.TopEnd)) {
        if (showButton && window != null) {
            var accumDy by remember(windowId) { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val thresholdPx = remember(density) { with(density) { PaneButtonDragThresholdDp.toPx() } }
            // Classic's idle-timer fade (`SplitBibleArea.kt:528-575`), NOT a removal from
            // composition — see this function's kdoc. `targetAlpha` picks the button's resting
            // state; `animateFloatAsState` supplies the tween classic drives via `ViewPropertyAnimator`.
            val targetAlpha = if (autoHidden) {
                paneButtonHiddenAlpha(nightMode, disableAnimations, monochrome)
            } else 1f
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(
                    durationMillis = paneButtonFadeMillis(disableAnimations),
                    // Classic uses DecelerateInterpolator on show and AccelerateInterpolator on
                    // hide (SplitBibleArea.kt:552,557); these are their M3 equivalents.
                    easing = if (autoHidden) FastOutLinearInEasing else LinearOutSlowInEasing,
                ),
                label = "paneButtonAlpha",
            )
            WindowButton(
                label = "☰",
                isActive = isActive,
                isMinimised = false,
                isPinned = shouldShowPinIndicator(
                    isPinMode = window.isPinMode,
                    autoPin = autoPin,
                    isMaximised = isMaximised,
                ),
                isLinks = window.isLinksWindow,
                syncGroup = if (window.isSynchronised) window.syncGroup + 1 else 0,
                mode = WindowButtonMode.Pane,
                onClick = {
                    controller.onWindowActivated(windowId)
                    onOpenPaneMenu(windowId, PaneMenuAnchor.Pane)
                },
                onLongPress = {
                    controller.onWindowActivated(windowId)
                    controller.onMinimise(windowId)
                },
                modifier = Modifier
                    .alpha(alpha)
                    .pointerInput(windowId) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when (paneButtonDragAction(accumDy, thresholdPx)) {
                                    PaneButtonAction.Maximise -> {
                                        controller.onWindowActivated(windowId)
                                        controller.onMaximise(windowId)
                                    }
                                    PaneButtonAction.Minimise -> {
                                        controller.onWindowActivated(windowId)
                                        controller.onMinimise(windowId)
                                    }
                                    PaneButtonAction.None -> {}
                                }
                                accumDy = 0f
                            },
                            onDragCancel = { accumDy = 0f },
                        ) { change, dragAmount -> change.consume(); accumDy += dragAmount }
                    },
            )
        }
        WindowPaneMenu(
            items = if (paneMenuWindowId == windowId) paneMenuItems else emptyList(),
            expanded = paneMenuWindowId == windowId,
            onItemClick = { id -> onPaneMenuItemClick(windowId, id) },
            onDismiss = onPaneMenuDismiss,
            icon = icon,
        )
    }
}
