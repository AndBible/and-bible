package net.bible.android.view.activity.page.screen

import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.passage.CurrentVerseChangedEvent
import net.bible.android.control.page.CurrentBibleVerseChanged
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.toolbar.ToolbarStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.nav.NavHostComposeActivity
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.android.view.activity.page.bibleViewBackgroundColorFor
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.nav.NavRoutes
import net.bible.sharedcore.progress.ReadHistoryEntry
import net.bible.sharedcore.progress.ReadingProgressService
import net.bible.sharedcore.reading.ReadingQuickSheet
import net.bible.sharedcore.reading.ShareVersesEntry
import net.bible.sharedcore.reading.ShareVersesInput
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.speak.SpeakSheetPage
import net.bible.sharedcore.window.RailEntry
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.progress.ReadHistoryRow
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** No-op fake — mockk was removed from this repo (Batch 12a T5 fix wave 1); no mocking framework is used. */
private val noopCommands = object : WindowCommands {
    override fun setActive(windowId: String) {}
    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {}
    override fun addNewWindow(fromWindowId: String) {}
    override fun minimise(windowId: String) {}
    override fun close(windowId: String) {}
    override fun restore(windowId: String) {}
    override fun maximise(windowId: String) {}
    override fun unMaximise() {}
    override fun setPin(windowId: String, value: Boolean) {}
    override fun move(windowId: String, position: Int) {}
    override fun setSynchronised(windowId: String, value: Boolean) {}
    override fun changeSyncGroup(windowId: String, group: Int) {}
    override fun focusNext() {}
    override fun focusPrevious() {}
    override fun setRestoreButtonsVisible(value: Boolean) {}
}

/**
 * Records every call — used by [ComposeReadingViewHostTest] to assert that the window-tab rail's
 * callbacks (Task 7) delegate to [ReadingViewController]'s `on*` forwards exactly the way
 * `ComposeReadingViewHost.mountComposeView`'s `tabBar` block wires them, without needing a
 * `ComposeTestRule` (this repo's `:app` unit tests have none). Mirrors sharedCore's
 * `ReadingViewControllerTest.RecordingCommands`.
 */
private class RecordingCommands : WindowCommands {
    val calls = mutableListOf<Pair<String, List<Any?>>>()
    override fun setActive(windowId: String) { calls += "setActive" to listOf(windowId) }
    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {
        calls += "commitWeights" to listOf(windowId1, weight1, windowId2, weight2)
    }
    override fun addNewWindow(fromWindowId: String) { calls += "addNewWindow" to listOf(fromWindowId) }
    override fun minimise(windowId: String) { calls += "minimise" to listOf(windowId) }
    override fun close(windowId: String) { calls += "close" to listOf(windowId) }
    override fun restore(windowId: String) { calls += "restore" to listOf(windowId) }
    override fun maximise(windowId: String) { calls += "maximise" to listOf(windowId) }
    override fun unMaximise() { calls += "unMaximise" to listOf() }
    override fun setPin(windowId: String, value: Boolean) { calls += "setPin" to listOf(windowId, value) }
    override fun move(windowId: String, position: Int) { calls += "move" to listOf(windowId, position) }
    override fun setSynchronised(windowId: String, value: Boolean) { calls += "setSynchronised" to listOf(windowId, value) }
    override fun changeSyncGroup(windowId: String, group: Int) { calls += "changeSyncGroup" to listOf(windowId, group) }
    override fun focusNext() { calls += "focusNext" to listOf() }
    override fun focusPrevious() { calls += "focusPrevious" to listOf() }
    override fun setRestoreButtonsVisible(value: Boolean) { calls += "setRestoreButtonsVisible" to listOf(value) }
}

private fun win(
    id: String,
    pin: Boolean = false,
    links: Boolean = false,
    state: WindowStateValue = WindowStateValue.VISIBLE,
) = WindowSnapshot(
    id = id,
    state = state,
    weight = 1.0f,
    isVisible = state == WindowStateValue.VISIBLE,
    isPinMode = pin,
    isSynchronised = false,
    syncGroup = 0,
    isLinksWindow = links,
)

