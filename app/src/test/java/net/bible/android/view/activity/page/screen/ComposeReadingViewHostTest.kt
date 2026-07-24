package net.bible.android.view.activity.page.screen

import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.DrawerMenuStateBuilder
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.WindowPaneMenuStateBuilder
import net.bible.service.common.CommonUtils
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
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
            onOpenPaneMenu = { id -> openedPaneMenuFor = id },
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
     * `copySettingsToWindow` must resolve the ACTUAL target window rather than defaulting
     * silently (Task-5 brief) — with no other visible window to copy to (the same guard
     * `WindowPaneMenuStateBuilder` uses to hide the row in the first place,
     * `copySettingsToWindowOnlyAppearsWhenAnotherVisibleWindowExists`), the picker must safely
     * no-op rather than crash on an empty target list.
     */
    @Test fun copySettingsToWindowNoOpsSafelyWithNoOtherVisibleWindow() {
        val w1 = windowRepository.activeWindow

        val stayOpen = activity.handleWindowPaneMenuItem(w1.id.toString(), WindowPaneMenuStateBuilder.ID_COPY_SETTINGS_TO_WINDOW)

        assertFalse(stayOpen)
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
}
