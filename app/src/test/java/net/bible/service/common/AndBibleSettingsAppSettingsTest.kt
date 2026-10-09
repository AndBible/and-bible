package net.bible.service.common

import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.service.db.DatabaseContainer
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.getEnumSet
import net.bible.sharedcore.platform.setEnumSet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Review Focus #3: the "database not ready" default must survive the [AppSettings] interface.
 * Under the stock test application `DatabaseContainer.instance` never throws `DataBaseNotReady`
 * (`isRunningTests` is true), so this test's own application turns `isRunningTests` off while the
 * not-ready read runs, making the real throw live.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = AndBibleSettingsAppSettingsTest.ToggleApplication::class, sdk = [TEST_SDK])
class AndBibleSettingsAppSettingsTest {
    class ToggleApplication : BibleApplication() {
        override val isRunningTests: Boolean get() = runningTests
    }

    @After fun tearDown() {
        runningTests = true
        DatabaseContainer.reset()
    }

    @Test fun readsThroughTheInterfaceAnswerDefaultsBeforeTheDatabaseExists() {
        DatabaseContainer.reset()
        DatabaseContainer.ready = false
        runningTests = false
        val s: AppSettings = CommonUtils.settings
        assertEquals("dflt", s.getString("display_color_mode", "dflt"))
        assertEquals(7L, s.getLong("anything", 7L))
    }

    @Test fun enumSetWrittenByTheOldApiReadsBackThroughTheInterface() {
        runningTests = true
        DatabaseContainer.ready = true
        val s: AppSettings = CommonUtils.settings
        s.setEnumSet("l1a_enum_probe", setOf(Thread.State.NEW))
        assertEquals(setOf(Thread.State.NEW), s.getEnumSet<Thread.State>("l1a_enum_probe"))
    }

    companion object {
        @Volatile private var runningTests = true
    }
}