private fun layout(
    windows: List<WindowSnapshot>,
    active: String = windows.firstOrNull()?.id ?: "",
    maximized: String? = null,
    restoreVisible: Boolean = true,
) = WindowLayoutState(
    windows = windows,
    activeWindowId = active,
    maximizedWindowId = maximized,
    reverseSplitMode = false,
    restoreButtonsVisible = restoreVisible,
)

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ComposeReadingViewHostTest {
    // Verifies the host mounts a ComposeView into the supplied container (no MainBibleActivity boot).
    @Test fun installAddsComposeViewChild() {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())
        // ComposeReadingViewHost.mountComposeView is a static/companion helper taking the
        // collaborators explicitly so it can be unit-tested without a full activity.
        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightModeState = mutableStateOf(false),
            pane = { },
        )
        assertTrue((0 until container.childCount).any { container.getChildAt(it) is ComposeView })
    }

    /**
     * `nightModeState`/`fullScreenState` are `State<Boolean>` (Task 5), not one-shot `Boolean`s,
     * specifically so an external owner (the real `ComposeReadingViewHost`'s
     * `NightModeChanged`/`FullScreenEvent` subscriptions, or this test) can flip them after mount
     * and have `ReadingViewScreen` recompose off the NEW value rather than a value frozen at mount
     * time. Mounting succeeds and the externally-owned states remain independently mutable after
     * mount — the actual "does the toolbar disappear" visual behavior is already golden-tested at
     * the `ReadingViewScreen` level (`ReadingViewScreenGoldenTest`'s fullScreen case, Task 4); this
     * repo's :app JVM unit tests have no `ComposeTestRule`, so a deeper interaction/recomposition
     * assertion isn't feasible here.
     */
    @Test fun installAcceptsMutableNightModeAndFullScreenState() {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())
        val nightModeState = mutableStateOf(false)
        val fullScreenState = mutableStateOf(false)
        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightModeState = nightModeState,
            fullScreenState = fullScreenState,
            pane = { },
        )
        assertTrue((0 until container.childCount).any { container.getChildAt(it) is ComposeView })

        // Flipping the externally-owned states after mount must not throw, and the states
        // themselves (owned by the caller, not copied) reflect the new values immediately.
        nightModeState.value = true
        fullScreenState.value = true
        assertTrue(nightModeState.value)
        assertTrue(fullScreenState.value)
    }

    /**
     * Batch 12b follow-on Plan B Task 5: the ☰-button auto-hide + per-window-menu `State`s are
     * externally owned (by the real host's [WindowButtonsVisibility] + `paneMenuWindowId`/
     * `paneMenuItems` fields), exactly like `nightModeState`/`fullScreenState` above — mounting
     * must accept them and leave them independently mutable, without a `ComposeTestRule` deeper
     * interaction assertion (same boundary as `installAcceptsMutableNightModeAndFullScreenState`).
     */
    @Test fun installAcceptsWindowButtonsAndPaneMenuState() {
        val container = FrameLayout(ApplicationProvider.getApplicationContext())
        val windowButtonsVisibleState = mutableStateOf(true)
        val touchTickState = mutableIntStateOf(0)
        val paneMenuWindowIdState = mutableStateOf<String?>(null)
        val paneMenuItemsState = mutableStateOf(emptyList<WindowPaneMenuItem>())
        var hideTimeoutCalls = 0
        var openedPaneMenuFor: String? = null
        var openedPaneMenuAnchor: PaneMenuAnchor? = null

        ComposeReadingViewHost.mountComposeView(
            container = container,
            windowState = WindowStateServiceImpl(),
            commands = noopCommands,
            nightModeState = mutableStateOf(false),
            pane = { },
            windowButtonsVisibleState = windowButtonsVisibleState,
            touchTickState = touchTickState,
            onWindowButtonsHideTimeout = { hideTimeoutCalls++ },
            paneMenuWindowIdState = paneMenuWindowIdState,
            paneMenuItemsState = paneMenuItemsState,
            onOpenPaneMenu = { id, anchor -> openedPaneMenuFor = id; openedPaneMenuAnchor = anchor },
        )
        assertTrue((0 until container.childCount).any { container.getChildAt(it) is ComposeView })

        windowButtonsVisibleState.value = false
        touchTickState.intValue++
        paneMenuWindowIdState.value = "w1"
        assertFalse(windowButtonsVisibleState.value)
        assertEquals(1, touchTickState.intValue)
        assertEquals("w1", paneMenuWindowIdState.value)
        // The callbacks themselves are simple pass-throughs owned by the caller (real wiring is
        // `ComposeReadingViewHost.install`'s `onOpenPaneMenu = ::openPaneMenu`/
        // `onWindowButtonsHideTimeout = windowButtonsVisibility::onHideTimeout`); confirm the
        // defaults compile/mount without them ever having been invoked by `mountComposeView`
        // itself (composition never runs without an attached window — see `pane = {}` note above).
        assertEquals(0, hideTimeoutCalls)
        assertEquals(null, openedPaneMenuFor)
        assertEquals(null, openedPaneMenuAnchor)
    }

    /**
     * Batch 12b follow-on, Plan A Task 7: with >= 2 (non-closed) windows,
     * `ComposeReadingViewHost.buildTabBarModel` — the non-`@Composable` helper `mountComposeView`
     * calls to derive the rail model — must delegate verbatim to `buildWindowTabBar` (Task 4), so
     * `showButtons` reflects `WindowLayoutState.restoreButtonsVisible` exactly as the sharedCore
     * derivation dictates.
     */
    @Test fun buildTabBarModelReflectsRestoreButtonsVisible() {
        val twoWindows = listOf(win("a"), win("b"))

        val expandedLayout = layout(windows = twoWindows, restoreVisible = true)
        val expandedModel = ComposeReadingViewHost.buildTabBarModel(expandedLayout)
        assertEquals(buildWindowTabBar(expandedLayout), expandedModel)
        assertTrue(expandedModel.showButtons)
        assertEquals(2, expandedModel.entries.count { it is RailEntry.WindowTab })

        val collapsedLayout = layout(windows = twoWindows, restoreVisible = false)
        val collapsedModel = ComposeReadingViewHost.buildTabBarModel(collapsedLayout)
        assertEquals(buildWindowTabBar(collapsedLayout), collapsedModel)
        assertFalse(collapsedModel.showButtons)
    }

    /**
     * Verifies the exact wiring pattern `mountComposeView`'s `tabBar` block uses for
     * `WindowTabBar`'s `onRestore`/`onAddWindow`/`onUnMaximise`/`onToggleCollapse` callbacks
     * (`controller::onRestore`, `controller.onAddWindow(layout.activeWindowId)`,
     * `controller::onUnMaximise`, `controller.onSetRestoreButtonsVisible(!layout.restoreButtonsVisible)`)
     * reaches the injected [WindowCommands] — a UI-level assert (actually tapping a rendered
     * `WindowButton`) isn't practical under Robolectric (no `ComposeTestRule` in this repo's `:app`
     * unit tests), so this exercises the same call expressions directly against a
     * [RecordingCommands] fake, mirroring `ReadingViewControllerTest` one layer up (at the exact
     * call pattern the host uses).
     */
    @Test fun tabBarCallbacksMapToControllerCommands() {
        val cmds = RecordingCommands()
        val controller = ReadingViewController(WindowStateServiceImpl(), cmds)
        val currentLayout = layout(windows = listOf(win("a"), win("b")), active = "a", restoreVisible = true)

        controller.onRestore("b")
        controller.onAddWindow(currentLayout.activeWindowId)
        controller.onUnMaximise()
        controller.onSetRestoreButtonsVisible(!currentLayout.restoreButtonsVisible)

        assertEquals(
            listOf(
                "restore" to listOf<Any?>("b"),
                "addNewWindow" to listOf<Any?>("a"),
                "unMaximise" to listOf<Any?>(),
                "setRestoreButtonsVisible" to listOf<Any?>(false),
            ),
            cmds.calls,
        )
    }

    /**
     * Batch Z-early A7 fix F drift guard: the drawer's icons are resolved through the host's
     * explicit `drawerIconResIds` table (direct `R.drawable` references, so resource shrinking
     * keeps them) instead of a per-recomposition `resources.getIdentifier`. Nothing forces that
     * table to keep up with [DrawerMenuStateBuilder]'s own icon column, so assert it here — a new
     * or renamed drawer icon fails this test instead of silently rendering an icon-less row.
     * `ic_logo` is not in the builder's table: `ReadingDrawerContent`'s header asks for it by name.
     *
     * Fix round 2 (R13): goes through [ComposeReadingViewHost.drawerIconResIdFor], not the raw
     * `drawerIconResIds` map directly, since `"ic_logo"` is resolved at call time (F58 discrete
     * mode) — asserting the map alone would miss a regression in that call-time branch while still
     * exercising every other key's coverage exactly as before.
     *
     * Per-method `@Config` override (class default is a plain `android.app.Application`, which
     * never runs `BibleApplication.onCreate()`): [drawerIconResIdFor]'s `"ic_logo"` branch reads
     * `CommonUtils.isDiscrete`, which needs `BibleApplication.application` to be set, so this one
     * test needs [TestBibleApplication] — the rest of this class stays on the lighter plain
     * `Application` it already used.
     */
    @Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
    @Test fun drawerIconResIdsCoverEveryBuilderIconKey() {
        for (iconName in DrawerMenuStateBuilder.entryIconNames + "ic_logo") {
            val resId = ComposeReadingViewHost.drawerIconResIdFor(iconName)
            assertTrue(resId != null && resId != 0, "no drawable mapped for drawer iconKey '$iconName'")
        }
    }

    /**
     * A/B batch 1 F5b fix round 1: state-independent drift guard for [ComposeReadingViewHost.menuIconResIds].
     *
     * `WindowPaneMenuStateBuilderTest.everyPaneMenuIconKeyIsResolvableByTheHost` and
     * `OptionsMenuStateBuilderTest.everyOverflowIconKeyIsResolvableByTheHost` only check the
     * `iconKey`s a SINGLE default-fixture item list actually emits at the moment it's built — and
     * most `window_popup_menu` rows are gated behind state a fresh single-window workspace never
     * produces (`changeToNormal`/`moveWindowSubMenu` need a links or a same-pin-mode sibling window,
     * `pinMode` needs auto-pin off, `addWholePageBookmark`/`exportHtml`/`exportStudypad`/
     * `exportStudypadCsv` need a non-Bible/StudyPad document, `goToReference` needs a clipboard key,
     * `goToSpeak` needs TTS running, `windowClose` needs a second window, `llmActionsSubMenu` needs
     * an `LlmProviderConfig` row). Those two tests alone would miss a rename or removal of an entry
     * one of those gated rows depends on. This test is state-independent by construction instead —
     * mirrors [drawerIconResIdsCoverEveryBuilderIconKey]'s static, exhaustive list rather than a
     * live, gated item list — and its `assertEquals` on the full key SET checks both directions at
     * once: a key removed/renamed here (the silent on-device icon-loss regression this whole task
     * exists to prevent) and a dead key left in the map that no builder ever emits.
     */
    @Test fun menuIconResIdsIsExactlyTheseTwentyTwoKeys() {
        val expected = setOf(
            "ic_window_add_outline_black_24dp",
            "ic_window_maximise_24dp",
            "ic_baseline_minimise_24",
            "ic_link_black_24dp",
            "ic_window_move_to_24dp",
            "ic_pin",
            "ic_window_sync_24dp",
            "ic_baseline_bookmark_24",
            "file_export",
            "ic_text_options_24dp",
            "ic_content_copy_black_24dp",
            "baseline_content_paste_24",
            "ic_baseline_headphones_24",
            "ic_close_white_24dp",
            "ic_full_screen_24",
            "ic_night_mode_24",
            "ic_baseline_workspace_24",
            "ic_tilt_to_scroll_24dp",
            "ic_reverse_split_mode_24dp",
            "ic_window_pinning_24",
            "ic_label_settings_24",
            "icon_robot",
        )
        assertEquals(expected, ComposeReadingViewHost.menuIconResIds.keys)
    }
}

