package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.database.IdType
import net.bible.android.view.activity.page.screen.RestoreButtonsVisibilityChanged
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [WindowCommandsImpl] against a REAL [WindowControl]/[WindowRepository]/[Window]
 * graph (Robolectric + [TestBibleApplication], same style as [WindowControlTest] /
 * [WindowRepositoryTest]) rather than mocking the collaborators: `WindowControl.activeWindow`
 * and `WindowRepository.getWindow`/`windowSizesChanged` are plain (non-`open`) Kotlin members,
 * so Mockito's default (non-inline) mock maker cannot stub or verify calls on them — it would
 * either silently fall through to the real (uninitialized) implementation or fail to record the
 * interaction. Building the real objects sidesteps that entirely and verifies genuine behavior.
 *
 * `workspaceSettings.autoPin = true` mirrors [WindowControlTest]'s setup: [Window.weight] is only
 * per-window when `isPinMode` is true; unpinned windows all share the single
 * `WindowRepository.unPinnedWeight`, which would make the two-window `commitWeights` assertions
 * spuriously collide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowCommandsImplTest {

    private lateinit var windowControl: WindowControl
    private lateinit var repo: WindowRepository
    private lateinit var commands: WindowCommandsImpl

    @Before
    fun setUp() {
        repo = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl = WindowControl()
        windowControl.windowRepository = repo
        repo.initialize()
        repo.workspaceSettings.autoPin = true
        commands = WindowCommandsImpl(windowControl)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase(repo.scope)
    }

    private fun awaitNumberOfWindowsChangedEvent(block: () -> Unit): Boolean {
        var notified = false
        val owner = Any()
        ABEventBus.register(owner) { on<NumberOfWindowsChangedEvent> { notified = true } }
        try {
            block()
        } finally {
            ABEventBus.unregister(owner)
        }
        return notified
    }

    private fun awaitRestoreButtonsVisibilityChanged(block: () -> Unit): Boolean {
        var notified = false
        val owner = Any()
        ABEventBus.register(owner) { on<RestoreButtonsVisibilityChanged> { notified = true } }
        try {
            block()
        } finally {
            ABEventBus.unregister(owner)
        }
        return notified
    }

    @Test fun setActiveResolvesIdAndSetsActiveWindow() {
        val newWindow = repo.addNewWindow()

        commands.setActive(newWindow.id.toString())

        assertEquals(newWindow, windowControl.activeWindow)
        assertEquals(newWindow, repo.activeWindow)
    }

    @Test fun setActiveWithUnknownIdIsNoOp() {
        val originalActive = windowControl.activeWindow

        commands.setActive(IdType().toString())

        assertEquals(originalActive, windowControl.activeWindow)
    }

    @Test fun commitWeightsSetsBothWeightsAndNotifies() {
        val w1 = repo.activeWindow
        val w2 = repo.addNewWindow()
        // sanity: both visible windows -> windowSizesChanged() actually runs orientationChange()
        assertTrue(repo.isMultiWindow)

        val notified = awaitNumberOfWindowsChangedEvent {
            commands.commitWeights(w1.id.toString(), 1.5f, w2.id.toString(), 0.5f)
        }

        assertEquals(1.5f, w1.weight)
        assertEquals(0.5f, w2.weight)
        assertTrue(notified, "windowSizesChanged() should have notified window listeners")
    }

    /**
     * Regression: with autoPin off (the default) a links window is always unpinned, and so is any
     * regular window copied from one. Both used to read and write the single shared
     * `unPinnedWeight`, so committing a separator drag between them wrote that one slot twice: both
     * panes ended up with `weight2`, the split snapped back to 50/50 on release, and the shrunken
     * weight sum made the next drag outrun the finger.
     */
    @Test fun commitWeightsKeepsDistinctWeightsForUnpinnedWindowAndItsLinksWindow() {
        repo.workspaceSettings.autoPin = false
        val regular = repo.activeWindow
        regular.isPinMode = false
        val links = regular.targetLinksWindow
        links.windowState = WindowLayout.WindowState.VISIBLE
        // sanity: the scenario really is two unpinned panes side by side
        assertFalse(regular.isPinMode)
        assertFalse(links.isPinMode)
        assertTrue(repo.isMultiWindow)

        commands.commitWeights(regular.id.toString(), 1.4f, links.id.toString(), 0.6f)

        assertEquals(1.4f, regular.weight)
        assertEquals(0.6f, links.weight)
    }

    /** A new links window takes the size its unpinned source shows, not the source's stale raw weight. */
    @Test fun newLinksWindowTakesTheUnpinnedSourcesShownWeight() {
        repo.workspaceSettings.autoPin = false
        val regular = repo.activeWindow
        regular.weight = 0.3f // pinned (initial window): lands in its own raw weight
        regular.isPinMode = false
        regular.weight = 1.2f // unpinned: lands in the shared slot, raw weight stays 0.3

        val links = regular.targetLinksWindow

        assertTrue(links.isLinksWindow)
        assertEquals(1.2f, links.weight)
    }

    /**
     * The shared unpinned slot is deliberate for regular windows: only one of them is visible at a
     * time (restoring one minimises the others), and the one shown takes over the size the slot had.
     */
    @Test fun unpinnedRegularWindowsStillShareOneWeight() {
        repo.workspaceSettings.autoPin = false
        val w1 = repo.activeWindow
        w1.isPinMode = false
        val w2 = repo.addNewWindow(w1)
        w2.isPinMode = false

        w1.weight = 0.7f

        assertEquals(0.7f, w2.weight)
    }

    @Test fun commitWeightsNoOpWhenFirstWindowIdUnknown() {
        val w2 = repo.addNewWindow()
        val originalWeight2 = w2.weight

        val notified = awaitNumberOfWindowsChangedEvent {
            commands.commitWeights(IdType().toString(), 1.5f, w2.id.toString(), 0.5f)
        }

        assertEquals(originalWeight2, w2.weight)
        assertFalse(notified, "no windows resolved -> windowSizesChanged() must not run")
    }

    @Test fun commitWeightsNoOpWhenSecondWindowIdUnknown() {
        val w1 = repo.activeWindow
        repo.addNewWindow() // second visible window, so isMultiWindow would be true if reached
        val originalWeight1 = w1.weight

        val notified = awaitNumberOfWindowsChangedEvent {
            commands.commitWeights(w1.id.toString(), 1.5f, IdType().toString(), 0.5f)
        }

        assertEquals(originalWeight1, w1.weight)
        assertFalse(notified, "second window unresolved -> neither weight nor windowSizesChanged() should fire")
    }

    @Test fun addNewWindowResolvesSourceAndAddsWindow() {
        val src = repo.activeWindow
        val before = repo.windowList.size

        commands.addNewWindow(src.id.toString())

        assertEquals(before + 1, repo.windowList.size)
    }

    @Test fun addNewWindowWithUnknownIdIsNoOp() {
        val before = repo.windowList.size

        commands.addNewWindow(IdType().toString())

        assertEquals(before, repo.windowList.size)
    }

    @Test fun minimiseThenRestoreRoundTrips() {
        val w = repo.addNewWindow() // 2 windows so minimise is allowed

        commands.minimise(w.id.toString())
        assertFalse(w.isVisible)

        commands.restore(w.id.toString())
        assertTrue(w.isVisible)
    }

    @Test fun closeResolvesIdAndClosesWindow() {
        val w = repo.addNewWindow() // 2 windows so close is allowed
        val before = repo.windowList.size

        commands.close(w.id.toString())

        assertEquals(before - 1, repo.windowList.size)
    }

    @Test fun closeWithUnknownIdIsNoOp() {
        repo.addNewWindow()
        val before = repo.windowList.size

        commands.close(IdType().toString())

        assertEquals(before, repo.windowList.size)
    }

    @Test fun maximiseSetsMaximizedThenUnMaximiseClears() {
        val w = repo.activeWindow

        commands.maximise(w.id.toString())
        assertEquals(w.id, repo.maximizedWindowId)

        commands.unMaximise()
        assertNull(repo.maximizedWindowId)
    }

    @Test fun setPinTogglesPinMode() {
        // Window.isPinMode's getter always reports true while workspaceSettings.autoPin is on
        // (irrespective of the backing field), so disable it here to observe the field toggle
        // setPinMode() actually performs.
        repo.workspaceSettings.autoPin = false
        val w = repo.activeWindow
        val before = w.isPinMode

        commands.setPin(w.id.toString(), !before)

        assertEquals(!before, w.isPinMode)
    }

    @Test fun moveChangesWindowPosition() {
        // All three windows share one pinned/unpinned group (isPinMode reads true for all of
        // them, autoPin=true), so moveWindowToPosition reorders them within a single bucket and
        // repo.windowList directly reflects that order. Chain sourceWindow explicitly (rather
        // than relying on the default `sourceWindow = activeWindow`, which stays w1 throughout
        // and would insert every new window right after it) so the initial order is [w1, w2, w3].
        val w1 = repo.activeWindow
        val w2 = repo.addNewWindow(w1)
        val w3 = repo.addNewWindow(w2)
        assertEquals(listOf(w1, w2, w3), repo.windowList) // sanity: w1 currently first

        commands.move(w3.id.toString(), 0)

        assertEquals(listOf(w3, w1, w2), repo.windowList)
    }

    @Test fun setSynchronisedTogglesSync() {
        val w = repo.addNewWindow()
        val before = w.isSynchronised

        commands.setSynchronised(w.id.toString(), !before)

        assertEquals(!before, w.isSynchronised)
    }

    @Test fun changeSyncGroupSetsGroupAndSynchronises() {
        val w = repo.addNewWindow()

        commands.changeSyncGroup(w.id.toString(), 3)

        assertTrue(w.isSynchronised)
        assertEquals(3, w.syncGroup)
    }

    @Test fun focusNextMovesToNextVisibleWindow() {
        val w1 = repo.activeWindow
        val w2 = repo.addNewWindow()
        assertEquals(w1, repo.activeWindow)

        commands.focusNext()

        assertEquals(w2, repo.activeWindow)
    }

    @Test fun focusPreviousMovesToPreviousVisibleWindow() {
        // Chain sourceWindow explicitly so windowList order is [w1, w2, w3] (see the
        // moveChangesWindowPosition comment) and w2 is unambiguously "between" w1 and w3.
        val w1 = repo.activeWindow
        val w2 = repo.addNewWindow(w1)
        val w3 = repo.addNewWindow(w2)
        windowControl.activeWindow = w2 // middle window, so prev/next are unambiguous

        commands.focusPrevious()

        assertEquals(w1, repo.activeWindow)
        assertNotEquals(w3, repo.activeWindow)
    }

    @Test fun setRestoreButtonsVisibleFlipsFlagAndNotifies() {
        val start = repo.workspaceSettings.restoreButtonsVisible

        val notified = awaitRestoreButtonsVisibilityChanged {
            commands.setRestoreButtonsVisible(!start)
        }

        assertEquals(!start, repo.workspaceSettings.restoreButtonsVisible)
        assertTrue(notified, "setRestoreButtonsVisible should post RestoreButtonsVisibilityChanged")
    }

    @Test fun unknownIdCommandsAreNoOps() {
        // must not throw
        commands.minimise(IdType().toString())
        commands.close(IdType().toString())
        commands.restore(IdType().toString())
        commands.maximise(IdType().toString())
        commands.setPin(IdType().toString(), true)
        commands.move(IdType().toString(), 0)
        commands.setSynchronised(IdType().toString(), true)
        commands.changeSyncGroup(IdType().toString(), 1)
    }
}
