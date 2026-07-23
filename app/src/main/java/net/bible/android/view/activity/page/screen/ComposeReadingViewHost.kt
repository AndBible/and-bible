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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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

    private val generation = ComposeReadingViewGeneration()

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
        }
    }

    /** See [ComposeReadingViewGeneration]. Called by [DocumentViewManager.buildView] on the compose path when `forceUpdate` is true. */
    fun rebuild() { generation.rebuild() }

    /**
     * Unregisters this host's [ABEventBus] subscriptions (see [init]). Call from
     * [MainBibleActivity.onDestroy] — each activity (re-)creation builds a fresh
     * [ComposeReadingViewHost], so without this the previous instance's registration would leak
     * (an activity-recreating config change would accumulate one stale registration per rotation).
     * Safe to call unconditionally even when [install] was never invoked (classic path).
     */
    fun dispose() { ABEventBus.unregister(this) }

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
        )
    }

    companion object {
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
        ) {
            val controller = ReadingViewController(windowState, commands)
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