/**
 * Fix wave 1 (stale-BibleView remount): [ComposeReadingViewGeneration] is the framework-free
 * counter [ComposeReadingViewHost.rebuild] delegates to. This is exercised directly (no
 * MainBibleActivity/Koin boot needed) because a headless unit test can't observe the real effect
 * (a destroyed-vs-fresh BibleView) — see `ComposeReadingViewHost`'s `generation`/`rebuild` kdoc for
 * why the bump is needed: `key(gen) { ... }` around `ReadingViewScreen` forces every pane's
 * `AndroidView` factory to re-run after a same-workspace reload clears the BibleView cache.
 */
class ComposeReadingViewGenerationTest {
    @Test fun rebuildBumpsGeneration() {
        val generation = ComposeReadingViewGeneration()
        assertEquals(0, generation.state.value)
        generation.rebuild()
        assertEquals(1, generation.state.value)
        generation.rebuild()
        generation.rebuild()
        assertEquals(3, generation.state.value)
    }
}

/**
 * Batch 12b follow-on Plan B Task 5: [WindowButtonsVisibility] is the framework-free holder the
 * real [ComposeReadingViewHost] wires to [net.bible.android.view.activity.page.BibleView.BibleViewTouched]
 * (via `ABEventBus.register`'s `onMain<BibleView.BibleViewTouched> { windowButtonsVisibility.onTouch() }`
 * in [ComposeReadingViewHost]'s `init`) — exercised directly here (no MainBibleActivity/Koin boot
 * needed), mirroring [ComposeReadingViewGenerationTest] one class up. [onTouch] is exactly what
 * that subscription calls on a real touch, so asserting it flips [WindowButtonsVisibility.visible]
 * back to `true` (after [WindowButtonsVisibility.onHideTimeout] set it `false`) is the direct,
 * framework-free equivalent of "a `BibleViewTouched` sets [visible] true" the Task-5 brief asks
 * for.
 */
class WindowButtonsVisibilityTest {
    @Test fun startsVisibleWithATouchTickOfZero() {
        val v = WindowButtonsVisibility()
        assertTrue(v.visible.value)
        assertEquals(0, v.touchTick.value)
    }

    @Test fun onHideTimeoutHidesTheButtons() {
        val v = WindowButtonsVisibility()
        v.onHideTimeout()
        assertFalse(v.visible.value)
    }

    @Test fun onTouchReShowsTheButtonsAndBumpsTheTouchTick() {
        val v = WindowButtonsVisibility()
        v.onHideTimeout()
        assertFalse(v.visible.value, "sanity: hidden before the touch")

        v.onTouch()

        assertTrue(v.visible.value, "a touch (BibleViewTouched) must re-show the buttons")
        assertEquals(1, v.touchTick.value)

        v.onTouch()
        assertEquals(2, v.touchTick.value, "every touch bumps the tick so the host's LaunchedEffect(touchTick) restarts its 2s countdown")
    }
}

