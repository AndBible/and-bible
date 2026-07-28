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

import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.ToastEvent
import net.bible.android.control.event.onMain
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.event.window.CurrentWindowChangedEvent
import net.bible.android.control.page.CurrentBibleVerseChanged
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.ai.RawLlmLogActivity
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.AgentForegroundService
import net.bible.service.sword.SwordDocumentFacade
import net.bible.sharedcore.ai.AgentPermissionChoice
import net.bible.sharedcore.ai.AgentPermissionController
import net.bible.sharedcore.ai.AgentPermissionRequest
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingLlmDialogController
import net.bible.sharedcore.ai.reading.ReadingLlmDialogState
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.reading.DrawerCloseLatch
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.PaneButtonAction
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.reading.bibleReferenceOverlayVisible
import net.bible.sharedcore.reading.paneButtonDragAction
import net.bible.sharedcore.reading.paneButtonFadeMillis
import net.bible.sharedcore.reading.paneButtonHiddenAlpha
import net.bible.sharedcore.speak.SpeakSettingsService
import net.bible.sharedcore.speak.SpeakTransportController
import net.bible.sharedcore.speak.SpeakTransportDialog
import net.bible.sharedcore.speak.SpeakTransportService
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AgentPermissionDialog
import net.bible.sharedui.ai.reading.AgentLogPanel
import net.bible.sharedui.ai.reading.ReadingLlmDialogs
import net.bible.sharedui.reading.BibleReferenceOverlay
import net.bible.sharedui.reading.ChooseSpeakBookmarkDialog
import net.bible.sharedui.reading.QuickDocMenuState
import net.bible.sharedui.reading.ReadingDrawerContent
import net.bible.sharedui.reading.ReadingDrawerWidth
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.SpeakTransportBar
import net.bible.sharedui.reading.WindowButton
import net.bible.sharedui.reading.WindowButtonMode
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.theme.AbTheme
import org.crosswire.jsword.book.BookCategory
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
 * setter (or `MainBibleAfterRestore`) reloads the SAME workspace, it does
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
 * Fans a "classic said this may have changed" signal out to the Compose reading view's state
 * (pre-A/B state-freshness spec §1 P3).
 *
 * Every Compose state bridge subscribes to a *guessed set* of [net.bible.android.control.event.ABEventBus]
 * events and rebuilds its snapshot from the domain, so a mutation that posts none of them leaves
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
 * [transportVisible] arrives from `SpeakTransportVisibilityChanged`, which the
 * `transportBarVisible` **setter** posts with the raw backing field — its getter's
 * `if (isFullScreen) false` mask is NOT applied before posting, and `toggleFullScreen()` posts
 * only `FullScreenEvent`. So without re-applying the fullscreen half here, the bar stayed on
 * screen in fullscreen on the Compose path while classic animated it away (pre-A/B spec §1 P3).
 *
 * A pure function so it is unit-testable — see `SpeakBarVisibilityTest`.
 */
internal fun speakBarVisible(fullScreen: Boolean, transportVisible: Boolean): Boolean =
    !fullScreen && transportVisible

/**
 * Whether the classic native bottom chrome — [net.bible.android.view.util.widget.AgentLogWidget]
 * and `MainBibleActivity`'s classic `speakTransport` bar — is allowed to make itself visible.
 * `false` on the Compose path, where `ReadingViewScreen`'s `agentLog`/`speakBar` slots (see
 * [agentLog]/[speakTransport] below, wired as `agentLogSlot`/`speakBarSlot` in [install]) are the
 * ones that own this chrome; left showing, the classic views would draw ON TOP of their Compose
 * replacements, since both are declared AFTER the Compose container in `main_bible_view.xml` and
 * anchored to the parent bottom (pre-A/B state-freshness spec §1 P3, Task 5).
 *
 * A pure function, mirroring [speakBarVisible] above, so this decision is unit-testable even at
 * its `AgentLogWidget` call site — a real `View` with no Robolectric test in this repo — see
 * `ClassicBottomChromeAllowedTest`.
 */
