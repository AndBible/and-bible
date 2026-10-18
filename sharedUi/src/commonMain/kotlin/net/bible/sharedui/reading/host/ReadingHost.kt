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

package net.bible.sharedui.reading.host

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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.view.activity.page.screen.PaneMenuAnchor
import net.bible.android.view.activity.page.screen.menuWindowIdFor
import net.bible.android.view.activity.page.screen.searchSheetShadowElevation
import net.bible.sharedcore.ai.reading.ReadingLlmDialogState
import net.bible.sharedcore.reading.DrawerCloseLatch
import net.bible.sharedcore.reading.DrawerMenuState
import net.bible.sharedcore.reading.OptionsMenuItem
import net.bible.sharedcore.reading.PaneButtonAction
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ReadingSearchBarState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.bibleReferenceOverlayVisible
import net.bible.sharedcore.reading.paneButtonDragAction
import net.bible.sharedcore.reading.paneButtonFadeMillis
import net.bible.sharedcore.reading.paneButtonHiddenAlpha
import net.bible.sharedcore.speak.SpeakTransportDialog
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedcore.window.shouldShowPinIndicator
import net.bible.sharedui.ai.reading.ReadingLlmDialogs
import net.bible.sharedui.reading.BibleReferenceOverlay
import net.bible.sharedui.reading.ChooseSpeakBookmarkDialog
import net.bible.sharedui.reading.ReadingDrawerContent
import net.bible.sharedui.reading.ReadingDrawerWidth
import net.bible.sharedui.reading.ReadingSearchBarCallbacks
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.reading.ReadingViewScreen
import net.bible.sharedui.reading.WindowButton
import net.bible.sharedui.reading.WindowButtonMode
import net.bible.sharedui.reading.WindowPaneMenu
import net.bible.sharedui.reading.WindowTabBar
import net.bible.sharedui.reading.readingRailInsetPadding
import net.bible.sharedui.search.readingSheetInsetPadding
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.Strings
import net.bible.sharedui.theme.LocalDisplayColorMode

/** Test tag of the node [ReadingHost] wraps around the open quick sheet. */
@kotlin.native.HiddenFromObjC
const val ReadingQuickSheetTag = "reading-quick-sheet"

/**
 * Everything [ReadingHost] needs from its platform host.
 *
 * Only [state], [controller], [toolbarCallbacks] and [toolbarIcons] are required: they carry the
 * host's own wiring. Every other member defaults to inert (no overlay, no callback, empty slot), so
 * a host or a test supplies only what it exercises; the Android host passes all of them.
 *
 * The platform-built pieces are the slots at the end. They stay platform-built in this phase because
 * they reach platform services (search indexing listeners, the system TTS settings intent, Android
 * drawable thumbnails, `R` strings); phase 2 moves them behind ports.
 */
