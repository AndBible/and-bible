package net.bible.android.control.page.window

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.on
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.database.IdType
import net.bible.test.DatabaseResetter
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