/**
 * Batch 12b follow-on Plan B Task 5: exercises [ReadingCommands.handleWindowPaneMenuItem]
 * against a REAL [WindowControl]/[WindowRepository]/[net.bible.android.control.page.window.Window]
 * graph (Robolectric + [TestBibleApplication], same style as
 * [net.bible.android.view.activity.page.OptionsMenuStateBuilderTest] /
 * [net.bible.android.view.activity.page.WindowPaneMenuStateBuilderTest]) rather than mocking the
 * collaborators. The activity is built WITHOUT `.create()` (same rationale as those two tests):
 * `handleWindowPaneMenuItem`'s atomic branches only touch `windowRepository`/`windowControl`
 * (+ optionally a real [ComposeReadingViewHost], which itself never calls `install()` here, so
 * `activity.binding` is never touched) — except `startActivityForResult` (the `allTextOptions`
 * bridge), which needs `ActivityBase.historyTraversal` primed via `setNewHistoryTraversal`
 * (normally done in `onCreate()`); done explicitly in [setUp] rather than calling `.create()`, to
 * keep the same minimal-boot footprint as [net.bible.android.view.activity.page.OptionsMenuStateBuilderTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class MainBibleActivityHandleWindowPaneMenuItemTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    /** Builds a real [ComposeReadingViewHost] against the test [activity] (never `.install()`ed — see the class kdoc). */
    private fun host() = ComposeReadingViewHost(activity)

    /**
     * A/B batch 3 F5b: [ComposeReadingViewHost.openPaneMenu] now takes a [PaneMenuAnchor] so the
     * host can tell which surface (the rail vs. the pane's own floating ☰ button) opened the menu
     * — the root cause of the reported bug was that only ONE `paneMenuWindowId` existed and BOTH
     * surfaces rendered a `WindowPaneMenu` gated on it alone, so a rail long-press expanded the
     * PANE's menu instead of the rail's own (Task 11's) menu. These three tests exercise the
     * anchor bookkeeping directly on the host (no `ComposeTestRule` in this repo's `:app` unit
     * tests — see the class kdoc's rationale for testing `handleWindowPaneMenuItem` this way).
     */
    /**
     * A/B batch 4a F5. [ComposeReadingViewHost.paneBackgroundArgbFor] must look the window up by the
     * id it is given, so each pane in a split can get its own reader background — a colour resolved
     * once for the whole split would be wrong for the second pane whenever the two windows carry
     * different day/night backgrounds.
     *
     * UPDATED A/B batch 4a whole-batch review C1: this test's ORIGINAL version compared against
     * `w1.bibleView?.backgroundColor`, which is `null` in this fixture (no `BibleView` exists — that
     * needs a real WebView) — so the pre-fix implementation (which read `Window.bibleView?.backgroundColor`)
     * happened to satisfy this exact assertion (`null == null`) while returning `null` for EVERY window,
     * including a brand-new real one whose pane is showing (the bug F5 exists to fix; see
     * `paneBackgroundArgbFor`'s kdoc). Now compared against the top-level [bibleViewBackgroundColorFor]
     * (which needs only a [net.bible.android.control.page.window.Window], no live `BibleView`), and the
     * non-null assertion below locks in the actual fix: resolution no longer depends on a `BibleView`
     * existing at all.
     */
    @Test fun paneBackgroundIsLookedUpByTheWindowIdItIsGiven() {
        val w1 = windowRepository.activeWindow
        val w2 = windowRepository.addNewWindow(w1)
        val host = host()

        assertEquals(bibleViewBackgroundColorFor(w1), host.paneBackgroundArgbFor(w1.id.toString()))
        assertEquals(bibleViewBackgroundColorFor(w2), host.paneBackgroundArgbFor(w2.id.toString()))
        assertTrue(
            host.paneBackgroundArgbFor(w1.id.toString()) != null,
            "must resolve to a real colour even with no live BibleView (C1) -- this is the exact case a" +
                " brand-new window's pane is in before its AndroidView factory has run",
        )
        assertNull(
            host.paneBackgroundArgbFor(IdType().toString()),
            "an id that matches no window must resolve to no background, not to the active window's",
        )
    }

    @Test fun openingFromTheRailRecordsTheRailAnchor() {
        val windowId = windowRepository.activeWindow.id.toString()
        val host = host()

        host.openPaneMenu(windowId, PaneMenuAnchor.Rail)

        assertEquals(windowId, host.paneMenuWindowIdForTest)
        assertEquals(PaneMenuAnchor.Rail, host.paneMenuAnchorForTest)
    }

    /**
     * Opens from the Rail FIRST, then from the Pane button, so this asserts a real transition
     * rather than merely matching [paneMenuAnchor]'s initial-default value ([PaneMenuAnchor.Pane])
     * — a deleted `paneMenuAnchor.value = anchor` assignment would leave the anchor stuck on
     * `Rail` from the first call and fail this test, whereas a single Pane-only open would pass
     * vacuously against the untouched default.
     */
    @Test fun openingFromThePaneButtonRecordsThePaneAnchor() {
        val windowId = windowRepository.activeWindow.id.toString()
        val host = host()
        host.openPaneMenu(windowId, PaneMenuAnchor.Rail)

        host.openPaneMenu(windowId, PaneMenuAnchor.Pane)

        assertEquals(PaneMenuAnchor.Pane, host.paneMenuAnchorForTest)
    }

    /**
     * [ComposeReadingViewHost.closePaneMenu] only clears [ComposeReadingViewHost.paneMenuWindowId]
     * — it does NOT reset [ComposeReadingViewHost.paneMenuAnchor], which is left stale at whatever
     * surface last opened a menu. That is harmless: [menuWindowIdFor] (what both surfaces actually
     * gate on) resolves to `null` once `paneMenuWindowId` is `null`, regardless of the stale anchor
     * — so this test pins down BOTH facts (the window id clears, the anchor does not) rather than
     * asserting a "clears both" guarantee that doesn't hold.
     */
    @Test fun closingClearsTheWindowIdButLeavesTheAnchorStale() {
        val windowId = windowRepository.activeWindow.id.toString()
        val host = host()

        host.openPaneMenu(windowId, PaneMenuAnchor.Rail)
        host.closePaneMenu()

        assertEquals(null, host.paneMenuWindowIdForTest)
        assertEquals(PaneMenuAnchor.Rail, host.paneMenuAnchorForTest, "closePaneMenu does not reset the anchor; harmless per menuWindowIdFor")
    }

    /**
     * Fallback path (no [ComposeReadingViewHost] installed, `activity.composeReadingViewHost ==
     * null`): `handleWindowPaneMenuItem` must still act, directly through [WindowControl] — the
     * "or directly windowControl" branch named in the Task-5 brief, and what keeps this dispatcher
     * unit-testable without booting the full Compose host.
     */
    @Test fun windowMinimiseActsDirectlyThroughWindowControlWhenNoHostIsInstalled() {
        val w1 = windowRepository.activeWindow
        windowRepository.addNewWindow(w1) // second window, so w1 is minimizable
        assertTrue(windowControl.isWindowMinimizable(w1), "sanity")
        assertNull(activity.composeReadingViewHost, "sanity: fallback path")

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)

        assertFalse(stayOpen, "an action closes the menu")
        assertFalse(w1.isVisible, "windowMinimise must actually minimise the window")
    }

    /**
     * The PRIMARY (production) path: with a real [ComposeReadingViewHost] installed,
     * `handleWindowPaneMenuItem` routes an atomic item through `composeReadingViewHost.controller`
     * (the Plan-A command seam) — the same seam the pane overlay's own gestures and the restore
     * rail drive — rather than the fallback. The controller's [ReadingViewController.onMinimise]
     * is itself backed by the REAL `WindowCommandsImpl`/`WindowControl` (Koin), so the observable
     * effect is identical to the fallback-path test above; what this test additionally proves is
     * that the controller-seam branch executes cleanly end-to-end (no NPE/host-wiring regression).
     */
    @Test fun windowMinimiseRoutesThroughTheRealControllerSeamWhenHostIsInstalled() {
        val w1 = windowRepository.activeWindow
        windowRepository.addNewWindow(w1)
        assertTrue(windowControl.isWindowMinimizable(w1), "sanity")

        activity.composeReadingViewHost = ComposeReadingViewHost(activity)

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)

        assertFalse(stayOpen)
        assertFalse(w1.isVisible, "windowMinimise via the controller seam must minimise the window")
    }

    /** `pinMode` is a checkable toggle (mirrors classic's `isBoolean` `CommandPreference`): stays open. */
    @Test fun pinModeTogglesAndStaysOpen() {
        windowRepository.workspaceSettings.autoPin = false
        val w1 = windowRepository.activeWindow
        w1.isPinMode = false

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_PIN_MODE)

        assertTrue(stayOpen, "a checkable toggle must tell the host to stay open (and rebuild)")
        assertTrue(w1.isPinMode)
    }

    /** `windowClose` acts through the seam/`WindowControl` and does not stay open. */
    @Test fun windowCloseActsAndCloses() {
        val w1 = windowRepository.activeWindow
        val w2 = windowRepository.addNewWindow(w1)
        assertTrue(windowControl.isWindowRemovable(w2))

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(w2.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE)

        assertFalse(stayOpen)
        assertNull(windowRepository.getWindow(w2.id), "windowClose must actually remove the window")
    }

    /**
     * A BRIDGED id (`"allTextOptions"`) invokes a native launch — `SplitBibleArea`'s window-level
     * counterpart — rather than acting through the command seam, and reports `false` (closes the
     * menu).
     *
     * Slice 8 B6: the target is the nav graph's text-settings route, scoped to the window the row
     * belongs to, launched with a plain `startActivity`. The classic `TextDisplaySettingsActivity` /
     * `TEXT_DISPLAY_SETTINGS_CHANGED` round-trip this used to assert is gone: the Compose screen
     * writes each edit through as it is made and returns no result. The scope extras are asserted
     * because the window id is the whole point of the WINDOW-level row — a launch that silently
     * fell back to global scope would still satisfy a bare class-name check.
     */
    @Test fun allTextOptionsLaunchesComposeTextDisplaySettingsForTheWindow() {
        val w1 = windowRepository.activeWindow

        val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS)

        assertFalse(stayOpen)
        // Robolectric records a plain startActivity as a for-result launch with requestCode -1, so
        // "no round-trip" is that sentinel rather than a missing record.
        assertEquals(
            -1,
            shadowOf(activity).nextStartedActivityForResult?.requestCode,
            "the classic TEXT_DISPLAY_SETTINGS_CHANGED round-trip is gone -- this is a plain startActivity",
        )
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(NavHostComposeActivity::class.java.name, started?.component?.className)
        val args = NavRoutes.readTextDisplaySettings(started!!.getStringExtra(NavHostComposeActivity.EXTRA_ROUTE)!!)
        assertEquals("window", args.scopeLevel)
        assertEquals(w1.id.toString(), args.windowId)
        assertEquals(windowRepository.id.toString(), args.workspaceId)
    }

    /**
     * A/B batch 4a F4: the picker dialog is gone — the target window's index is now carried
     * directly in the row's own id (`WindowPaneMenuStateBuilder.idForCopySettingsToWindow(order)`,
     * classic's shape), so `handleWindowPaneMenuItem` dispatches straight to
     * `windowControl.copySettingsToWindow(window, parsed.order)` with no intermediate resolution
     * step. That real method reads `CurrentActivityHolder.currentActivity!!.lifecycleScope`
     * (`WindowControl.kt:295`), which no other test in this class needs — activate/deactivate the
     * fixture activity around the call, mirroring what `ActivityBase.onResume`/`onPause` do for a
     * real activity, so the dispatch exercises the actual production call rather than short-
     * circuiting before it. This only exercises the synchronous part of that call
     * (`visibleWindows[order]` lookup, which throws immediately on a bad order): `copySettingsToWindow`
     * itself then hands off to a `scope.launch(Dispatchers.Main)` coroutine that opens a native
     * chooser dialog, which this Robolectric fixture never idles/drives — the correctness of the
     * ORDER itself (index into `visibleWindows` including the subject) is covered exhaustively by
     * `WindowPaneMenuStateBuilderTest.copySettingsToIsNestedUnderTextOptionsWithOneRowPerOtherVisibleWindow`.
     */
    @Test fun copySettingsToWindowDispatchesTheParsedOrderWithoutCrashing() {
        val w1 = windowRepository.activeWindow
        windowRepository.addNewWindow(w1)
        windowRepository.addNewWindow(w1)

        CurrentActivityHolder.activate(activity)
        try {
            val stayOpen = activity.readingCommands.handleWindowPaneMenuItem(
                w1.id.toString(),
                WindowPaneMenuStateBuilder.idForCopySettingsToWindow(1),
            )

            assertFalse(stayOpen, "an action closes the menu")

            // A/B batch 4a whole-batch review I4: the assertion above alone cannot tell "the parsed
            // order (1) was forwarded to WindowControl" from "a hardcoded 0 was forwarded instead" --
            // with 3 visible windows both 0 and 1 are in range, so either would pass it silently.
            // WindowControl.copySettingsToWindow (WindowControl.kt:366-367) indexes
            // `visibleWindows[order]` SYNCHRONOUSLY (before the coroutine launch), so dispatching an
            // order (5) that is out of range for this 3-window fixture must throw
            // IndexOutOfBoundsException -- it would NOT throw if the dispatcher silently substituted
            // some in-range order instead of the one actually parsed from the menu item id.
            assertFailsWith<IndexOutOfBoundsException> {
                activity.readingCommands.handleWindowPaneMenuItem(
                    w1.id.toString(),
                    WindowPaneMenuStateBuilder.idForCopySettingsToWindow(5),
                )
            }
        } finally {
            CurrentActivityHolder.deactivate(activity)
        }
    }

    @Test fun parseIdRoundTripsForAnAtomicId() {
        assertEquals(
            WindowPaneMenuStateBuilder.ParsedId.StaticItem(WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE),
            WindowPaneMenuStateBuilder.parseId(WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE),
        )
    }

    @Test fun handleWindowPaneMenuItemReturnsFalseForAnUnknownWindowId() {
        // A stale click after the window closed underneath it (`getWindow` returns null) must not throw.
        assertFalse(activity.readingCommands.handleWindowPaneMenuItem(IdType().toString(), WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE))
    }

    /**
     * Task 4 (F2b), fix round 1: with a real [ComposeReadingViewHost] installed,
     * `CurrentBibleVerseChanged` (classic's own trigger for the rail's tiny top label,
     * `WindowButtonWidget.kt:232-234`) must reach the host's `refreshHostedState()` push — the SAME
     * mechanism `updateActions()`'s callers use for the sibling `windowLabelFor`
     * (document-abbreviation) refresh — rather than the rail staying stale until some unrelated
     * event happens to recompose it.
     *
     * Asserted end-to-end (event posted -> `refreshHostedState()` -> `hostedStateRefresher.refresh()`
     * -> the fake's recorded refresh) by swapping the Koin-bound `ToolbarStateService` for the
     * existing [RecordingToolbarStateService] fake, scoped to this one test via
     * `loadKoinModules`/`unloadKoinModules` — the same "framework-free fake, driven through the real
     * mechanism" idiom [HostedStateRefresherTest] already uses one level down, just reached through
     * Koin instead of direct construction (this test needs the REAL [ComposeReadingViewHost]'s `init`
     * ABEventBus wiring, which `HostedStateRefresherTest` deliberately bypasses). This replaced an
     * earlier version that widened `ComposeReadingViewHost`/`refreshHostedState` to `open` for a
     * counting subclass — reverted once this composition-based route was confirmed to work, so the
     * production class's inheritance contract is unchanged.
     *
     * (An even earlier attempt asserted on the REAL Koin-singleton `ToolbarStateService`'s derived
     * snapshot directly, with no fake — that was empirically unreliable: `WindowRepository`'s own
     * async DB load raced with the test's synchronous assertions, so a silently-seeded document+verse
     * pair showed up split across two different `refresh()` calls. Substituting the fake sidesteps
     * that: the fake only counts calls, it never reads `WindowRepository`/`pageManager` state.)
     */
    @Test fun currentBibleVerseChangedTriggersARefresh() {
        val fakeToolbar = RecordingToolbarStateService()
        val overrideModule = module { single<ToolbarStateService> { fakeToolbar } }
        loadKoinModules(overrideModule)
        try {
            activity.composeReadingViewHost = ComposeReadingViewHost(activity)
            assertEquals(0, fakeToolbar.refreshCount, "sanity: constructing the host must not itself refresh")

            ABEventBus.post(CurrentBibleVerseChanged())

            assertEquals(1, fakeToolbar.refreshCount, "CurrentBibleVerseChanged must reach refreshHostedState() -> hostedStateRefresher.refresh() -> toolbarStateService.refresh()")
        } finally {
            // `unloadKoinModules` only REMOVES the override module's definition — it does NOT
            // restore the production `CoreModule` binding it replaced, and `GlobalContext` is
            // process-wide (shared by every test class in this Gradle test JVM fork). Left as a
            // bare `unloadKoinModules`, any later test in the same fork that constructs a
            // `ComposeReadingViewHost` (which injects `ToolbarStateService`) would throw
            // `NoDefinitionFoundException`. Re-install the exact same production definition
            // `CoreModule.kt` installs, so the singleton binding is intact again afterwards.
            unloadKoinModules(overrideModule)
            loadKoinModules(module { singleOf(::ToolbarStateServiceImpl) { bind<ToolbarStateService>() } })
            assertTrue(
                GlobalContext.get().get<ToolbarStateService>() is ToolbarStateServiceImpl,
                "must not leak the fake ToolbarStateService binding into later tests",
            )
        }
    }

    /**
     * Regression: scrolling a Bible window never updated the rail's per-window labels, because
     * `windowLabelFor`/`windowTopLabelFor` are plain reads and nothing told Compose to re-run them.
     * A label read through the host's [WindowLabelFreshness] must be invalidated (i.e. the rail
     * recomposes) when a window's verse changes — for ANY window, not just the active one.
     */
    private fun assertPostingInvalidatesLabelReads(event: Any) {
        activity.composeReadingViewHost = ComposeReadingViewHost(activity)
        val host = activity.composeReadingViewHost!!
        val reads = mutableSetOf<Any>()
        Snapshot.observe(readObserver = { reads += it }) { host.windowLabelFreshness.observed { "label" } }
        assertTrue(reads.isNotEmpty(), "sanity: the label read must subscribe to some state")
        val written = mutableSetOf<Any>()
        val handle = Snapshot.registerApplyObserver { changed, _ -> written.addAll(changed) }
        try {
            ABEventBus.post(event)
            Snapshot.sendApplyNotifications()
        } finally {
            handle.dispose()
        }
        assertTrue(reads.any { it in written }, "${event::class.simpleName} must invalidate composition scopes that read a window label")
    }

    @Test fun currentVerseChangedEventInvalidatesRailLabels() {
        assertPostingInvalidatesLabelReads(CurrentVerseChangedEvent(windowRepository.activeWindow))
    }

    @Test fun currentBibleVerseChangedInvalidatesRailLabels() {
        assertPostingInvalidatesLabelReads(CurrentBibleVerseChanged())
    }
}

