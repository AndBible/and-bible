package net.bible.sharedui.ios.startup

import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.IosDeviceSettings
import okio.Path.Companion.toPath
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosCrashTrackerTest {
    private val suite = "org.andbible.crash.test.${NSUUID().UUIDString}"
    private val defaults = NSUserDefaults(suiteName = suite)
    private val settings = IosDeviceSettings(defaults)
    private val now = 1_800_000_000_123L
    private fun tracker() = IosCrashTracker(settings) { now }
    @AfterTest fun clear() { defaults.removePersistentDomainForName(suite) }

    @Test fun recordUncaughtWritesExceptionAndStackToInternalLog() {
        val files = okio.FileSystem.SYSTEM
        val support = platform.Foundation.NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSApplicationSupportDirectory, platform.Foundation.NSUserDomainMask, true,
        ).first().toString()
        val expectedPath = "$support/AndBible/log/last_crash.txt"
        assertEquals(expectedPath, IosCrashTracker.crashFile)
        val file = expectedPath.toPath()
        val missingDirectories = generateSequence(file.parent) { it.parent }
            .takeWhile { !files.exists(it) }.toList()
        val previous = if (files.exists(file)) files.read(file) { readByteArray() } else null
        try {
            // A stale file must not satisfy the test if recordUncaught stops writing.
            files.delete(file, mustExist = false)
            val exception = IllegalStateException("task4-known-crash-${NSUUID().UUIDString}")
            tracker().recordUncaught(exception)
            val stack = files.read(file) { readUtf8() }
            assertTrue(stack.contains("IllegalStateException"))
            assertTrue(stack.contains(exception.message!!))
            assertEquals(exception.stackTraceToString(), stack)
            assertTrue(stack.lineSequence().count() > 1, "crash capture must include stack frames, not just the message")
        } finally {
            if (previous != null) files.write(file) { write(previous) }
            else files.delete(file, mustExist = false)
            missingDirectories.forEach { directory ->
                if (files.listOrNull(directory)?.isEmpty() == true) files.delete(directory)
            }
        }
    }

    @Test fun dirtyStartCountsACrash() {
        settings.putBoolean(CrashPreferences.RUNNING, true)
        tracker().onLaunch()
        assertEquals(1, settings.getInt(CrashPreferences.COUNT, 0))
        assertEquals(now, settings.getLong(CrashPreferences.TIME, 0))
    }
    @Test fun cleanExitDoesNotCount() {
        tracker().apply { onLaunch(); onCleanExit() }
        tracker().onLaunch()
        assertEquals(0, settings.getInt(CrashPreferences.COUNT, 0))
    }
    @Test fun markerClearedBeforeRecoveryScreen() {
        repeat(3) { tracker().onLaunch() }
        assertTrue(tracker().shouldOfferRecovery())
        assertFalse(settings.getBoolean(CrashPreferences.RUNNING, true))
        assertEquals(0, settings.getInt(CrashPreferences.COUNT, -1))
    }
    @Test fun repeatedControllerAndForegroundDoNotCountACrash() {
        val tracker = tracker()
        tracker.onLaunch()
        tracker.onLaunch()
        tracker.onCleanExit()
        tracker.onForeground()
        assertTrue(settings.getBoolean(CrashPreferences.RUNNING, false))
        assertEquals(0, settings.getInt(CrashPreferences.COUNT, 0))
    }
    @Test fun recoveryStaysCleanAcrossBackgroundUntilContinue() {
        settings.putInt(CrashPreferences.COUNT, 2)
        settings.putLong(CrashPreferences.TIME, now)
        val tracker = tracker()
        tracker.onLaunch()
        assertTrue(tracker.shouldOfferRecovery())
        tracker.onCleanExit()
        tracker.onForeground()
        assertFalse(settings.getBoolean(CrashPreferences.RUNNING, true))
        tracker.continueStartup()
        assertTrue(settings.getBoolean(CrashPreferences.RUNNING, false))
    }
}