@kotlin.native.HiddenFromObjC
class ReadingHostDeps(
    val state: ReadingHostState,
    val controller: ReadingViewController,
    val toolbarCallbacks: ReadingToolbarCallbacks,
    /** `@Composable` because the icons are resolved with a composition-only painter lookup. */
    val toolbarIcons: @Composable () -> ReadingToolbarIcons,
    val toolbar: StateFlow<ToolbarState> = MutableStateFlow(ToolbarState.EMPTY).asStateFlow(),

    // --- ambient display state ---
    val nightMode: State<Boolean> = mutableStateOf(false),
    val monochrome: State<Boolean> = mutableStateOf(false),
    /** Bumped by the host to dispose and recreate the whole pane subtree. */
    val generation: State<Int> = mutableIntStateOf(0),
    val fullScreen: State<Boolean> = mutableStateOf(false),
    val overlayText: State<String> = mutableStateOf(""),
    val activeIsBibleShown: State<Boolean> = mutableStateOf(false),
    /** The keyboard shrink in px (see `ReadingViewScreen.imeBottomPadding`). */
    val imeBottomPaddingPx: State<Int> = mutableIntStateOf(0),
    /** Read in composition (the "No animations" setting can change while the view is up). */
    val disableAnimations: () -> Boolean = { false },

    // --- overflow menu ---
    val overflowItems: State<List<OptionsMenuItem>> = mutableStateOf(emptyList()),
    val overflowExpanded: State<Boolean> = mutableStateOf(false),
    val onOverflowItemClick: (id: String) -> Unit = {},
    val onOverflowDismiss: () -> Unit = {},

    // --- drawer ---
    val drawerMenu: State<DrawerMenuState> = mutableStateOf(DrawerMenuState.EMPTY),
    val drawerIcon: @Composable (iconKey: String) -> Painter? = { null },
    val onDrawerItemClick: (id: String) -> Unit = {},
    val onDrawerInMotion: () -> Unit = {},
    val onDrawerIdleClosed: () -> Unit = {},
    val onDrawerClosed: () -> Unit = {},

    // --- panes, rail and ☰ menus ---
    val paneBackground: (windowId: String) -> Color? = { null },
    val windowLabel: (WindowSnapshot) -> String = { "" },
    val windowIcon: (WindowSnapshot) -> Painter? = { null },
    val windowTopLabel: (WindowSnapshot) -> String? = { null },
    /** Runs a ☰ menu row; true when the menu should stay open (its items are then refreshed). */
    val handlePaneMenuItem: (windowId: String, id: String) -> Boolean = { _, _ -> false },
    val menuIcon: @Composable (iconKey: String) -> Painter? = { null },

    // --- dialogs owned by the controllers behind the reading view ---
    val llmDialog: StateFlow<ReadingLlmDialogState> = MutableStateFlow(ReadingLlmDialogState()).asStateFlow(),
    val onLlmPromptChosen: (promptId: String) -> Unit = {},
    val onLlmToggleFavorite: (promptId: String) -> Unit = {},
    val onLlmCategoryExpandedChanged: (categoryId: String?, expanded: Boolean) -> Unit = { _, _ -> },
    val onLlmSpecifySubmitted: (text: String) -> Unit = {},
    val onLlmModelChosen: (modelId: String, setAsDefault: Boolean) -> Unit = { _, _ -> },
    val onLlmRegenerateConfirmed: (instructions: String?, keepPrevious: Boolean, freshRun: Boolean) -> Unit =
        { _, _, _ -> },
    val onLlmDismiss: () -> Unit = {},
    val speakDialog: StateFlow<SpeakTransportDialog> =
        MutableStateFlow<SpeakTransportDialog>(SpeakTransportDialog.None).asStateFlow(),
    val onSpeakBookmarkChosen: (id: String) -> Unit = {},
    val onSpeakDialogDismiss: () -> Unit = {},

    // --- search ---
    val searchBar: StateFlow<ReadingSearchBarState?> = MutableStateFlow<ReadingSearchBarState?>(null).asStateFlow(),
    val searchBarCallbacks: ReadingSearchBarCallbacks? = null,
    val searchSheetVisible: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow(),
    val onSearchSheetDismissed: () -> Unit = {},
    /** The search sheet's live (visible, measured height in px), for the host's WebView bottom offset. */
    val onSearchSheetOffsetsChanged: (visible: Boolean, heightPx: Int) -> Unit = { _, _ -> },
    val searchUnavailableDocName: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow(),
    val onSearchUnavailableMessageShown: () -> Unit = {},

    // --- platform-built slots (phase 2 must join: see the class kdoc) ---
    val agentLogSlot: (@Composable (
        applyNavBarInset: Boolean,
        maxHeightDp: Float,
        collapsedHeightDp: Float,
        onCollapsedHeightMeasured: (Float) -> Unit,
    ) -> Unit)? = null,
    val speakBarSlot: (@Composable (applyNavBarInset: Boolean) -> Unit)? = null,
    val agentLogVisible: @Composable () -> Boolean = { false },
    val speakBarVisible: @Composable () -> Boolean = { false },
    /** What goes INSIDE the search sheet (index prompt/progress, or results). */
    val searchSheetSlot: @Composable () -> Unit = {},
    val searchSettingsSlot: @Composable () -> Unit = {},
    val textSettingsEditorSlot: @Composable () -> Unit = {},
    val speakSettingsSlot: @Composable () -> Unit = {},
    /** The body of the open quick sheet; [ReadingHost] decides WHEN one is open, from [ReadingHostState.quickSheet]. */
    val quickSheetContent: @Composable (ReadingQuickSheet) -> Unit = {},
    val readingDialogSlot: @Composable () -> Unit = {},
)