/** Recording fake — no mocking framework in this repo (see the file header note). */
private class RecordingToolbarStateService : ToolbarStateService {
    var refreshCount = 0
    private val _toolbar = MutableStateFlow(ToolbarState.EMPTY)
    override val toolbar: StateFlow<ToolbarState> = _toolbar.asStateFlow()
    override fun refresh() { refreshCount++ }
}

/**
 * Platform-dialog removal Task 18 — the reading view's own dialogs
 * ([ComposeReadingViewHost.showHelp]/[ComposeReadingViewHost.showDeleteDocumentPageConfirm], driven
 * by `BibleJavascriptInterface`) and the Speak/Advanced-Speak help dialogs
 * ([ComposeReadingViewHost.showSpeakHelp]/[ComposeReadingViewHost.showAdvancedSpeakHelp]). State-only
 * assertions, same limit [MainBibleActivityHandleWindowPaneMenuItemTest]'s class kdoc documents (no
 * `ComposeTestRule` in this repo's `:app` unit tests) — same real-host fixture, copied verbatim.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadingDialogHostTest {
    private lateinit var windowControl: WindowControl
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: NavHostComposeActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(
            NavHostComposeActivity::class.java,
            NavHostComposeActivity.intentFor(ApplicationProvider.getApplicationContext(), NavRoutes.READING),
        ).get()
        activity.readingAppBootstrap.windowRepository = windowRepository
        activity.setNewHistoryTraversal(GlobalContext.get().get())
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    private fun host() = ComposeReadingViewHost(activity)

    /** `BibleJavascriptInterface.helpBookmarks`/`.helpDialog`'s entry point. */
    @Test fun showHelpSetsTheHelpDialogState() {
        val host = host()

        host.showHelp("Bookmarks & My Notes", "<b>Long-press</b> some text to bookmark it.")

        assertEquals(
            ReadingDialog.Help("Bookmarks & My Notes", "<b>Long-press</b> some text to bookmark it."),
            host.readingDialog.value,
        )
    }

    /** `helpDialog`'s title is nullable (unlike `helpBookmarks`' fixed title) — confirms `showHelp`
     *  carries a `null` title through rather than substituting one. */
    @Test fun showHelpAcceptsANullTitle() {
        val host = host()

        host.showHelp(null, "Experimental features are new capabilities that are still being tested.")

        assertEquals(
            ReadingDialog.Help(null, "Experimental features are new capabilities that are still being tested."),
            host.readingDialog.value,
        )
    }

    /**
     * `BibleJavascriptInterface.deleteMyDocumentPage`'s confirm: the deletion runs exactly once on
     * OK, never on Cancel/dismiss, and a duplicate confirm after the dialog already cleared is a
     * no-op — it must never re-run the deletion.
     */
    @Test fun deleteConfirmRunsOnceOnConfirmAndNeverOnDismiss() {
        val host = host()
        var confirmCalls = 0
        host.showDeleteDocumentPageConfirm { confirmCalls++ }
        assertIs<ReadingDialog.ConfirmDeleteDocumentPage>(host.readingDialog.value, "sanity: the confirm is showing")

        host.dismissReadingDialog()

        assertNull(host.readingDialog.value, "dismiss clears the dialog")
        assertEquals(0, confirmCalls, "Cancel/dismiss must never run the deletion")

        host.showDeleteDocumentPageConfirm { confirmCalls++ }
        host.confirmReadingDialog()

        assertEquals(1, confirmCalls, "OK runs the deletion exactly once")
        assertNull(host.readingDialog.value, "confirm clears the dialog")

        // Nothing is showing any more -- a duplicate confirm must be a no-op, not a second deletion.
        host.confirmReadingDialog()
        assertEquals(1, confirmCalls, "a duplicate confirm must not re-run the deletion")
    }

    /** The Speak help dialog renders OVER the open speak sheet: a dialog over a sheet is fine
     *  (`ReadingOverlayExclusion`'s kdoc), so opening it must not close the sheet underneath it. */
    @Test fun speakHelpRendersOverTheOpenSpeakSheet() {
        val host = host()
        host.showSpeakSettings()
        assertEquals(SpeakSheetPage.Settings, host.speakSheet.current, "sanity: the sheet is open")

        host.showSpeakHelp()

        assertNotNull(host.speakHelp.value)
        assertEquals(SpeakSheetPage.Settings, host.speakSheet.current, "the help dialog must not close the sheet underneath it")
    }

    /** Same as [speakHelpRendersOverTheOpenSpeakSheet], for the Advanced page's help dialog. */
    @Test fun advancedSpeakHelpRendersOverTheOpenSpeakSheet() {
        val host = host()
        host.showSpeakSettings()

        host.showAdvancedSpeakHelp()

        assertNotNull(host.speakHelp.value)
        assertEquals(SpeakSheetPage.Settings, host.speakSheet.current, "the help dialog must not close the sheet underneath it")
    }

    private val shareInput = ShareVersesInput(
        verses = listOf(ShareVersesEntry(1, "In the beginning God created the heaven and the earth.")),
        startOffset = 0,
        endOffset = null,
        referenceAbbreviated = "Gen 1:1",
        referenceFull = "Genesis 1:1",
        versionAbbreviation = "KJV",
        notesText = null,
        advertiseText = "Shared from AndBible Bible Study (https://andbible.github.io)",
        hasRange = false,
    )

    /**
     * I3 (run 3 final review): the classic `ShareWidget` `AlertDialog`'s Share/Copy buttons
     * dismissed the dialog on click; `ReadingQuickSheet.Share`'s equivalents must too, unlike the
     * port's regression where the sheet stayed open after either action. Exercises
     * `shareVersesTextAndCloseSheet`/`copyVersesTextAndCloseSheet` directly (the same functions
     * `QuickSheetSlot`'s `is ReadingQuickSheet.Share ->` arm wires `onShare`/`onCopy` to) rather
     * than rendering the sheet -- an open `ModalBottomSheet` must never be idled in a Robolectric
     * test (this run's "Hang protection" rule).
     */
    @Test fun shareClosesTheQuickSheetAfterFiringTheShareChooser() {
        val host = host()
        host.showShareSheet(shareInput)
        assertIs<ReadingQuickSheet.Share>(host.quickSheet.value, "sanity: the share sheet is open")

        host.shareVersesTextAndCloseSheet("Gen 1:1 KJV In the beginning...")

        assertNull(host.quickSheet.value, "Share must close the sheet, like the old dialog's Share button did")
        val started = shadowOf(activity).nextStartedActivity
        assertNotNull(started, "the share chooser must still fire")
        assertEquals(android.content.Intent.ACTION_CHOOSER, started.action)
    }

    @Test fun copyClosesTheQuickSheetAfterCopyingToTheClipboard() {
        val host = host()
        host.showShareSheet(shareInput)
        assertIs<ReadingQuickSheet.Share>(host.quickSheet.value, "sanity: the share sheet is open")

        host.copyVersesTextAndCloseSheet("Gen 1:1 KJV In the beginning...", shareInput.referenceFull)

        assertNull(host.quickSheet.value, "Copy must close the sheet, like the old dialog's Copy button did")
        val clipboard = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("Gen 1:1 KJV In the beginning...", clipboard.primaryClip?.getItemAt(0)?.text)
    }
}