internal fun classicBottomChromeAllowed(composeHosted: Boolean): Boolean = !composeHosted

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
 * [BibleView.BibleViewTouched] in [ComposeReadingViewHost.init], and also called by
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
 * Mounts the Compose reading view into [MainBibleActivity]'s content, replacing the classic
 * `SplitBibleArea` build (see [DocumentViewManager]'s `use_compose_ui` guard) when
 * `use_compose_ui` is on. Plan A kept the classic toolbar/drawer chrome; Plan B (this task) hosts
 * the Compose `ReadingToolbar` instead: [install] hides the classic `toolbarLayout`/
 * `toolbarDivider` and re-anchors [container] (`binding.mainBibleView`) from below the divider to
 * the parent top, so the Compose toolbar — which applies its own
 * `Modifier.windowInsetsPadding(WindowInsets.statusBars)` — owns the top inset instead. The drawer
 * (`binding.drawerLayout`) stays a classic `View`; only the toolbar row moves into Compose. Each
 * pane hosts the window's existing [net.bible.android.view.activity.page.BibleView] via
 * [AndroidView] wrapping [MainBibleActivity.bibleViewFactory] — the WebView/JS bridge stays an
 * unmodified black box.
 */
class ComposeReadingViewHost(private val activity: MainBibleActivity) : KoinComponent {
    private val windowState: WindowStateServiceImpl by inject()
    private val commands: WindowCommands by inject()
    private val toolbarStateService: ToolbarStateService by inject()
    private val readingLlmService: ReadingLlmService by inject()
    private val agentSessionService: AgentSessionService by inject()
    private val speakTransportService: SpeakTransportService by inject()
    private val speakSettingsService: SpeakSettingsService by inject()

    /**
     * The app-wide runtime agent tool-permission bridge (Z-early B4). A Koin `single` (see
     * `CoreModule`), NOT host-owned state: `AgentExecutor` asks from a foreground service's
     * coroutine and must outlive any single activity, so an in-flight request survives an activity
     * recreation and is picked up by whichever host is installed at the time.
     */
    private val permissions: AgentPermissionController by inject()

    /** Owns [readingLlmDialogs]' coroutine work (dialog open/execute/dismiss). Cancelled in
     *  [dispose] — one host per activity (re-)creation, mirroring the [ABEventBus] registration
     *  lifecycle right below. */
    private val hostScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * State machine driving the reading-view LLM dialogs (Batch 12e-A Task 6): prompt selector,
     * specify-before-run, model selection, regenerate-confirm. Not `private` so
     * `ComposeReadingViewHostTest`-style tests can assert against it directly, mirroring
     * [controller]'s visibility below. Rendered by [mountComposeView] (see [install]) as a sibling
     * of `ReadingViewScreen`; its `onExecute`/`onRegenerate` callbacks are wired by
     * [showPromptSelector]/[showRegenerate].
     */
    val readingLlmDialogs = ReadingLlmDialogController(readingLlmService, hostScope)

    /**
     * State holder for the reading-view agent-log panel (Batch 12e-B Task 6) — the compose-path
     * counterpart of classic `AgentLogWidget`. Not `private`, mirroring [readingLlmDialogs]/
     * [controller] below, so `AgentLogHostTest`-style tests can assert against it directly.
     * [onCompletedToast]/[onOpenRawLog] are verbatim mirrors of classic `AgentLogWidget`'s
     * `ABEventBus.post(ToastEvent(R.string.ai_task_completed))` / `openRawLog()`. Rendered by
     * [mountComposeView] (see [install]) as `ReadingViewScreen`'s `agentLog` slot (Task 5).
     */
    val agentLog = AgentLogController(
        agentSessionService,
        hostScope,
        onCompletedToast = { ABEventBus.post(ToastEvent(R.string.ai_task_completed)) },
        onOpenRawLog = {
            val intent = ScreenLauncher.intentFor(activity, Screen.RawLlmLog).apply {
                putExtra(RawLlmLogActivity.EXTRA_WORKSPACE_ID, agentSessionService.currentWorkspaceId())
            }
            activity.startActivity(intent)
        },
    )

    /**
     * State holder for the reading-view Speak transport bar (Batch 12f Task 6) — the compose-path
     * counterpart of classic `SpeakTransportWidget`. Not `private`, mirroring [readingLlmDialogs]/
     * [agentLog] above, for the same test-visibility reason. Visibility flows entirely from
     * [speakTransportService] (bridged from [MainBibleActivity.transportBarVisible] via
     * `SpeakTransportVisibilityChanged`), NOT from a host-owned flag — see [onConfig] below, which
     * is the only host-supplied seam (launches [Screen.BibleSpeak], mirroring the classic bar's
     * settings-cog button). Rendered by [mountComposeView] (see [install]) as `ReadingViewScreen`'s
     * `speakBar` slot (Task 5).
     */
    val speakTransport = SpeakTransportController(
        speakTransportService,
        speakSettingsService,
        hostScope,
        onConfig = { activity.startActivity(ScreenLauncher.intentFor(activity, Screen.BibleSpeak)) },
    )

    private val generation = ComposeReadingViewGeneration()

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
     * Builds the per-window (☰) pane popup menu's item list (Task 4) — constructed from the same
     * `windowControl`/`speakControl` [activity] already exposes, mirroring how classic
     * `SplitBibleArea.getItemOptions` closes over its own `mainBibleActivity`'s collaborators.
     */
    private val paneMenuStateBuilder = WindowPaneMenuStateBuilder(activity.windowControl, activity.speakControl)

    /**
     * The windowId whose ☰ menu is currently open, or `null` when closed — host-owned so the
     * popup survives recomposition, mirroring [overflowExpanded]/[overflowItems] below. Opened by
     * [openPaneMenu] (the pane overlay's ☰-button tap, or the Plan-A restore rail's long-press);
     * closed by [closePaneMenu] (`onDismiss`, or after a non-toggle item acts).
     */
    private val paneMenuWindowId = mutableStateOf<String?>(null)
    private val paneMenuItems = mutableStateOf(emptyList<WindowPaneMenuItem>())

    /**
     * Mirrors [ScreenSettings.nightMode]. Kept current via [ScreenSettings.NightModeChanged] (see
     * [init]) instead of being captured once at [install] time, which is what made the host's
     * `AbTheme` non-reactive to a runtime night-mode flip (the Plan-A carry-forward this task
     * closes — see the whole-Plan-A review Minor).
     */
    private val nightMode = mutableStateOf(ScreenSettings.nightMode)

    /**
     * Mirrors `CommonUtils.settings.monochromeMode` — the compose-drawer counterpart of classic
     * `setupUi`'s `if (monochromeMode) drawerLayout.setScrimColor(TRANSPARENT)` (this is the only
     * thing that read it on the drawer path). There is no dedicated change event for it, so it is
     * refreshed alongside [nightMode] on [ScreenSettings.NightModeChanged] (see [init]) — the
     * closest thing this codebase has to a "display appearance changed" signal, and the same event
     * the e-ink/monochrome device path posts.
     */
    private val monochrome = mutableStateOf(CommonUtils.settings.monochromeMode)

    /**
     * Mirrors [MainBibleActivity.fullScreen]. Kept current via [MainBibleActivity.FullScreenEvent]
     * (see [init]) so entering/leaving fullscreen from ANY path — the Compose overflow menu's
     * "Full screen" row (Batch 12b-C Task 3, dispatched via [MainBibleActivity.handleOptionsMenuItem]),
     * the classic native options menu reached via the hardware menu key / `BibleJavascriptInterface`
     * (still [MainBibleActivity.showOptionsMenu]), or `onBackPressed` — is reflected here. There is
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
    private val activeIsBibleShown = mutableStateOf(activity.windowControl.activeWindow.pageManager.isBibleShown)

    private fun readOverlayText(): String = try { activity.bibleOverlayText } catch (e: MainBibleActivity.KeyIsNull) { "" }

    /**
     * The Compose overflow ("3-dot") options menu's item list + expanded flag (Batch 12b-C Task 3)
     * — host-owned state, since (unlike [nightMode]/[fullScreen]) there is no `ABEventBus` event to
     * mirror: [ReadingToolbarCallbacks.onOverflow] below rebuilds [overflowItems] from
     * [MainBibleActivity.buildOptionsMenuItems] and opens the menu; the `onOverflowItemClick`/
     * `onOverflowDismiss` callbacks passed to [mountComposeView] (see [install]) drive it closed
     * again — or, for a boolean toggle, rebuild it with the flipped check — via
     * [MainBibleActivity.handleOptionsMenuItem].
     */
    private val overflowItems = mutableStateOf(emptyList<OptionsMenuItem>())
    private val overflowExpanded = mutableStateOf(false)

    /**
     * Host-owned state for the Bible/Commentary quick-document picker menus (Batch 12g Task 8) —
     * the compose-path replacement for the native `menuForDocs` `PopupMenu` on the non-swap
     * short-press branch of [ReadingToolbarCallbacks.onBible]/`onCommentary` (see [install]).
     * Mirrors [overflowItems]/[overflowExpanded] above: built fresh from
     * [MainBibleActivity.composeQuickDocItems] on each tap, cleared by `onQuickDocSelect`/
     * `onQuickDocDismiss`. Only one of the two is ever expanded at a time (the toolbar only lets
     * one menu be open), so a single [MainBibleActivity.composeQuickDocSelect] can resolve either.
     */
    private val bibleQuickDoc = mutableStateOf(QuickDocMenuState())
    private val commentaryQuickDoc = mutableStateOf(QuickDocMenuState())

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
            isRateVisible = activity.drawerRateVisible,
        )
    }

    /** Toggles the Compose drawer — the target of [MainBibleActivity.composeToggleDrawer]. */
    fun toggleDrawer() { drawerOpen.value = !drawerOpen.value }

    /**
     * Whether the Compose drawer is open. Reads the same request flag `mountComposeView` keeps in
     * two-way sync with the real `DrawerState` (Batch Z-early A7 fix D made that sync symmetric, so
     * this is true for a drawer opened by ANY route, not just [toggleDrawer]).
     *
     * Read by [MainBibleActivity]'s key/back handlers, which on the classic path ask
     * `drawerLayout.isDrawerVisible(GravityCompat.START)` — always `false` on the compose path,
     * since `setupUi` locks the native `DrawerLayout` there.
     */
    val isDrawerOpen: Boolean get() = drawerOpen.value

    /** Opens the Compose drawer (idempotent) — see [isDrawerOpen]. */
    fun openDrawer() { drawerOpen.value = true }

    /** Closes the Compose drawer (idempotent) — see [isDrawerOpen]. */
    fun closeDrawer() { drawerOpen.value = false }

    init {
        ABEventBus.register(this) {
            onMain<ScreenSettings.NightModeChanged> {
                nightMode.value = ScreenSettings.nightMode
                monochrome.value = CommonUtils.settings.monochromeMode
            }
            onMain<MainBibleActivity.FullScreenEvent> { event -> fullScreen.value = event.isFullScreen }
            // Classic BibleView.BibleViewTouched re-show (SplitBibleArea.kt:203-205) — see
            // WindowButtonsVisibility's kdoc.
            onMain<BibleView.BibleViewTouched> { windowButtonsVisibility.onTouch() }
            // Batch 12g Task 3: mirrors classic `SplitBibleArea`'s own registration for the same two
            // events (`SplitBibleArea.kt:172,190`), which drive its `updateBibleReferenceOverlay`.
            // Unlike a dedicated `:sharedCore` service seam, this reuses the host's existing
            // fullscreen/night-mode event-mirror idiom: the overlay is just one string + two
            // booleans, kept as host-owned Compose `State` and gated by the pure `bibleReferenceOverlayVisible`
            // (`:sharedCore`) fn at render time — no separate service/controller class, per the plan.
            onMain<CurrentVerseChangedEvent> {
                overlayText.value = readOverlayText()
                activeIsBibleShown.value = activity.windowControl.activeWindow.pageManager.isBibleShown
            }
            onMain<CurrentWindowChangedEvent> {
                overlayText.value = readOverlayText()
                activeIsBibleShown.value = activity.windowControl.activeWindow.pageManager.isBibleShown
            }
            // Task 4 (F2b): classic's per-window rail top label (`WindowButtonWidget.kt:148`,
            // `pageManager.titleText`) is refreshed on this SAME event
            // (`WindowButtonWidget.kt:232-234`). `windowTopLabel`/`windowLabel`/`windowIcon` are
            // plain, non-`@Composable` lambdas re-read fresh on the next recomposition rather than
            // Compose `State` (see `MainBibleActivity.windowTopLabelFor`'s kdoc for why), so there is
            // no dedicated state field to push into here — this reuses the SAME `refreshHostedState()`
            // push `updateActions()`'s callers already use for the analogous `windowLabelFor`
            // (document-abbreviation) refresh, rather than adding a second refresh path.
            //
            // KNOWN COST (whole-batch review Minor #3, not coalesced this batch): on the dominant
            // scroll path, `CurrentBiblePage.setCurrentVerseOrdinal` posts THIS event via
            // `CurrentBibleVerse.setVerseSelected` and then posts `CurrentVerseChangedEvent` right
            // after (`VersePage.onVerseChange` -> `PassageChangeMediator.onCurrentVerseChanged`) —
            // an event `ToolbarStateServiceImpl` already subscribes to. Both handlers call the same
            // `ToolbarStateServiceImpl.refresh()` (this one via `refreshHostedState()` ->
            // `HostedStateRefresher.refresh()` -> `toolbar.refresh()`), so `buildSnapshot()` —
            // including `DocumentControl.biblesForVerse`/`commentariesForVerse`'s installed-book
            // sort — runs TWICE per verse change on the main thread; the `MutableStateFlow` only
            // conflates away the second, redundant EMISSION, not the recomputation cost. Still
            // needed: `CurrentBiblePage.doSetKey` posts this event ALONE (no `onVerseChange` call,
            // so no `CurrentVerseChangedEvent`), and would go stale without this subscription. See
            // `compose-port-status.md`'s F2b section and the on-device checklist's F2b performance
            // item (scrolling verse-by-verse in a multi-window split is where it would show).
            onMain<CurrentBibleVerseChanged> { refreshHostedState() }
        }
    }

    /**
     * See [ComposeReadingViewGeneration]. Called by [DocumentViewManager.buildView] on the compose
     * path when `forceUpdate` is true — which is exactly the hook `MainBibleActivity.currentWorkspaceId`'s
     * setter (workspace switch, or a same-workspace reload) drives, AFTER `windowRepository` has
     * already been reloaded to the new/current workspace. Also refreshes [agentSessionService] here
     * (Batch 12e-B Task 6 workspace-switch refresh): unlike classic `AgentLogWidget`, which always
     * recomputes `workspaceId` fresh per `ABEventBus` event, [agentSessionService]'s `snapshot` is a
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
     * mutators, so it is inert on the classic path (the host is null there).
     */
    fun refreshHostedState(rebuildComposition: Boolean = false) =
        hostedStateRefresher.refresh(rebuildComposition)

    /**
     * Opens the per-window (☰) pane menu for [windowId] — called by the pane overlay's ☰-button
     * tap and by the Plan-A restore rail's `onWindowLongPress` (both wired in [install]). Forces
     * the pane buttons visible first (mirroring classic `showPopupMenu`'s
     * `timerTask?.cancel(); toggleWindowButtonVisibility(true)`, `SplitBibleArea.kt:731-732`), so a
     * menu opened from the always-visible rail while the floating ☰ overlay happens to be
     * auto-hidden still renders anchored correctly — the pane overlay's `WindowPaneMenu` sibling is
     * composed regardless of the ☰ button's own visibility, see [mountComposeView]'s
     * `PaneWindowButtonOverlay`.
     */
    fun openPaneMenu(windowId: String) {
        windowButtonsVisibility.onTouch()
        val window = activity.windowRepository.getWindow(IdType(windowId)) ?: return
        paneMenuItems.value = paneMenuStateBuilder.build(window)
        paneMenuWindowId.value = windowId
    }

    /** Closes whichever per-window ☰ menu is open (`onDismiss`, or after a non-toggle item acts). */
    fun closePaneMenu() { paneMenuWindowId.value = null }

    /**
     * Opens the Compose reading-view LLM prompt-selector dialog for [selection] (Batch 12e-A Task
     * 6) — the compose-path counterpart of classic `LlmDialogHelper.showPromptSelector`. The
     * actual dialog UI is rendered by [readingLlmDialogs]/[ReadingLlmDialogs] inside
     * [mountComposeView]; this bridge only supplies the `onExecute` callback that starts the agent
     * once a prompt (and, if needed, a specification/model override) has been chosen — a verbatim
     * mirror of classic `LlmDialogHelper.executePrompt`. Called by [MainBibleActivity]'s
     * flag-gated entry points ([MainBibleActivity.showLlmPromptSelector] and the overflow/pane-menu
     * LLM actions) when `use_compose_ui` is on.
     */
    fun showPromptSelector(selection: Selection, context: PromptContext, docCategory: DocumentCategory?) {
        readingLlmDialogs.openPromptSelector(context.name, docCategory?.name) { promptId, userSpecification, modelOverrideId ->
            AgentForegroundService.startAgent(
                activity,
                IdType(promptId),
                selection,
                activity.windowControl.windowRepository.id,
                userSpecification,
                modelOverrideId?.let { IdType(it) },
            )
        }
    }

    /**
     * Opens the Compose reading-view regenerate-confirm dialog for [pageId]/[bibleView] (Batch
     * 12e-A Task 6) — the compose-path counterpart of classic `LlmDialogHelper.showRegenerateDialog`.
     * The `onRegenerate` callback is a verbatim mirror of classic `LlmDialogHelper.startRegenerate`:
     * it loads the "Regenerating…" placeholder into [bibleView] before kicking off the foreground
     * service. Called by [MainBibleActivity.showRegenerate] when `use_compose_ui` is on.
     */
    fun showRegenerate(pageId: IdType, bibleView: BibleView) {
        readingLlmDialogs.openRegenerate(pageId.toString()) { _, instructions, keepPrevious, freshRun, modelOverrideId ->
            activity.lifecycleScope.launch {
                bibleView.loadDocument(ErrorDocument(activity.getString(R.string.ai_document_regenerating), ErrorSeverity.NORMAL))
            }
            AgentForegroundService.startRegenerate(
                activity,
                pageId,
                activity.windowControl.windowRepository.id,
                bibleView.window.id,
                instructions,
                keepPrevious,
                freshRun,
                modelOverrideId?.let { IdType(it) },
            )
        }
    }

    /**
     * Suspends until the user answers [request], showing the Compose [AgentPermissionDialog] as a
     * sibling overlay of the reading view (Z-early B4) — the compose-path counterpart of classic
     * `Dialogs.agentPermissionDialog`'s native `AlertDialog`. Called from
     * `Dialogs.agentPermissionDialog` when the foreground activity is a [MainBibleActivity] with
     * this host installed; every other case still runs the classic dialog.
     *
     * Deliberately delegates straight to [permissions] rather than owning the suspension itself: the
     * request must survive this host being disposed (an activity recreation mid-prompt), and
     * [hostScope] — which [dispose] cancels — must not be in the chain resolving the agent's await.
     */
    suspend fun awaitPermission(request: AgentPermissionRequest): AgentPermissionChoice =
        permissions.await(request)

    /**
     * Unregisters this host's [ABEventBus] subscriptions (see [init]) and cancels [hostScope] (so
     * any in-flight [readingLlmDialogs] coroutine work is torn down with the host). Call from
     * [MainBibleActivity.onDestroy] — each activity (re-)creation builds a fresh
     * [ComposeReadingViewHost], so without this the previous instance's registration/scope would
     * leak (an activity-recreating config change would accumulate one stale registration per
     * rotation). Safe to call unconditionally even when [install] was never invoked (classic path).
     */
    fun dispose() {
        ABEventBus.unregister(this)
        hostScope.cancel()
    }

    /** Mounts the Compose reading view into [container] (expected: `binding.mainBibleView`, already emptied by the caller). */
    fun install(container: ViewGroup) {
        // Ensure the SSOT reflects the freshly-loaded workspace before first render (repo
        // mutations after this point already route through the 12a notifiers, which keep
        // windowState.layout current, but the very first mount needs an explicit kick).
        windowState.refresh(activity.windowRepository)

        // Layout surgery (compose path only — see class kdoc). Done programmatically here, not in
        // main_bible_view.xml, so the classic (use_compose_ui=false) path — which never calls
        // install() — stays byte-identical: toolbarLayout/toolbarDivider keep their XML-authored
        // visibility/constraints, and `container`'s LayoutParams are never mutated.
        activity.binding.toolbarLayout.visibility = View.GONE
        activity.binding.toolbarDivider.visibility = View.GONE
        // The classic Speak-transport bar and agent-log widget are declared AFTER the Compose
        // container in main_bible_view.xml and anchored to the parent bottom — unless hidden,
        // they draw ON TOP of their Compose replacements (`ReadingViewScreen`'s `speakBar`/
        // `agentLog` slots below, `speakBarSlot`/`agentLogSlot`). Hiding them here alone is not
        // enough — both re-show themselves later (`MainBibleActivity.updateBottomBars()`,
        // `AgentLogWidget`'s own bus handlers) — see `classicBottomChromeAllowed`, which those
        // call sites guard on.
        activity.binding.speakTransport.visibility = View.GONE
        activity.binding.agentLogWidget.visibility = View.GONE
        (container.layoutParams as? ConstraintLayout.LayoutParams)?.let { params ->
            params.topToBottom = ConstraintLayout.LayoutParams.UNSET
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            container.layoutParams = params
        }

        mountComposeView(
            container = container,
            windowState = windowState,
            commands = commands,
            nightModeState = nightMode,
            generationState = generation.state,
            toolbar = toolbarStateService.toolbar,
            toolbarCallbacks = ReadingToolbarCallbacks(
                onHome = { activity.composeToggleDrawer() },
                onTitleTap = { activity.composeStartKeyChooser() },
                onTitleLongPress = { activity.composeChooseDocument() },
                onTitleFlingVertical = { activity.composeWorkspace() },
                onTitleFlingHorizontal = { forward -> activity.composeCycleWorkspace(forward) },
                // Batch 12g Task 8: the non-swap short-press branch now drives the real Compose
                // quick-doc menu (`bibleQuickDoc`/`commentaryQuickDoc` below) instead of bridging to
                // the classic native `menuForDocs` `PopupMenu`. The swap-doc shortcut (short-press
                // with `toolbarButtonSetting` = "swap-*") and both long-press branches are UNCHANGED
                // bridges to `container` (the ComposeView) since the classic anchors (bibleButton/
                // commentaryButton) live inside the now-GONE toolbarLayout and would position a
                // native popup at a stale/zero location — long-press still opens the native
                // `menuForDocs`/`ChooseDocument` (device A/B backlog item, not this task's scope).
                // The overflow options menu is NOT bridged this way (Batch 12b-C Task 3 replaced
                // that native PopupMenu bridge with the real Compose `ReadingOverflowMenu` below —
                // see `onOverflow`/`overflowItems`).
                onBible = {
                    if (activity.toolbarButtonSetting?.startsWith("swap-") == true) {
                        activity.composeBibleClick(container)
                    } else {
                        val items = activity.composeQuickDocItems(activity.documentControl.biblesForVerse)
                        bibleQuickDoc.value = QuickDocMenuState(expanded = items.isNotEmpty(), items = items)
                    }
                },
                onBibleLong = { activity.composeBibleLongClick(container) },
                onCommentary = {
                    if (activity.toolbarButtonSetting?.startsWith("swap-") == true) {
                        activity.composeCommentaryClick(container)
                    } else {
                        val books = activity.documentControl.commentariesForVerse +
                            SwordDocumentFacade.getBooks(BookCategory.GENERAL_BOOK) +
                            SwordDocumentFacade.getBooks(BookCategory.DICTIONARY)
                        val items = activity.composeQuickDocItems(books)
                        commentaryQuickDoc.value = QuickDocMenuState(expanded = items.isNotEmpty(), items = items)
                    }
                },
                onCommentaryLong = { activity.composeCommentaryLongClick(container) },
                // The Strongs refresh now lives inside `composeCycleStrongs`/`composeStrongsLong`,
                // next to their `updateStrongsButton()` call — `StrongsPreference.handle()` posts
                // none of the 5 ABEventBus events `toolbarStateService` subscribes to, and keeping
                // the refresh at the mutation site also covers the long-press dialog's `onReset`
                // path, which this call site never saw.
                onStrongs = { activity.composeCycleStrongs() },
                onStrongsLong = { activity.composeStrongsLong() },
                onSearch = { activity.composeSearch() },
                onSpeak = { activity.composeToggleSpeak() },
                onSpeakLong = { activity.composeSpeakLong() },
                onWorkspace = { activity.composeWorkspace() },
                onOverflow = {
                    overflowItems.value = activity.buildOptionsMenuItems()
                    overflowExpanded.value = true
                },
            ),
            fullScreenState = fullScreen,
            overlayTextState = overlayText,
            activeIsBibleShownState = activeIsBibleShown,
            overflowItemsState = overflowItems,
            overflowExpandedState = overflowExpanded,
            onOverflowItemClick = { id ->
                val stayOpen = activity.handleOptionsMenuItem(id)
                if (stayOpen) {
                    overflowItems.value = activity.buildOptionsMenuItems()
                } else {
                    overflowExpanded.value = false
                }
            },
            onOverflowDismiss = { overflowExpanded.value = false },
            bibleQuickDocState = bibleQuickDoc,
            commentaryQuickDocState = commentaryQuickDoc,
            onQuickDocSelect = { id ->
                activity.composeQuickDocSelect(id)
                bibleQuickDoc.value = QuickDocMenuState()
                commentaryQuickDoc.value = QuickDocMenuState()
            },
            onQuickDocDismiss = {
                bibleQuickDoc.value = QuickDocMenuState()
                commentaryQuickDoc.value = QuickDocMenuState()
            },
            // Batch Z-early A6: the navigation drawer. Icons are resolved from the `iconKey` the
            // `:sharedCore` model carries (mirroring the menu XML's `android:icon`) through the
            // explicit [drawerIconResIds] table — the same "host resolves, `:sharedUi` stays
            // Android-free" shape as `windowIcon` above, except this one must be `@Composable`
            // because `painterResource` is only callable inside composition.
            drawerState = drawerMenu,
            drawerOpenState = drawerOpen,
            drawerIcon = { key ->
                val resId = drawerIconResIds[key]
                if (resId == null) null else painterResource(resId)
            },
            // Dispatches through the SAME `MenuCommandHandler.handleMenuRequest(itemId)` the classic
            // `NavigationView` listener calls (see `MainBibleActivity.handleDrawerItemClick`), so
            // every row's command behaviour is classic's by construction.
            onDrawerItemClick = { id -> activity.handleDrawerItemClick(DrawerMenuStateBuilder.resIdFor(id)) },
            // Batch Z-early A7: classic `DrawerListener` parity — see the three `LaunchedEffect`s
            // in `mountComposeView` and the entry points' kdoc on [MainBibleActivity].
            monochromeState = monochrome,
            onDrawerInMotion = { activity.drawerShowSystemUiTransient() },
            onDrawerIdleClosed = { activity.drawerApplyIdleSystemUi() },
            onDrawerClosed = { activity.drawerRestorePaneFocus() },
            pane = { windowId ->
                val window = activity.windowRepository.getWindow(IdType(windowId))
                if (window != null) {
                    AndroidView(factory = { activity.bibleViewFactory.getOrCreateBibleView(window) })
                }
            },
            windowLabel = { snapshot -> activity.windowLabelFor(snapshot.id) },
            windowIcon = { snapshot -> activity.windowIconFor(snapshot.id) },
            // Task 4 (F2b): the rail's tiny top row (classic `topButtonText`) — see
            // `MainBibleActivity.windowTopLabelFor`'s kdoc.
            windowTopLabel = { snapshot -> activity.windowTopLabelFor(snapshot.id) },
            controller = controller,
            windowButtonsVisibleState = windowButtonsVisibility.visible,
            touchTickState = windowButtonsVisibility.touchTick,
            onWindowButtonsHideTimeout = windowButtonsVisibility::onHideTimeout,
            paneMenuWindowIdState = paneMenuWindowId,
            paneMenuItemsState = paneMenuItems,
            onOpenPaneMenu = ::openPaneMenu,
            onPaneMenuItemClick = { windowId, id ->
                val stayOpen = activity.handleWindowPaneMenuItem(windowId, id)
                if (stayOpen) {
                    activity.windowRepository.getWindow(IdType(windowId))?.let {
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
                val resId = menuIconResIds[key]
                if (resId == null) null else painterResource(resId)
            },
            // Batch 12e-B Task 6: the agent-log panel, pre-built here (closing over the live
            // `agentLog` controller) since `install` already owns it — mirrors how `pane` above is
            // threaded straight through `mountComposeView` rather than rebuilt from raw state.
            agentLogSlot = {
                val agentLogUiState by agentLog.state.collectAsState()
                AgentLogPanel(
                    agentLogUiState,
                    animateStatus = !CommonUtils.settings.disableAnimations,
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
            speakBarSlot = {
                val speakState by speakTransport.state.collectAsState()
                // `fullScreen` is the host's own MutableState (fed by FullScreenEvent), read here
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
                    )
                }
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
            // Z-early B4: the runtime agent tool-permission prompt. Fed straight from the app-wide
            // controller (see [permissions]) — `dismiss()` resolves the awaiting agent coroutine
            // with DENY, matching the classic dialog's `setOnCancelListener`.
            permissionState = permissions.pending,
            onPermissionChoice = { permissions.respond(it) },
            onPermissionDismiss = { permissions.dismiss() },
        )
    }

    companion object {
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

        /**
         * Drawable-name -> `R.drawable.*` for every icon the Compose drawer can ask for: the 22
         * `iconKey`s of [DrawerMenuStateBuilder]'s static table plus `ic_logo` (the header, which
         * `ReadingDrawerContent` requests directly).
         *
         * Batch Z-early A7 fix F — this replaces a per-row, per-recomposition
         * `resources.getIdentifier(key, "drawable", packageName)`. Beyond the (minor) cost, name
         * lookup is invisible to R8: a resource referenced only by string silently resolves to `0`
         * once resource shrinking runs in a release build, so the icons would vanish from the
         * release drawer only. Direct `R.drawable` references mark them used and resolve at compile
         * time. Kept in step with the builder's table by
         * `ComposeReadingViewHostTest.drawerIconResIdsCoverEveryBuilderIconKey`.
         */
        internal val drawerIconResIds: Map<String, Int> = mapOf(
            "ic_logo" to R.drawable.ic_logo,
            "ic_library_books_white_24dp" to R.drawable.ic_library_books_white_24dp,
            "ic_search_24dp" to R.drawable.ic_search_24dp,
            "ic_baseline_headphones_24" to R.drawable.ic_baseline_headphones_24,
            "ic_baseline_bookmark_24" to R.drawable.ic_baseline_bookmark_24,
            "ic_baseline_studypads_24" to R.drawable.ic_baseline_studypads_24,
            "ic_baseline_description_24" to R.drawable.ic_baseline_description_24,
            "ic_reading_plan_24dp" to R.drawable.ic_reading_plan_24dp,
            "ic_bar_chart_24dp" to R.drawable.ic_bar_chart_24dp,
            "ic_history_clock_24dp" to R.drawable.ic_history_clock_24dp,
            "ic_file_download_24dp" to R.drawable.ic_file_download_24dp,
            "ic_settings_backup_restore_db_24dp" to R.drawable.ic_settings_backup_restore_db_24dp,
            "ic_syncdb_24dp" to R.drawable.ic_syncdb_24dp,
            "icon_robot" to R.drawable.icon_robot,
            "ic_settings_white_24dp" to R.drawable.ic_settings_white_24dp,
            "ic_help_white_24dp" to R.drawable.ic_help_white_24dp,
            "baseline_attach_money_24" to R.drawable.baseline_attach_money_24,
            "ic_need_help_24dp" to R.drawable.ic_need_help_24dp,
            "ic_baseline_emoji_people_24" to R.drawable.ic_baseline_emoji_people_24,
            "ic_baseline_copyright_24" to R.drawable.ic_baseline_copyright_24,
            "ic_baseline_people_24" to R.drawable.ic_baseline_people_24,
            "ic_rate_review_white_24dp" to R.drawable.ic_rate_review_white_24dp,
            "ic_bug_report_white_24dp" to R.drawable.ic_bug_report_white_24dp,
        )

        /**
         * Drawable-name -> `R.drawable.*` for every icon the per-window (☰) pane popup menu
         * ([WindowPaneMenuStateBuilder]) or the toolbar's overflow ("3-dot") menu
         * ([OptionsMenuStateBuilder]) can ask for — 22 entries total (14 shared with, or unique to,
         * the pane menu's `window_popup_menu.xml` table, plus 8 more from the overflow menu's
         * `main_bible_options_menu.xml` table; `ic_baseline_headphones_24`/
         * `ic_baseline_bookmark_24`/`icon_robot` also appear in [drawerIconResIds] above — kept as
         * separate entries here rather than merged, mirroring how each menu owns its own
         * self-contained table).
         *
         * A/B batch 1 F5b — same rationale as [drawerIconResIds]: a name-only
         * `resources.getIdentifier` lookup is invisible to R8 (silently resolves to `0` once
         * release resource shrinking runs), so direct `R.drawable` references are used instead.
         * Kept in step with both builders' tables by
         * `WindowPaneMenuStateBuilderTest.everyPaneMenuIconKeyIsResolvableByTheHost` and
         * `OptionsMenuStateBuilderTest.everyOverflowIconKeyIsResolvableByTheHost`.
         */
        internal val menuIconResIds: Map<String, Int> = mapOf(
            "ic_window_add_outline_black_24dp" to R.drawable.ic_window_add_outline_black_24dp,
            "ic_window_maximise_24dp" to R.drawable.ic_window_maximise_24dp,
            "ic_baseline_minimise_24" to R.drawable.ic_baseline_minimise_24,
            "ic_link_black_24dp" to R.drawable.ic_link_black_24dp,
            "ic_window_move_to_24dp" to R.drawable.ic_window_move_to_24dp,
            "ic_pin" to R.drawable.ic_pin,
            "ic_window_sync_24dp" to R.drawable.ic_window_sync_24dp,
            "ic_baseline_bookmark_24" to R.drawable.ic_baseline_bookmark_24,
            "file_export" to R.drawable.file_export,
            "ic_text_options_24dp" to R.drawable.ic_text_options_24dp,
            "ic_content_copy_black_24dp" to R.drawable.ic_content_copy_black_24dp,
            "baseline_content_paste_24" to R.drawable.baseline_content_paste_24,
            "ic_baseline_headphones_24" to R.drawable.ic_baseline_headphones_24,
            "ic_close_white_24dp" to R.drawable.ic_close_white_24dp,
            "ic_full_screen_24" to R.drawable.ic_full_screen_24,
            "ic_night_mode_24" to R.drawable.ic_night_mode_24,
            "ic_baseline_workspace_24" to R.drawable.ic_baseline_workspace_24,
            "ic_tilt_to_scroll_24dp" to R.drawable.ic_tilt_to_scroll_24dp,
            "ic_reverse_split_mode_24dp" to R.drawable.ic_reverse_split_mode_24dp,
            "ic_window_pinning_24" to R.drawable.ic_window_pinning_24,
            "ic_label_settings_24" to R.drawable.ic_label_settings_24,
            "icon_robot" to R.drawable.icon_robot,
        )

        /**
         * Testable mount: adds a [ComposeView] rendering [ReadingViewScreen] to [container].
         * Collaborators are passed explicitly so this can be exercised without booting a full
         * [MainBibleActivity] (see `ComposeReadingViewHostTest`).
         */
        fun mountComposeView(
            container: ViewGroup,
            windowState: WindowStateServiceImpl,
            commands: WindowCommands,
            // A `State` (not a plain `Boolean`) so the host's [ScreenSettings.NightModeChanged]
            // subscription (or a test) can flip it and drive a real recomposition instead of a
            // value frozen at mount time.
            nightModeState: State<Boolean>,
            // Defaults to a fresh, never-bumped state for the unit test (which mounts with
            // `pane = {}` and never attaches the ComposeView, so composition never runs).
            generationState: State<Int> = mutableIntStateOf(0),
            toolbar: StateFlow<ToolbarState> = MutableStateFlow(ToolbarState.EMPTY).asStateFlow(),
            toolbarCallbacks: ReadingToolbarCallbacks = noopToolbarCallbacks,
            // A `State` for the same reactivity reason as [nightModeState]: [install] mirrors
            // [MainBibleActivity.fullScreen] here via [MainBibleActivity.FullScreenEvent] instead
            // of passing a one-shot snapshot.
            fullScreenState: State<Boolean> = mutableStateOf(false),
            // Batch 12g Task 3 additions: the fullscreen bible-reference overlay's text + the
            // active window's bible-shown flag, `State`s for the same reactivity reason as
            // `fullScreenState` above — [ComposeReadingViewHost.install] mirrors both from
            // `CurrentVerseChangedEvent`/`CurrentWindowChangedEvent` (see its `init` block) rather
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
            // Batch 12g Task 8 additions: the Bible/Commentary quick-document picker menus,
            // `State`s for the same reason as `overflowItemsState`/`overflowExpandedState` above —
            // [ComposeReadingViewHost] owns mutable backing state (`bibleQuickDoc`/
            // `commentaryQuickDoc`) that its `ReadingToolbarCallbacks.onBible`/`onCommentary`
            // (non-swap branch) mutate, and `onQuickDocSelect`/`onQuickDocDismiss` clear. Defaulted
            // (collapsed/empty/no-op) so `ComposeReadingViewHostTest` (which never opens either
            // menu) is unaffected.
            bibleQuickDocState: State<QuickDocMenuState> = mutableStateOf(QuickDocMenuState()),
            commentaryQuickDocState: State<QuickDocMenuState> = mutableStateOf(QuickDocMenuState()),
            onQuickDocSelect: (id: String) -> Unit = {},
            onQuickDocDismiss: () -> Unit = {},
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
            onOpenPaneMenu: (windowId: String) -> Unit = {},
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
            agentLogSlot: (@Composable () -> Unit)? = { },
            // Batch 12f Task 6 additions: the reading-view Speak transport bar. `speakBarSlot` is a
            // pre-built `@Composable` lambda (same pass-through shape as `agentLogSlot` right
            // above — [ComposeReadingViewHost.install] already owns the live `speakTransport`
            // controller and closes over it directly). The bookmark-chooser dialog instead mirrors
            // the LLM-dialog shape (raw `StateFlow` + individual callbacks, assembled into a
            // concrete composable inside this function's body) since — like `ReadingLlmDialogs` —
            // it's rendered as a sibling overlay of `ReadingViewScreen`, not inside a slot. Defaulted
            // to inert no-ops so `ComposeReadingViewHostTest` (which never renders the bar/dialog)
            // is unaffected.
            speakBarSlot: (@Composable () -> Unit)? = { },
            speakDialogState: StateFlow<SpeakTransportDialog> = MutableStateFlow<SpeakTransportDialog>(SpeakTransportDialog.None).asStateFlow(),
            onSpeakBookmarkChosen: (id: String) -> Unit = {},
            onSpeakDialogDismiss: () -> Unit = {},
            // Z-early B4 additions: the runtime agent tool-permission prompt, rendered as a sibling
            // of `ReadingViewScreen` below (same shape as the LLM dialogs / speak-bookmark chooser:
            // a raw `StateFlow` + callbacks, assembled into the concrete dialog inside this
            // function's body). The flow is `AgentPermissionController.pending`, owned by the
            // app-wide Koin single, NOT by the host — see [ComposeReadingViewHost.awaitPermission].
            // Defaulted to an always-null flow + no-op callbacks so existing call sites and every
            // golden/host test (which never raise a permission request) are unaffected.
            permissionState: StateFlow<AgentPermissionRequest?> = MutableStateFlow<AgentPermissionRequest?>(null).asStateFlow(),
            onPermissionChoice: (AgentPermissionChoice) -> Unit = {},
            onPermissionDismiss: () -> Unit = {},
        ) {
            val composeView = ComposeView(container.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setContent {
                    ProvideAppLocals {
                        val nightMode by nightModeState
                        AbTheme(
                            darkTheme = nightMode,
                            // Not behind a dedicated change event (none exists for these settings
                            // in this codebase), but read directly in the composable body rather
                            // than `remember`ed, so they're re-read fresh on every recomposition
                            // this function already drives (nightMode/toolbar/gen/fullScreen).
                            colorMode = CommonUtils.settings.displayColorMode,
                            disableAnimations = CommonUtils.settings.disableAnimations,
                        ) {
                            val layout by controller.layout.collectAsState()
                            val toolbarState by toolbar.collectAsState()
                            val gen by generationState
                            val fullScreen by fullScreenState
                            val overflowItems by overflowItemsState
                            val overflowExpanded by overflowExpandedState
                            val bibleQuickDoc by bibleQuickDocState
                            val commentaryQuickDoc by commentaryQuickDocState
                            val tabBarModel = buildTabBarModel(layout)
                            val touchTick by touchTickState
                            val paneMenuWindowId by paneMenuWindowIdState
                            val paneMenuItems by paneMenuItemsState
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
                                // Keying the whole screen on `gen` forces every pane's `AndroidView`
                                // factory to re-run on `rebuild()` — see the `generation` kdoc above.
                                key(gen) {
                                    ReadingViewScreen(
                                        layout = layout,
                                        toolbar = toolbarState,
                                        toolbarIcons = readingToolbarIcons(),
                                        toolbarCallbacks = toolbarCallbacks,
                                        fullScreen = fullScreen,
                                        onWindowActivated = controller::onWindowActivated,
                                        onSeparatorCommitted = controller::onSeparatorCommitted,
                                        pane = pane,
                                        overflowItems = overflowItems,
                                        overflowExpanded = overflowExpanded,
                                        onOverflowItemClick = onOverflowItemClick,
                                        onOverflowDismiss = onOverflowDismiss,
                                        overflowIcon = menuIcon,
                                        bibleQuickDoc = bibleQuickDoc,
                                        commentaryQuickDoc = commentaryQuickDoc,
                                        onQuickDocSelect = onQuickDocSelect,
                                        onQuickDocDismiss = onQuickDocDismiss,
                                        paneOverlay = { windowId ->
                                            val window = layout.windows.firstOrNull { it.id == windowId }
                                            PaneWindowButtonOverlay(
                                                windowId = windowId,
                                                window = window,
                                                isActive = windowId == layout.activeWindowId,
                                                showButton = showPaneButtons,
                                                autoHidden = paneButtonsAutoHidden,
                                                nightMode = nightModeState.value,
                                                disableAnimations = CommonUtils.settings.disableAnimations,
                                                monochrome = monochromeState.value,
                                                paneMenuWindowId = paneMenuWindowId,
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
                                        bottomOverlay = { BibleReferenceOverlay(visible = overlayVisible, text = overlayText) },
                                        tabBar = if (hideTabBarInFullScreen) null else {
                                            {
                                                WindowTabBar(
                                                    // Classic lifts restoreButtonsContainer clear of the
                                                    // system/transport chrome with translationY(-bottomOffset2)
                                                    // (SplitBibleArea.kt:619). mainBibleView is bottom-padded
                                                    // only while the IME is open (MainBibleActivity.kt:642-648),
                                                    // so the floating rail must consume the navigation-bar inset
                                                    // itself. The agentLog/speakBar slots sit BELOW the split
                                                    // and are unaffected by this padding.
                                                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
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
                                                        onOpenPaneMenu(id)
                                                    },
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
                            // Sibling of `ReadingViewScreen` (not nested inside `key(gen)`, which
                            // only needs to scope the panes' `AndroidView` factories) — an
                            // `AlertDialog` overlays regardless of where in the tree it's composed,
                            // and there is at most one non-`None` dialog at a time.
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
                            // Z-early B4: the runtime agent tool-permission prompt — a third sibling
                            // overlay alongside `ReadingLlmDialogs`/`ChooseSpeakBookmarkDialog`
                            // above, for the same reason: an `AlertDialog` overlays regardless of
                            // where in the tree it is composed, and composing it OUTSIDE `key(gen)`
                            // (and outside every pane's `AndroidView`) means showing/dismissing it
                            // can never re-key the pane subtree and destroy/recreate the panes'
                            // BibleView WebViews.
                            val pendingPermission by permissionState.collectAsState()
                            pendingPermission?.let { req ->
                                AgentPermissionDialog(
                                    request = req,
                                    onChoice = { onPermissionChoice(it) },
                                    onDismiss = { onPermissionDismiss() },
                                )
                            }
                        }
                    }
                }
            }
            container.addView(composeView)
        }
    }
}

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
    bible = painterResource(R.drawable.ic_bible_24dp),
    commentary = painterResource(R.drawable.ic_commentary),
    workspace = painterResource(R.drawable.ic_workspace_solid_24dp),
    overflow = painterResource(R.drawable.ic_more_vert_black_24dp),
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
    nightMode: Boolean,
    disableAnimations: Boolean,
    monochrome: Boolean,
    paneMenuWindowId: String?,
    paneMenuItems: List<WindowPaneMenuItem>,
    controller: ReadingViewController,
    onOpenPaneMenu: (windowId: String) -> Unit,
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
                isPinned = window.isPinMode,
                isLinks = window.isLinksWindow,
                syncGroup = if (window.isSynchronised) window.syncGroup + 1 else 0,
                mode = WindowButtonMode.Pane,
                onClick = {
                    controller.onWindowActivated(windowId)
                    onOpenPaneMenu(windowId)
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