/**
 * The reading view: the toolbar, the panes and the window rail, with the drawer around them and
 * every overlay as a sibling, all driven from [deps].
 *
 * What it does NOT contain: the theme and the link-routing wrapper (the host provides them, as it
 * does for every other screen), and what fills a pane (see [LocalReadingPane]).
 *
 * The structure carries load: the drawer and the (always present) search-sheet scaffold ENCLOSE
 * `key(generation)` and `ReadingViewScreen`, and every dialog/sheet is a SIBLING of them, never inside.
 * Making any of those conditional, or moving one inside the pane subtree, would re-parent every pane
 * and destroy and recreate each window's WebView.
 */
@kotlin.native.HiddenFromObjC
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingHost(deps: ReadingHostDeps, modifier: Modifier = Modifier) {
    val state = deps.state
    val controller = deps.controller
    val layout by controller.layout.collectAsState()
    val toolbarState by deps.toolbar.collectAsState()
    val gen by deps.generation
    val fullScreen by deps.fullScreen
    val imeBottomPaddingPx by deps.imeBottomPaddingPx
    val overflowItems by deps.overflowItems
    val overflowExpanded by deps.overflowExpanded
    val tabBarModel = buildWindowTabBar(layout)
    val touchTick by state.windowButtonsVisibility.touchTick
    val paneMenuWindowId by state.paneMenuWindowId
    val paneMenuItems by state.paneMenuItems
    val paneMenuAnchor by state.paneMenuAnchor
    // `null` outside search mode, which is what makes `ReadingToolbar` draw its normal row.
    val searchBar by deps.searchBar.collectAsState()
    // Classic `resetTouchTimer`'s 2s hide countdown (`SplitBibleArea.kt:511-524`), restarted on every
    // `touchTick` bump (a real touch, or `openPaneMenu`). Suppressed entirely while a pane menu is
    // open (classic cancels the timer for the popup's lifetime): Compose cancels/reruns this effect
    // whenever either key changes, so closing the menu starts a fresh 2s countdown.
    LaunchedEffect(touchTick, paneMenuWindowId) {
        if (paneMenuWindowId == null) {
            delay(2000)
            state.windowButtonsVisibility.onHideTimeout()
        }
    }
    val windowButtonsVisible by state.windowButtonsVisibility.visible
    val overlayText by deps.overlayText
    val activeIsBibleShown by deps.activeIsBibleShown
    // Pure gate (`:sharedCore`) mirroring classic `SplitBibleArea.updateBibleReferenceOverlay`: visible
    // only fullscreen, with the active window showing a Bible, while the (auto-hiding) window buttons
    // are shown, and the user hasn't disabled the overlay.
    val overlayVisible = bibleReferenceOverlayVisible(
        fullScreen = fullScreen,
        activeIsBibleShown = activeIsBibleShown,
        buttonsShown = windowButtonsVisible,
        hideSetting = state.settings.getBoolean("hide_bible_reference_overlay", false),
    )
    // Hard gates (classic `BibleFrame.addWindowButton`'s early-outs, screen/BibleFrame.kt:157-158):
    // these genuinely remove the button. `windowButtonsVisible` is the 2s IDLE TIMER, which classic
    // expresses as an ALPHA fade, not a removal (SplitBibleArea.kt:528-575), so it is passed
    // separately as `autoHidden` and must NOT be folded back in here. (The per-window MENU can still
    // open via the rail's unmaximise-button long-press; `PaneWindowButtonOverlay` composes
    // `WindowPaneMenu` unconditionally.)
    val showPaneButtons = layout.maximizedWindowId == null &&
        !state.settings.getBoolean("hide_window_buttons", false)
    val paneButtonsAutoHidden = !windowButtonsVisible
    // Classic `SplitBibleArea`'s fullscreen auto-hide (`full_screen_hide_buttons_pref`, default ON,
    // `SplitBibleArea.kt:166-171`/365-366): in fullscreen with the pref on, drop the rail entirely
    // (not merely collapsed, which is what `tabBarModel.showButtons` already handles).
    val hideTabBarInFullScreen = fullScreen &&
        state.settings.getBoolean("full_screen_hide_buttons_pref", true)
    val md3DrawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerOpen = state.drawerOpen
    val pane = LocalReadingPane.current

    // Host request -> drawer.
    LaunchedEffect(drawerOpen.value) {
        if (drawerOpen.value) md3DrawerState.open() else md3DrawerState.close()
    }
    // Drawer -> host request, BOTH ways: a swipe/scrim/back close must clear the request, else the next
    // `toggleDrawer()` would see a stale `true` and do nothing; and an open that did NOT come from the
    // request (predictive-back cancel) must set it, else the next tap would look dead until pressed
    // twice. `snapshotFlow` only emits on a CHANGE, so mirroring the settled value cannot undo the
    // `open()` above while the open animation is still running.
    LaunchedEffect(md3DrawerState) {
        snapshotFlow { md3DrawerState.isClosed }
            .collect { closed -> drawerOpen.value = !closed }
    }
    // Classic DrawerListener parity: in-motion vs idle-closed drive the system UI, and the transition
    // TO closed restores focus into the active pane.
    LaunchedEffect(md3DrawerState) {
        snapshotFlow { md3DrawerState.currentValue != md3DrawerState.targetValue }
            .collect { inMotion -> if (inMotion) deps.onDrawerInMotion() }
    }
    LaunchedEffect(md3DrawerState) {
        snapshotFlow {
            md3DrawerState.currentValue == md3DrawerState.targetValue &&
                md3DrawerState.currentValue == DrawerValue.Closed
        }.collect { idleClosed -> if (idleClosed) deps.onDrawerIdleClosed() }
    }
    // Edge-detect the close: `DrawerCloseLatch` seeds from the initial snapshot and latches, so a
    // (re)subscription replay cannot re-fire the focus restore (a raw `collect {}` re-fires on every
    // resubscribe).
    LaunchedEffect(md3DrawerState) {
        val latch = DrawerCloseLatch(initiallyOpen = md3DrawerState.currentValue == DrawerValue.Open)
        snapshotFlow { md3DrawerState.currentValue }.collect { value ->
            if (latch.observe(value == DrawerValue.Open)) deps.onDrawerClosed()
        }
    }

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = md3DrawerState,
        // No drag-to-OPEN (classic opens on a ~20dp EDGE drag; M3's anywhere-drag is a new gesture, and
        // the panes' WebViews swallow horizontal drags anyway). Gate on the open state rather than
        // hardcoding `false`: in M3 1.4.0 `gesturesEnabled` ALSO gates the scrim's dismiss handler, so
        // `false` outright would kill tap-outside-to-dismiss. With `isOpen`, gestures stay off while
        // closed but turn on once open, restoring scrim-tap-to-dismiss and swipe-to-close.
        gesturesEnabled = md3DrawerState.isOpen,
        // Classic `setupUi`: `if (monochromeMode) drawerLayout.setScrimColor(TRANSPARENT)`; no dimming on e-ink.
        scrimColor = if (deps.monochrome.value) Color.Transparent else DrawerDefaults.scrimColor,
        drawerContent = {
            // Classic's `NavigationView` is `wrap_content` (~300dp); M3's default is a fixed 360dp. See
            // `ReadingDrawerWidth`'s kdoc.
            ModalDrawerSheet(modifier = Modifier.width(ReadingDrawerWidth)) {
                ReadingDrawerContent(
                    state = deps.drawerMenu.value,
                    icon = deps.drawerIcon,
                    onItemClick = { id ->
                        drawerOpen.value = false
                        deps.onDrawerItemClick(id)
                    },
                )
            }
        },
    ) {
        // The search sheet's scaffold: UNCONDITIONALLY present and ENCLOSING `key(gen)`, the same "outer
        // wrap, never inside the pane subtree" rule as the drawer. Conditionality, not the wrapper, is
        // what would re-parent every pane and recreate every WebView on each search open/close.
        // `skipHiddenState = false` makes "always present" and "fully closable" compatible. See
        // [DriveSearchSheet], which also carries why `expand()` and not `partialExpand()` shows it.
        val searchSheetState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.Hidden,
                skipHiddenState = false,
            ),
        )
        val searchSheetVisible by deps.searchSheetVisible.collectAsState()
        DriveSearchSheet(searchSheetState, searchSheetVisible, deps.onSearchSheetDismissed)
        // The "<document> cannot be searched" snackbar, through this scaffold's `snackbarHost` (the one
        // `BottomSheetScaffold` of this host). `onUnavailable` fires outside composition, which is why
        // it only records the document name and leaves formatting to this composable.
        val searchSnackbarHostState = remember { SnackbarHostState() }
        val searchUnavailableDocName by deps.searchUnavailableDocName.collectAsState()
        DriveSearchUnavailableSnackbar(
            snackbarHostState = searchSnackbarHostState,
            docName = searchUnavailableDocName,
            strings = LocalStrings.current,
            onShown = deps.onSearchUnavailableMessageShown,
        )
        // The sheet is self-sizing, so its height for the host's WebView bottom offset must be MEASURED
        // off the real content. The on-screen sheet is the CONTENT plus M3's drag-handle band, which
        // `BottomSheetScaffold` renders as a SEPARATE composable; rather than hardcode the default
        // handle's height, `sheetDragHandle` is supplied explicitly so its real height is measured too.
        var searchSheetContentHeightPx by remember { mutableIntStateOf(0) }
        var searchSheetHandleHeightPx by remember { mutableIntStateOf(0) }
        LaunchedEffect(searchSheetVisible, searchSheetContentHeightPx, searchSheetHandleHeightPx) {
            deps.onSearchSheetOffsetsChanged(
                searchSheetVisible,
                searchSheetContentHeightPx + searchSheetHandleHeightPx,
            )
        }
        BottomSheetScaffold(
            scaffoldState = searchSheetState,
            sheetPeekHeight = 0.dp,
            // The mounted hidden sheet's shadow otherwise spills onto grey-base (MONO and B&W) paper.
            sheetShadowElevation = searchSheetShadowElevation(LocalDisplayColorMode.current),
            // BottomSheetScaffold applies no window insets (material3 1.4.0) and this tree is
            // edge-to-edge: clear the nav bar / cutout ourselves.
            snackbarHost = { SnackbarHost(searchSnackbarHostState, Modifier.readingSheetInsetPadding()) },
            sheetDragHandle = {
                Box(Modifier.onSizeChanged { size -> searchSheetHandleHeightPx = size.height }) {
                    BottomSheetDefaults.DragHandle()
                }
            },
            sheetContent = {
                // Exactly as tall as its content, but never taller than [SearchSheetMaxHeightFraction]
                // of the window, so the reading text it was opened from stays visible above it.
                // `BoxWithConstraints` supplies that ceiling in Dp without reaching for a platform
                // configuration.
                //
                // The 1.dp FLOOR is load-bearing: an empty sheet measures to zero, and M3 publishes no
                // `Expanded` anchor for a zero-height sheet at all, so an `expand()` landing before the
                // first layout pass would be a silent no-op.
                //
                // The nav-bar/cutout padding is OUTSIDE the measured Box on purpose:
                // `searchSheetContentHeightPx` feeds the host's bottom offset, which already adds the
                // nav bar, so measuring it here would reserve it twice.
                BoxWithConstraints(Modifier.fillMaxWidth().readingSheetInsetPadding()) {
                    Box(
                        Modifier
                            .heightIn(
                                min = 1.dp,
                                max = maxHeight * SearchSheetMaxHeightFraction,
                            )
                            .onSizeChanged { size -> searchSheetContentHeightPx = size.height }
                    ) { deps.searchSheetSlot() }
                }
            },
        ) { _ ->
            // Keying the whole screen on `gen` forces every pane's host view to be recreated on the
            // host's `rebuild()`.
            key(gen) {
                ReadingViewScreen(
                    layout = layout,
                    toolbar = toolbarState,
                    toolbarIcons = deps.toolbarIcons(),
                    toolbarCallbacks = deps.toolbarCallbacks,
                    fullScreen = fullScreen,
                    // The keyboard shrink the host computed, applied as a plain bottom padding on the
                    // reading column.
                    imeBottomPadding = with(LocalDensity.current) { imeBottomPaddingPx.toDp() },
                    onWindowActivated = controller::onWindowActivated,
                    onSeparatorCommitted = controller::onSeparatorCommitted,
                    pane = { windowId -> pane(windowId, Modifier.fillMaxSize()) },
                    paneBackground = deps.paneBackground,
                    // Read in composition, so it follows the active window and night/monochrome as the panes do.
                    edgeBackground = deps.paneBackground(layout.activeWindowId),
                    // A non-null state replaces the toolbar's normal row with the search field.
                    searchBar = searchBar,
                    searchBarCallbacks = deps.searchBarCallbacks,
                    overflowItems = overflowItems,
                    overflowExpanded = overflowExpanded,
                    onOverflowItemClick = deps.onOverflowItemClick,
                    onOverflowDismiss = deps.onOverflowDismiss,
                    overflowIcon = deps.menuIcon,
                    paneOverlay = { windowId ->
                        val window = layout.windows.firstOrNull { it.id == windowId }
                        PaneWindowButtonOverlay(
                            windowId = windowId,
                            window = window,
                            isActive = windowId == layout.activeWindowId,
                            showButton = showPaneButtons,
                            autoHidden = paneButtonsAutoHidden,
                            autoPin = layout.autoPin,
                            // Currently unreachable as true: `showPaneButtons` is a SINGLE value shared by
                            // every window and is false whenever ANY window is maximised. Kept per-window
                            // (not hard-coded false) because it is the correct value if a future change
                            // ever composes pane buttons during maximise.
                            isMaximised = layout.maximizedWindowId == windowId,
                            nightMode = deps.nightMode.value,
                            disableAnimations = deps.disableAnimations(),
                            monochrome = deps.monochrome.value,
                            // This surface only reports a menu open when it (not the rail) is the anchor.
                            paneMenuWindowId = menuWindowIdFor(PaneMenuAnchor.Pane, paneMenuAnchor, paneMenuWindowId),
                            paneMenuItems = paneMenuItems,
                            controller = controller,
                            onOpenPaneMenu = state::openPaneMenu,
                            onPaneMenuItemClick = { id, item -> onPaneMenuItemClick(deps, id, item) },
                            onPaneMenuDismiss = state::closePaneMenu,
                            icon = deps.menuIcon,
                        )
                    },
                    agentLog = deps.agentLogSlot,
                    speakBar = deps.speakBarSlot,
                    agentLogVisible = deps.agentLogVisible(),
                    speakBarVisible = deps.speakBarVisible(),
                    bottomOverlay = { BibleReferenceOverlay(visible = overlayVisible, text = overlayText) },
                    tabBar = if (hideTabBarInFullScreen) null else {
                        { applyNavBarInset ->
                            WindowTabBar(
                                // ReadingViewScreen passes true only when the strip is the bottom-most
                                // surface and the column has no IME padding.
                                modifier = Modifier.readingRailInsetPadding(applyNavBarInset),
                                model = tabBarModel,
                                onRestore = controller::onRestore,
                                // A rail long-press opens the SAME per-window menu as tapping the
                                // floating pane button, activating the window first (classic
                                // `SplitBibleArea.showPopupMenu`).
                                onWindowLongPress = { id ->
                                    controller.onWindowActivated(id)
                                    state.openPaneMenu(id, PaneMenuAnchor.Rail)
                                },
                                // The rail renders its OWN anchored `WindowPaneMenu`, only when the
                                // rail (not the pane overlay) is the anchor. See [menuWindowIdFor].
                                menuWindowId = menuWindowIdFor(PaneMenuAnchor.Rail, paneMenuAnchor, paneMenuWindowId),
                                menuItems = paneMenuItems,
                                onMenuItemClick = { id, item -> onPaneMenuItemClick(deps, id, item) },
                                onMenuDismiss = state::closePaneMenu,
                                menuIcon = deps.menuIcon,
                                onAddWindow = { controller.onAddWindow(layout.activeWindowId) },
                                onUnMaximise = controller::onUnMaximise,
                                onToggleCollapse = {
                                    controller.onSetRestoreButtonsVisible(!layout.restoreButtonsVisible)
                                },
                                windowLabel = deps.windowLabel,
                                windowIcon = deps.windowIcon,
                                windowTopLabel = deps.windowTopLabel,
                            )
                        }
                    },
                )
            }
        }
    }
    // Siblings of the screen (not nested inside `key(gen)`, which only needs to scope the panes): an
    // `AlertDialog` AND a `ModalBottomSheet` each overlay regardless of where in the tree they are
    // composed, and at most one non-`None` arm shows at a time. The LLM sheets are subject to
    // `ReadingOverlayExclusion`, applied where the host constructs its LLM dialog controller.
    val llmDialog by deps.llmDialog.collectAsState()
    ReadingLlmDialogs(
        dialog = llmDialog.dialog,
        onPromptChosen = deps.onLlmPromptChosen,
        onToggleFavorite = deps.onLlmToggleFavorite,
        onCategoryExpandedChanged = deps.onLlmCategoryExpandedChanged,
        onSpecifySubmitted = deps.onLlmSpecifySubmitted,
        onModelChosen = deps.onLlmModelChosen,
        onRegenerateConfirmed = deps.onLlmRegenerateConfirmed,
        onDismiss = deps.onLlmDismiss,
    )
    // The speak-from-bookmark chooser.
    val speakDialog by deps.speakDialog.collectAsState()
    (speakDialog as? SpeakTransportDialog.ChooseSpeakBookmark)?.let { d ->
        ChooseSpeakBookmarkDialog(
            rows = d.rows,
            onChoose = deps.onSpeakBookmarkChosen,
            onDismiss = deps.onSpeakDialogDismiss,
        )
    }
    // The remaining sibling overlays. Each `ModalBottomSheet` renders in its own window regardless of
    // where it is composed, so opening one can never re-key the pane subtree and destroy the panes'
    // WebViews; each one self-hides when closed, so each stays unconditional here.
    deps.searchSettingsSlot()
    deps.textSettingsEditorSlot()
    deps.speakSettingsSlot()
    // The quick sheets: [ReadingHostState.quickSheet] says WHEN one is open, the host says what is in it.
    QuickSheetMount(state, deps.quickSheetContent)
    deps.readingDialogSlot()
}