/**
 * [HostedStateRefresher] is the framework-free fan-out behind
 * `ComposeReadingViewHost.refreshHostedState()` — extracted precisely so the "classic refresh
 * point pushed into the Compose state" contract is unit-testable without booting a
 * [MainBibleActivity] or a Koin context (this repo's `:app` unit tests have no `ComposeTestRule`).
 * See the pre-A/B state-freshness spec §1 P3.
 */
// No @RunWith/@Config — matching `ComposeReadingViewGenerationTest` in this same file: the holder
// is framework-free (Compose snapshot state is pure Kotlin), so Robolectric is not needed.
class HostedStateRefresherTest {
    @Test fun refreshRefreshesTheToolbarWithoutBumpingTheGeneration() {
        val toolbar = RecordingToolbarStateService()
        val generation = ComposeReadingViewGeneration()

        HostedStateRefresher(toolbar, generation).refresh()

        assertEquals(1, toolbar.refreshCount)
        // A plain refresh must NOT recreate the pane subtree: that would re-run every
        // AndroidView factory and remount the WebViews on an ordinary toolbar update.
        assertEquals(0, generation.state.value)
    }

    @Test fun refreshWithRebuildCompositionAlsoBumpsTheGeneration() {
        val toolbar = RecordingToolbarStateService()
        val generation = ComposeReadingViewGeneration()

        HostedStateRefresher(toolbar, generation).refresh(rebuildComposition = true)

        assertEquals(1, toolbar.refreshCount)
        assertEquals(1, generation.state.value)
    }

