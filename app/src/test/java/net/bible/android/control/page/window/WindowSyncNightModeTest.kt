package net.bible.android.control.page.window

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.service.common.CommonUtils
import net.bible.sharedlogic.platform.NightModeState
import net.bible.test.DatabaseResetter
import net.bible.test.KoinFactorySnapshot
import net.bible.test.testWindowRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** WindowSync reads the night mode through the [NightModeState] port, not the Android ScreenSettings. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WindowSyncNightModeTest {
    private class FakeNightMode(override var nightMode: Boolean) : NightModeState

    private val lastForceSyncAllField = WindowSync::class.java.getDeclaredField("lastForceSyncAll").apply { isAccessible = true }
    private val immediateSync = WindowSync::class.java.getDeclaredMethod("immediateSynchronizeWindows", Window::class.java)
        .apply { isAccessible = true }

    @Before
    fun setUp() {
        CommonUtils.settings.setBoolean("first-time", false)
    }

    @After
    fun tearDown() {
        DatabaseResetter.resetDatabase()
    }

    /**
     * Regression (desktop sync loop): night mode on while no reloadAllWindows ran since start.
     * Every sync used to force a resync again (lastSyncWasInNightMode was only stored by
     * reloadAllWindows), so each reload's verse report re-triggered a sync and the windows reloaded
     * the same chapter forever. The flip must force exactly one resync.
     */
    @Test
    fun nightModeStaysOnForcesResyncOnlyOnce() {
        val koin = GlobalContext.get()
        val snapshot = KoinFactorySnapshot.of<NightModeState>(koin)
        val fake = FakeNightMode(true)
        try {
            koin.loadModules(listOf(module { single<NightModeState> { fake } }), allowOverride = true)
            val repository = testWindowRepository()
            repository.initializeAtEdge()
            val sync = WindowSync(repository)
            val window = repository.activeWindow

            immediateSync.invoke(sync, window) // first sync sees night mode differ from the initial false
            val afterFirst = lastForceSyncAllField.getLong(sync)
            lastForceSyncAllField.setLong(sync, 0L)
            immediateSync.invoke(sync, window)
            assertEquals("night mode unchanged since the last sync: no new forced resync", 0L, lastForceSyncAllField.getLong(sync))
            assertNotEquals("the first sync after the flip did force one", 0L, afterFirst)
        } finally {
            snapshot.restore()
        }
    }

    @Test
    fun nightModeFlipForcesResync() {
        val koin = GlobalContext.get()
        val snapshot = KoinFactorySnapshot.of<NightModeState>(koin)
        val fake = FakeNightMode(false)
        try {
            koin.loadModules(listOf(module { single<NightModeState> { fake } }), allowOverride = true)
            val repository = testWindowRepository()
            repository.initializeAtEdge()
            val sync = WindowSync(repository)
            val window = repository.activeWindow

            sync.reloadAllWindows() // remembers night mode = false
            lastForceSyncAllField.setLong(sync, 0L)
            immediateSync.invoke(sync, window)
            assertEquals("no night mode change: no forced resync", 0L, lastForceSyncAllField.getLong(sync))

            fake.nightMode = true
            immediateSync.invoke(sync, window)
            assertNotEquals("night mode flipped: all windows must resync", 0L, lastForceSyncAllField.getLong(sync))
        } finally {
            snapshot.restore()
        }
    }
}