/**
 * Reads [ReadingHostState.quickSheet] in its own scope, so opening or closing a quick sheet recomposes
 * only this mount and not the whole reading host (the old host slot read it the same way).
 */
@Composable
private fun QuickSheetMount(state: ReadingHostState, content: @Composable (ReadingQuickSheet) -> Unit) {
    state.quickSheet.value?.let { sheet ->
        Box(Modifier.testTag(ReadingQuickSheetTag)) { content(sheet) }
    }
}

/**
 * A ☰ menu row was chosen: the host runs it, and a menu that asks to stay open gets its items
 * rebuilt (a toggle row changes its own label), otherwise it closes.
 */
private fun onPaneMenuItemClick(deps: ReadingHostDeps, windowId: String, id: String) {
    if (deps.handlePaneMenuItem(windowId, id)) {
        deps.state.refreshPaneMenuItems(windowId)
    } else {
        deps.state.closePaneMenu()
    }
}

/**
 * How much of the window the reading-view search sheet may take at most. Its content is otherwise
 * self-sizing, so a short index prompt does not become a half-screen panel.
 */
private const val SearchSheetMaxHeightFraction = 0.6f

/** Bound for [DriveSearchSheet]'s expand retry: ~10 x 50ms, i.e. well under a second. */
private const val SearchSheetExpandAttempts = 10
private const val SearchSheetExpandRetryMillis = 50L