    @Test fun repeatedRefreshesAccumulate() {
        val toolbar = RecordingToolbarStateService()
        val generation = ComposeReadingViewGeneration()
        val refresher = HostedStateRefresher(toolbar, generation)

        refresher.refresh()
        refresher.refresh(rebuildComposition = true)
        refresher.refresh()

        assertEquals(3, toolbar.refreshCount)
        assertEquals(1, generation.state.value)
    }
}

/**
 * The Compose speak transport bar's visibility condition, extracted as a pure predicate because
 * this repo's `:app` unit tests have no `ComposeTestRule` — inline in the slot lambda it would be
 * device-verifiable only. Classic hides the bar with the compound
 * `if (isFullScreen || !transportBarVisible)` in `MainBibleActivity.updateBottomBars()`, while
 * `SpeakTransportVisibilityChanged` carries only the raw `transportBarVisible` field (its getter's
 * fullscreen mask is not applied before posting) — so the fullscreen half has to be re-applied
 * here. See the pre-A/B state-freshness spec §1 P3.
 */
class SpeakBarVisibilityTest {
    @Test fun visibleOnlyWhenSpeakingAndNotFullscreen() {
        assertTrue(speakBarVisible(fullScreen = false, transportVisible = true))
        assertFalse(speakBarVisible(fullScreen = true, transportVisible = true))
        assertFalse(speakBarVisible(fullScreen = false, transportVisible = false))
        assertFalse(speakBarVisible(fullScreen = true, transportVisible = false))
    }
}

/**
 * A/B batch 3 F5b fix-round: [menuWindowIdFor] is the pure gate `mountComposeView`'s pane-overlay
 * and rail sites each call inline (`paneMenuWindowId = menuWindowIdFor(PaneMenuAnchor.Pane, ...)` /
 * `menuWindowId = menuWindowIdFor(PaneMenuAnchor.Rail, ...)`) — extracted so the actual "only one
 * surface ever sees a non-null window id" behaviour is unit-tested directly, rather than only
 * through [ComposeReadingViewHost.openPaneMenu]'s bookkeeping (which the original three anchor
 * tests covered, but which cannot detect a deleted or inverted gate at the two composition call
 * sites — the review finding this class fixes). Covers all four (surface × openAnchor)
 * combinations plus the null-`openWindowId` case.
 */
class MenuWindowIdForTest {
    @Test fun paneSurfaceSeesTheWindowIdOnlyWhenTheOpenAnchorIsPane() {
        assertEquals("w1", menuWindowIdFor(PaneMenuAnchor.Pane, PaneMenuAnchor.Pane, "w1"))
        assertEquals(null, menuWindowIdFor(PaneMenuAnchor.Pane, PaneMenuAnchor.Rail, "w1"))
    }

    @Test fun railSurfaceSeesTheWindowIdOnlyWhenTheOpenAnchorIsRail() {
        assertEquals("w1", menuWindowIdFor(PaneMenuAnchor.Rail, PaneMenuAnchor.Rail, "w1"))
        assertEquals(null, menuWindowIdFor(PaneMenuAnchor.Rail, PaneMenuAnchor.Pane, "w1"))
    }

