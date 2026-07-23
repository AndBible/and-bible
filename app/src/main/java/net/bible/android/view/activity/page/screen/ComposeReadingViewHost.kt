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

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
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
 * `use_compose_ui` is on. Plan A (this task) keeps the classic toolbar/drawer chrome; Plan B
 * ports the toolbar into Compose. Each pane hosts the window's existing [net.bible.android.view.activity.page.BibleView]
 * via [AndroidView] wrapping [MainBibleActivity.bibleViewFactory] — the WebView/JS bridge stays
 * an unmodified black box.
 */
class ComposeReadingViewHost(private val activity: MainBibleActivity) : KoinComponent {
    private val windowState: WindowStateServiceImpl by inject()
    private val commands: WindowCommands by inject()
    private val toolbarStateService: ToolbarStateService by inject()

    private val generation = ComposeReadingViewGeneration()

    /** See [ComposeReadingViewGeneration]. Called by [DocumentViewManager.buildView] on the compose path when `forceUpdate` is true. */
    fun rebuild() { generation.rebuild() }

    /** Mounts the Compose reading view into [container] (expected: `binding.mainBibleView`, already emptied by the caller). */
    fun install(container: ViewGroup) {
        // Ensure the SSOT reflects the freshly-loaded workspace before first render (repo
        // mutations after this point already route through the 12a notifiers, which keep
        // windowState.layout current, but the very first mount needs an explicit kick).
        windowState.refresh(activity.windowRepository)
        mountComposeView(
            container = container,
            windowState = windowState,
            commands = commands,
            nightMode = ScreenSettings.nightMode,
            generationState = generation.state,
            toolbar = toolbarStateService.toolbar,
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
            nightMode: Boolean,
            // Defaults to a fresh, never-bumped state for the unit test (which mounts with
            // `pane = {}` and never attaches the ComposeView, so composition never runs).
            generationState: State<Int> = mutableIntStateOf(0),
            // TODO(Batch 12b-B Task 5): [install] passes the real ToolbarStateServiceImpl-backed
            // flow; this default (a static EMPTY state) only keeps unit tests that don't care
            // about the toolbar (e.g. ComposeReadingViewHostTest) compiling without change.
            toolbar: StateFlow<ToolbarState> = MutableStateFlow(ToolbarState.EMPTY).asStateFlow(),
            // TODO(Batch 12b-B Task 5): source this from a real setting/host signal (e.g. an
            // immersive-mode toggle) instead of the always-false placeholder.
            fullScreen: Boolean = false,
            pane: @Composable (windowId: String) -> Unit,
        ) {
            val controller = ReadingViewController(windowState, commands)
            val composeView = ComposeView(container.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setContent {
                    ProvideAppLocals {
                        AbTheme(
                            darkTheme = nightMode,
                            colorMode = CommonUtils.settings.displayColorMode,
                            disableAnimations = CommonUtils.settings.disableAnimations,
                        ) {
                            val layout by controller.layout.collectAsState()
                            val toolbarState by toolbar.collectAsState()
                            val gen by generationState
                            // Keying the whole screen on `gen` forces every pane's `AndroidView`
                            // factory to re-run on `rebuild()` — see the `generation` kdoc above.
                            key(gen) {
                                ReadingViewScreen(
                                    layout = layout,
                                    toolbar = toolbarState,
                                    toolbarIcons = defaultToolbarIcons(),
                                    toolbarCallbacks = noopToolbarCallbacks,
                                    fullScreen = fullScreen,
                                    onWindowActivated = controller::onWindowActivated,
                                    onSeparatorCommitted = controller::onSeparatorCommitted,
                                    pane = pane,
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
 * Interim [ReadingToolbarIcons] for [ComposeReadingViewHost.mountComposeView] — the same
 * `main_bible_view.xml` toolbar drawables `ReadingToolbarGoldenTest` uses. Real per-action button
 * behaviour (drawer/search/speak/etc.) is wired by Batch 12b-B Task 5; only the icon set is filled
 * in here so the toolbar renders correctly meanwhile.
 */
@Composable
private fun defaultToolbarIcons() = ReadingToolbarIcons(
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
 * Interim no-op [ReadingToolbarCallbacks] for [ComposeReadingViewHost.mountComposeView] — every
 * button/gesture is inert until Batch 12b-B Task 5 routes them to the real
 * `MainBibleActivity`/`WindowControl`/`SpeakControl` actions the classic toolbar performs.
 */
private val noopToolbarCallbacks = ReadingToolbarCallbacks(
    onHome = {}, onTitleTap = {}, onTitleLongPress = {}, onTitleFlingVertical = {},
    onTitleFlingHorizontal = {}, onBible = {}, onBibleLong = {}, onCommentary = {},
    onCommentaryLong = {}, onStrongs = {}, onStrongsLong = {}, onSearch = {}, onSpeak = {},
    onSpeakLong = {}, onWorkspace = {}, onOverflow = {},
)