/**
 * Vertical drag distance on the pane ☰ button that counts as swipe-to-maximise/minimise (iOS uses a
 * 28pt threshold; classic's own fling-velocity gesture has no direct dp equivalent, so this port
 * matches the iOS distance threshold instead of reproducing classic's `ViewConfiguration` fling detector).
 */
private val PaneButtonDragThresholdDp = 28.dp

/**
 * Keeps the (always-present) search sheet in step with the session's `sheetVisible`, both ways.
 *
 * Its own function rather than two `LaunchedEffect`s inline for two reasons: the conditional inside
 * it would otherwise sit directly above the `BottomSheetScaffold` call, which
 * `SearchSheetStructureGuardTest` reads as the scaffold becoming conditional; and the two
 * non-obvious mechanics below deserve one place to be explained.
 *
 * **`expand()`, not `partialExpand()`.** With `sheetPeekHeight = 0.dp` the `PartiallyExpanded`
 * anchor sits at `layoutHeight - peekHeight`, i.e. exactly where `Hidden` sits, so a partial
 * expand animates to an invisible sheet, and `show()` (which prefers `PartiallyExpanded` whenever
 * that anchor exists) would do the same. `Expanded` puts the sheet at its own content height, which
 * with the cap in `sheetContent` is what makes both the short index panel and the tall result list
 * look right.
 *
 * **The expand is retried.** The `Expanded` anchor is published while the sheet is MEASURED, and
 * this effect can run before that layout pass, in which case the animation is a no-op and the sheet
 * stays hidden with the session believing otherwise. Bounded retries close that window; the 1.dp
 * floor on the sheet content makes it a narrow one.
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
    // A sheet dragged away must reach the session too: otherwise `sheetVisible` stays true, the effect
    // above never re-fires, and the next `open()` for the same query looks dead. Anything other than
    // `Expanded` is invisible here, so it all counts as dismissed; the initial `Hidden` emission is
    // harmless (`closeSheet()` returns false when nothing is open).
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.currentValue }.collect { if (it != SheetValue.Expanded) onDismissedByUser() }
    }
}

/**
 * Turns the host's pending "cannot be searched" document name into a real `Snackbar`. [docName]
 * transitions null -> name -> null (the host clears it via [onShown] the moment this effect starts,
 * not when the snackbar is dismissed) so a second `open()` on the same kind of document, even with
 * the identical name, still restarts the effect and shows again.
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
        snackbarHostState.showSnackbar(searchUnavailableMessage(docName, strings))
    }
}

/**
 * The "cannot be searched" snackbar's text. The doc name is composed from the current document's
 * name, and "there is no current document" is itself one of the unavailable cases, so a blank name
 * is reachable and the parameterised string would then read " cannot be searched".
 */
