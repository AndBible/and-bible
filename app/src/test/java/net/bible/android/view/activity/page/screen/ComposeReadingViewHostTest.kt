package net.bible.android.view.activity.page.screen

import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.CurrentBibleVerseChanged
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.control.page.toolbar.ToolbarStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.android.view.activity.page.bibleViewBackgroundColorFor
import net.bible.android.view.util.widget.composeHostMounted
import net.bible.service.common.CommonUtils
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.ToolbarStateService
import net.bible.sharedcore.window.RailEntry
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowPaneMenuItem
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
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
     */
    @Test fun drawerIconResIdsCoverEveryBuilderIconKey() {
        for (iconName in DrawerMenuStateBuilder.entryIconNames + "ic_logo") {
            val resId = ComposeReadingViewHost.drawerIconResIds[iconName]
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
 * Batch 12b follow-on Plan B Task 5: exercises [MainBibleActivity.handleWindowPaneMenuItem]
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
    private lateinit var activity: MainBibleActivity

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
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

        val stayOpen = activity.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)

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

        val stayOpen = activity.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_MINIMISE)

        assertFalse(stayOpen)
        assertFalse(w1.isVisible, "windowMinimise via the controller seam must minimise the window")
    }

    /** `pinMode` is a checkable toggle (mirrors classic's `isBoolean` `CommandPreference`): stays open. */
    @Test fun pinModeTogglesAndStaysOpen() {
        windowRepository.workspaceSettings.autoPin = false
        val w1 = windowRepository.activeWindow
        w1.isPinMode = false

        val stayOpen = activity.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_PIN_MODE)

        assertTrue(stayOpen, "a checkable toggle must tell the host to stay open (and rebuild)")
        assertTrue(w1.isPinMode)
    }

    /** `windowClose` acts through the seam/`WindowControl` and does not stay open. */
    @Test fun windowCloseActsAndCloses() {
        val w1 = windowRepository.activeWindow
        val w2 = windowRepository.addNewWindow(w1)
        assertTrue(windowControl.isWindowRemovable(w2))

        val stayOpen = activity.handleWindowPaneMenuItem(w2.id.toString(), WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE)

        assertFalse(stayOpen)
        assertNull(windowRepository.getWindow(w2.id), "windowClose must actually remove the window")
    }

    /**
     * A BRIDGED id (`"allTextOptions"`) invokes the classic native launch — `TextDisplaySettingsActivity`
     * via `startActivityForResult` (`SplitBibleArea.kt:978-986`'s window-level counterpart) — rather
     * than acting through the command seam, and reports `false` (closes the menu).
     */
    @Test fun allTextOptionsLaunchesTextDisplaySettingsActivity() {
        val w1 = windowRepository.activeWindow

        val stayOpen = activity.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_ALL_TEXT_OPTIONS)

        assertFalse(stayOpen)
        val started = shadowOf(activity).nextStartedActivityForResult
        assertEquals(MainBibleActivity.TEXT_DISPLAY_SETTINGS_CHANGED, started?.requestCode)
        assertEquals(
            "net.bible.android.view.activity.settings.TextDisplaySettingsActivity",
            started?.intent?.component?.className,
        )
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
            val stayOpen = activity.handleWindowPaneMenuItem(
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
                activity.handleWindowPaneMenuItem(
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
        assertFalse(activity.handleWindowPaneMenuItem(IdType().toString(), WindowPaneMenuStateBuilder.ID_WINDOW_CLOSE))
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
}

/** Recording fake — no mocking framework in this repo (see the file header note). */
private class RecordingToolbarStateService : ToolbarStateService {
    var refreshCount = 0
    private val _toolbar = MutableStateFlow(ToolbarState.EMPTY)
    override val toolbar: StateFlow<ToolbarState> = _toolbar.asStateFlow()
    override fun refresh() { refreshCount++ }
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
 * A/B round 6: [bottomInsetReserved] decides whether `ReadingViewScreen` must reserve the bottom
 * navigation-bar inset — true iff at least one of the agent-log/speak-bar slots is on screen. A
 * pure function, mirroring [SpeakBarVisibilityTest] above, so the decision is unit-testable without
 * a `ComposeTestRule`.
 */
class BottomInsetReservedTest {
    @Test
    fun bottomInsetIsReservedWheneverEitherBottomBarIsVisible() {
        assertFalse(bottomInsetReserved(agentLogVisible = false, speakBarVisible = false))
        assertTrue(bottomInsetReserved(agentLogVisible = true, speakBarVisible = false))
        assertTrue(bottomInsetReserved(agentLogVisible = false, speakBarVisible = true))
        assertTrue(bottomInsetReserved(agentLogVisible = true, speakBarVisible = true))
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
 * [classicBottomChromeAllowed] is the pure decision behind hiding `AgentLogWidget`/classic
 * `speakTransport` on the Compose path (pre-A/B state-freshness spec §1 P3, Task 5) — extracted so
 * it's unit-testable at its `AgentLogWidget` call site too, a real `View` this repo's `:app` unit
 * tests never Robolectric-boot.
 *
 * Robolectric-backed (finding I1, pre-A/B state-freshness FINAL review fix wave): the blocker was
 * that both real call sites originally fed this predicate the live `use_compose_ui` flag rather
 * than whether a Compose host is actually MOUNTED. Those disagree for a whole activity lifetime
 * after `preferenceSettingsChanged()` returns from Settings without recreating `MainBibleActivity`
 * (only a locale change / `SettingsComposeActivity`'s `RECREATE_ON_CHANGE_KEYS` recreate anything).
 * The tests below build a REAL [MainBibleActivity] (the same minimal `.get()`-not-`.create()`
 * footprint [MainBibleActivityHandleWindowPaneMenuItemTest] above uses) and deliberately set
 * `use_compose_ui` OUT OF SYNC with `composeReadingViewHost` to prove each fixed call site tracks
 * the mounted host, not the flag, in both directions.
 *
 * Covered: the exact boolean expression each call site now feeds [classicBottomChromeAllowed] --
 * `MainBibleActivity.updateBottomBars()`'s `composeReadingViewHost != null`, and `AgentLogWidget`'s
 * `composeHostMounted(context)` (including its non-`MainBibleActivity`-context fallback) -- against
 * the real field/context each site reads.
 *
 * NOT covered here: `updateBottomBars()`/`AgentLogWidget.show()`/`hide()` themselves are not driven
 * end-to-end (that needs a full `.create()` boot with an inflated `binding` and a real
 * `AgentLogWidget` View, which no test in this file does -- see the class kdoc above about
 * `AgentLogWidget` never being Robolectric-booted as a View). Device A/B remains the interim DoD
 * for the full round-trip, same as the rest of the reading view.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ClassicBottomChromeAllowedTest {
    private lateinit var windowRepository: WindowRepository
    private lateinit var activity: MainBibleActivity

    @Before
    fun setUp() {
        val windowControl = CommonUtils.windowControl
        windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository = windowRepository
        windowRepository.initialize()

        activity = Robolectric.buildActivity(MainBibleActivity::class.java).get()
        activity.windowRepository = windowRepository
    }

    @After
    fun tearDown() {
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        DatabaseResetter.resetDatabase(windowRepository.scope)
    }

    @Test fun allowedOnlyWhenComposeIsNotHosting() {
        assertTrue(classicBottomChromeAllowed(composeHosted = false))
        assertFalse(classicBottomChromeAllowed(composeHosted = true))
    }

    /** `MainBibleActivity.updateBottomBars()`'s call site: `composeReadingViewHost != null`. */
    @Test fun mainBibleActivitySiteTracksTheMountedHostNotTheLiveFlag() {
        assertNull(activity.composeReadingViewHost, "sanity: no host mounted yet")

        // Flag ON, host still null (the exact "returned from Settings, flag just flipped ON,
        // activity not recreated" moment) -- classic chrome must still be ALLOWED, or the app
        // would show neither bar (finding I1).
        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertTrue(
            classicBottomChromeAllowed(composeHosted = activity.composeReadingViewHost != null),
            "flag ON with no host mounted must still allow classic chrome",
        )

        // Host mounted, flag OFF (the symmetric mismatch) -- classic chrome must be DISALLOWED,
        // since the mounted Compose host now owns this chrome regardless of what the flag reads.
        activity.composeReadingViewHost = ComposeReadingViewHost(activity)
        CommonUtils.settings.setBoolean("use_compose_ui", false)
        assertFalse(
            classicBottomChromeAllowed(composeHosted = activity.composeReadingViewHost != null),
            "a mounted host must suppress classic chrome even when the flag reads OFF",
        )
    }

    /** `AgentLogWidget`'s call site: `composeHostMounted(context)`. */
    @Test fun agentLogWidgetSiteTracksTheMountedHostNotTheLiveFlag() {
        assertFalse(composeHostMounted(activity), "sanity: no host mounted yet")

        CommonUtils.settings.setBoolean("use_compose_ui", true)
        assertTrue(
            classicBottomChromeAllowed(composeHosted = composeHostMounted(activity)),
            "flag ON with no host mounted must still allow the classic widget",
        )

        activity.composeReadingViewHost = ComposeReadingViewHost(activity)
        assertFalse(
            classicBottomChromeAllowed(composeHosted = composeHostMounted(activity)),
            "a mounted host must suppress the classic widget",
        )
    }

    /**
     * `AgentLogWidget` is only ever inflated inside `MainBibleActivity`, but [composeHostMounted]
     * must fail safe -- "no compose host" -- for any other [android.content.Context], preserving
     * classic behaviour rather than crashing or defaulting the wrong way.
     */
    @Test fun composeHostMountedDefaultsToFalseForANonMainBibleActivityContext() {
        val otherContext = ApplicationProvider.getApplicationContext<TestBibleApplication>()
        assertFalse(composeHostMounted(otherContext))
    }
}
