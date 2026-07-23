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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import net.bible.android.control.page.DocumentCategory
import net.bible.android.control.page.ErrorDocument
import net.bible.android.control.page.ErrorSeverity
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.ai.RawLlmLogActivity
import net.bible.android.view.activity.page.BibleView
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.Selection
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.service.llm.PromptContext
import net.bible.service.llm.agent.AgentForegroundService
import net.bible.sharedcore.ai.reading.AgentLogController
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.ReadingLlmDialogController
import net.bible.sharedcore.ai.reading.ReadingLlmDialogState
import net.bible.sharedcore.ai.reading.ReadingLlmService
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.PaneButtonAction
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.reading.paneButtonDragAction
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowTabBarModel
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.reading.AgentLogPanel
import net.bible.sharedui.ai.reading.ReadingLlmDialogs
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.WindowButton
import net.bible.sharedui.reading.WindowButtonMode
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.theme.AbTheme
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

    private val generation = ComposeReadingViewGeneration()

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

    init {
        ABEventBus.register(this) {
            onMain<ScreenSettings.NightModeChanged> { nightMode.value = ScreenSettings.nightMode }
            onMain<MainBibleActivity.FullScreenEvent> { event -> fullScreen.value = event.isFullScreen }
            // Classic BibleView.BibleViewTouched re-show (SplitBibleArea.kt:203-205) — see
            // WindowButtonsVisibility's kdoc.
            onMain<BibleView.BibleViewTouched> { windowButtonsVisibility.onTouch() }
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
                // BRIDGE to the classic native popups (bible/commentary doc pickers): anchored to
                // `container` (the ComposeView) since the classic anchors (bibleButton/
                // commentaryButton) live inside the now-GONE toolbarLayout and would position the
                // popup at a stale/zero location. The overflow options menu is NOT bridged this
                // way (Batch 12b-C Task 3 replaced that native PopupMenu bridge with the real
                // Compose `ReadingOverflowMenu` below — see `onOverflow`/`overflowItems`).
                onBible = { activity.composeBibleClick(container) },
                onBibleLong = { activity.composeBibleLongClick(container) },
                onCommentary = { activity.composeCommentaryClick(container) },
                onCommentaryLong = { activity.composeCommentaryLongClick(container) },
                // `composeCycleStrongs`/`composeStrongsLong` call `StrongsPreference.handle()`,
                // which posts none of the 5 ABEventBus events `toolbarStateService` subscribes to
                // (see ToolbarStateServiceImpl kdoc) — so without this explicit `refresh()` the
                // toolbar's Strongs icon dim state (the only feedback this button gives) would
                // stay stale until the next unrelated scroll/passage/window/speak event.
                onStrongs = { activity.composeCycleStrongs(); toolbarStateService.refresh() },
                onStrongsLong = { activity.composeStrongsLong(); toolbarStateService.refresh() },
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
            pane = { windowId ->
                val window = activity.windowRepository.getWindow(IdType(windowId))
                if (window != null) {
                    AndroidView(factory = { activity.bibleViewFactory.getOrCreateBibleView(window) })
                }
            },
            windowLabel = { snapshot -> activity.windowLabelFor(snapshot.id) },
            windowIcon = { snapshot -> activity.windowIconFor(snapshot.id) },
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
            llmDialogState = readingLlmDialogs.state,
            onLlmPromptChosen = readingLlmDialogs::onPromptChosen,
            onLlmToggleFavorite = readingLlmDialogs::onToggleFavorite,
            onLlmCategoryExpandedChanged = readingLlmDialogs::onCategoryExpandedChanged,
            onLlmSpecifySubmitted = readingLlmDialogs::onSpecifySubmitted,
            onLlmModelChosen = readingLlmDialogs::onModelChosen,
            onLlmRegenerateConfirmed = readingLlmDialogs::onRegenerateConfirmed,
            onLlmDismiss = readingLlmDialogs::dismiss,
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
            pane: @Composable (windowId: String) -> Unit,
            // Host-supplied per-window label/icon for the restore rail (Task 7) — plain,
            // non-`@Composable` lambdas, matching `WindowTabBar`'s `windowLabel`/`windowIcon`
            // parameter types (so composable calls like `painterResource` can't sneak into them;
            // see `MainBibleActivity.windowIconFor`'s kdoc for how it builds a `Painter` without
            // one). Defaulted (blank label, no icon) so `ComposeReadingViewHostTest` — which never
            // renders the rail itself — is unaffected.
            windowLabel: (WindowSnapshot) -> String = { "" },
            windowIcon: (WindowSnapshot) -> Painter? = { null },
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
                            // Classic `BibleFrame.addWindowButton`'s early-outs (`screen/BibleFrame.kt:157-158`):
                            // never shown while `hide_window_buttons` is set, or while a window is
                            // maximised (the floating button, specifically — the per-window MENU can
                            // still open via the rail's unmaximise-button long-press; see
                            // `PaneWindowButtonOverlay`, which composes `WindowPaneMenu` unconditionally).
                            val showPaneButtons = windowButtonsVisible && layout.maximizedWindowId == null &&
                                !CommonUtils.settings.getBoolean("hide_window_buttons", false)
                            // Classic `SplitBibleArea`'s fullscreen auto-hide
                            // (`autoHideWindowButtonBarInFullScreen`, `full_screen_hide_buttons_pref`,
                            // default ON, `SplitBibleArea.kt:166-171`/365-366) — in fullscreen with the
                            // pref on, drop the rail entirely (not merely collapsed, which is what
                            // `tabBarModel.showButtons` already handles for the non-fullscreen
                            // collapse toggle).
                            val hideTabBarInFullScreen = fullScreen &&
                                CommonUtils.settings.getBoolean("full_screen_hide_buttons_pref", true)
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
                                    paneOverlay = { windowId ->
                                        val window = layout.windows.firstOrNull { it.id == windowId }
                                        PaneWindowButtonOverlay(
                                            windowId = windowId,
                                            window = window,
                                            isActive = windowId == layout.activeWindowId,
                                            showButton = showPaneButtons,
                                            paneMenuWindowId = paneMenuWindowId,
                                            paneMenuItems = paneMenuItems,
                                            controller = controller,
                                            onOpenPaneMenu = onOpenPaneMenu,
                                            onPaneMenuItemClick = onPaneMenuItemClick,
                                            onPaneMenuDismiss = onPaneMenuDismiss,
                                        )
                                    },
                                    agentLog = agentLogSlot,
                                    tabBar = if (hideTabBarInFullScreen) null else {
                                        {
                                            WindowTabBar(
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
                                            )
                                        }
                                    },
                                )
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
 * The button itself is gated by [showButton] (auto-hide / `hide_window_buttons` / maximised —
 * see `showPaneButtons` at the call site), but [WindowPaneMenu] is ALWAYS composed as its sibling:
 * a menu opened via the always-visible restore rail's long-press must still be able to render
 * anchored to this pane even when the floating button itself is hidden (see
 * [ComposeReadingViewHost.openPaneMenu]'s kdoc).
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
    paneMenuWindowId: String?,
    paneMenuItems: List<WindowPaneMenuItem>,
    controller: ReadingViewController,
    onOpenPaneMenu: (windowId: String) -> Unit,
    onPaneMenuItemClick: (windowId: String, id: String) -> Unit,
    onPaneMenuDismiss: () -> Unit,
) {
    Box(Modifier.align(Alignment.TopEnd)) {
        if (showButton && window != null) {
            var accumDy by remember(windowId) { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val thresholdPx = remember(density) { with(density) { PaneButtonDragThresholdDp.toPx() } }
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
                modifier = Modifier.pointerInput(windowId) {
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
        )
    }
}