@kotlin.native.HiddenFromObjC
fun searchUnavailableMessage(docName: String, strings: Strings): String =
    if (docName.isBlank()) strings.searchNotAvailable
    else strings.searchNotAvailableForDocument(docName)

/**
 * One pane's floating ☰ button + its [WindowPaneMenu] popup (invoked inside each visible pane's `Box`,
 * see `SplitContent`'s kdoc). Compose port of classic `BibleFrame`'s per-pane window button
 * (`screen/BibleFrame.kt:156-194`) + `WindowButtonGestureListener` (`:43-83`).
 *
 * The button itself is gated by [showButton] (`hide_window_buttons` / maximised): those are classic's
 * two HARD removals (`screen/BibleFrame.kt:157-158`). [WindowPaneMenu] is ALWAYS composed as its
 * sibling: a menu opened via the always-visible restore rail's long-press must still be able to
 * render anchored to this pane even when the floating button itself is hidden.
 *
 * The 2s idle timer ([autoHidden]) is NOT a third hard gate: classic fades the button to
 * [paneButtonHiddenAlpha] rather than removing it (`SplitBibleArea.toggleWindowButtonVisibility`,
 * `SplitBibleArea.kt:528-575`), so it stays present and tappable while faded: [Modifier.alpha] only,
 * never a size/visibility change.
 *
 * Gestures (classic `WindowButtonGestureListener` dispatch, `screen/BibleFrame.kt:185-191`): tap ->
 * [onOpenPaneMenu]; long-press -> minimise; swipe-up -> maximise; swipe-down -> minimise. Every
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
    icon: @Composable (iconKey: String) -> Painter? = { null },
) {
    Box(Modifier.align(Alignment.TopEnd)) {
        if (showButton && window != null) {
            var accumDy by remember(windowId) { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val thresholdPx = remember(density) { with(density) { PaneButtonDragThresholdDp.toPx() } }
            // Classic's idle-timer fade (`SplitBibleArea.kt:528-575`), NOT a removal from composition.
            // `targetAlpha` picks the button's resting state; `animateFloatAsState` supplies the tween
            // classic drives via `ViewPropertyAnimator`.
            val targetAlpha = if (autoHidden) {
                paneButtonHiddenAlpha(nightMode, disableAnimations, monochrome)
            } else 1f
            val alpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = tween(
                    durationMillis = paneButtonFadeMillis(disableAnimations),
                    // Classic uses DecelerateInterpolator on show and AccelerateInterpolator on hide
                    // (SplitBibleArea.kt:552,557); these are their M3 equivalents.
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
