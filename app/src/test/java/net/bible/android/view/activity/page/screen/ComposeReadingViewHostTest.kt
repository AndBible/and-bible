package net.bible.android.view.activity.page.screen

import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ApplicationProvider
import net.bible.android.control.page.window.WindowStateServiceImpl
import net.bible.sharedcore.window.RailEntry
import net.bible.sharedcore.window.ReadingViewController
import net.bible.sharedcore.window.WindowCommands
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