    @Test fun aNullOpenWindowIdIsNullForEitherSurfaceRegardlessOfAnchor() {
        assertEquals(null, menuWindowIdFor(PaneMenuAnchor.Pane, PaneMenuAnchor.Pane, null))
        assertEquals(null, menuWindowIdFor(PaneMenuAnchor.Rail, PaneMenuAnchor.Rail, null))
    }
}

/**
 * Task 27 (platform-dialog removal, run 3) fix round 1: [chapterReadHistoryRow] is the load/format
 * step the review named as untested — `QuickSheetSlot`'s `is ReadingQuickSheet.ReadHistory ->`
 * branch's `LaunchedEffect` maps every loaded [ReadHistoryEntry] through it. Extracted the same way
 * [SpeakBarVisibilityTest]/[MenuWindowIdForTest] above extract their gates, so the mapping itself —
 * not just the pre-existing `ReadingProgressServiceImpl.readHistoryForChapter`/
 * `deleteReadHistoryEntries` pair `ReadingProgressServiceImplTest` already covers — is unit-tested.
 */
class ChapterReadHistoryRowTest {
    private val entry = ReadHistoryEntry(
        id = "h1", bookId = "GEN", chapter = 1, readAt = 0L, bookInitials = "KJV",
    )

    @Test fun rowIsDateTimeAndVersionWithNoChapterReference() {
        // Classic showForChapter's showChapterPerRow = false format: "$date $time" / version — no
        // "chapterRef · " prefix, unlike the book/day history screens' multi-chapter row format.
        val row = chapterReadHistoryRow(entry, date = "12 Aug 2026", time = "10:15", versionUnknownText = "Unknown")
        assertEquals(ReadHistoryRow(id = "h1", primary = "12 Aug 2026 10:15", secondary = "KJV"), row)
    }

    @Test fun emptyBookInitialsFallsBackToTheVersionUnknownText() {
        val row = chapterReadHistoryRow(
            entry.copy(bookInitials = ""), date = "12 Aug 2026", time = "10:15", versionUnknownText = "Unknown",
        )
        assertEquals("Unknown", row.secondary)
    }

    @Test fun idIsCarriedThroughUnchanged() {
        val row = chapterReadHistoryRow(
            entry.copy(id = "h2"), date = "12 Aug 2026", time = "10:15", versionUnknownText = "Unknown",
        )
        assertEquals("h2", row.id)
    }
}

/**
 * Task 27 (platform-dialog removal, run 3) fix round 2: [readHistoryApplyDeletes] is the
 * `onApplyDeletes` callback assembly review round 2 named as still untested after round 1 — round
 * 1's `AbReadHistorySheetContentTest` could only REPRODUCE the `ids.isNotEmpty()` guard in its own
 * test harness, not exercise the real one, and nothing verified the `cycle` `LaunchedEffect` loads
 * actually reaches `deleteReadHistoryEntries`. `QuickSheetSlot`'s `is ReadingQuickSheet.ReadHistory
 * ->` branch now calls this function VERBATIM (`onApplyDeletes = readHistoryApplyDeletes(activity
 * .lifecycleScope, readingProgressService, cycle)`) instead of inlining the lambda, so this test
 * exercises the actual production glue.
 *
 * [RecordingReadingProgressService] fakes [ReadingProgressService] the same one-member-per-line way
 * `:sharedCore`'s own `ReadingProgressControllerTest.Fake` does (mockk was removed from this repo;
 * no mocking framework is used) — typed to the portable interface, not
 * `ReadingProgressServiceImpl`, specifically so it needs no Room/Robolectric. `UnconfinedTestDispatcher`
 * runs `readHistoryApplyDeletes`'s `coroutineScope.launch { … }` eagerly, so the fake's call is
 * already recorded by the time the returned lambda's invocation returns.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReadHistoryApplyDeletesTest {
    private class RecordingReadingProgressService : ReadingProgressService {
        val deleteCalls = mutableListOf<Pair<List<String>, Int>>()

        override fun currentCycle() = 0
        override fun latestCycle() = 0
        override fun setActiveCycle(cycle: Int) {}
        override fun startNewCycle() = 0
        override suspend fun readingSummary(cycle: Int) = error("not needed")
        override suspend fun bookReadProgress(cycle: Int) = error("not needed")
        override suspend fun chapterReadCounts(bookId: String, cycle: Int) = error("not needed")
        override suspend fun readingCalendarSkeleton() = error("not needed")
        override suspend fun dailyReadCounts(cycle: Int) = error("not needed")
        override suspend fun readHistoryForBook(bookId: String, cycle: Int) = error("not needed")
        override suspend fun readHistoryForChapter(bookId: String, chapter: Int, cycle: Int) = error("not needed")
        override suspend fun readHistoryForDay(dayTimestamp: Long, cycle: Int) = error("not needed")
        override suspend fun deleteReadHistoryEntries(ids: List<String>, cycle: Int) {
            deleteCalls.add(ids to cycle)
        }
        override fun dayTitle(dayTimestamp: Long) = error("not needed")
        override fun formatEntryDate(readAt: Long) = error("not needed")
        override fun formatEntryTime(readAt: Long) = error("not needed")
        override fun bookShortName(bookId: String) = error("not needed")
        override fun bookLongName(bookId: String) = error("not needed")
        override suspend fun memorizeSummary() = error("not needed")
        override suspend fun bookMemorizationProgress() = error("not needed")
        override suspend fun chapterMemorizationProgress(bookId: String) = error("not needed")
        override suspend fun dailyMemorizationCounts() = error("not needed")
        override suspend fun memorizedPassages() = error("not needed")
        override suspend fun memorizeTargets() = error("not needed")
        override suspend fun unmarkMemorized(startOrdinal: Int, endOrdinal: Int) = error("not needed")
        override suspend fun removeMemorizationTarget(id: String) = error("not needed")
    }

    @Test fun nonEmptyIdsCallDeleteExactlyOnceWithTheIdsAndTheLoadedCycle() = runTest(UnconfinedTestDispatcher()) {
        val service = RecordingReadingProgressService()
        val apply = readHistoryApplyDeletes(this, service, cycle = 7)

        apply(listOf("h1", "h2"))

        assertEquals(listOf(listOf("h1", "h2") to 7), service.deleteCalls)
    }

    @Test fun emptyIdsNeverCallDelete() = runTest(UnconfinedTestDispatcher()) {
        val service = RecordingReadingProgressService()
        val apply = readHistoryApplyDeletes(this, service, cycle = 7)

        apply(emptyList())

        assertTrue(service.deleteCalls.isEmpty())
    }

    @Test fun eachAssemblyUsesTheCycleItWasGivenNotAStaleOne() = runTest(UnconfinedTestDispatcher()) {
        // Two sheet openings (e.g. a cycle rollover between them) must each apply against THEIR
        // OWN loaded cycle -- this is what "cycle threading" means concretely, and is exactly what
        // the review named as unverified.
        val service = RecordingReadingProgressService()

        readHistoryApplyDeletes(this, service, cycle = 3)(listOf("a"))
        readHistoryApplyDeletes(this, service, cycle = 9)(listOf("b"))

        assertEquals(listOf(listOf("a") to 3, listOf("b") to 9), service.deleteCalls)
    }
}
