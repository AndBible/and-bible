package net.bible.sharedui.desktop.misc

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.bible.sharedcore.platform.desktop.DesktopDeviceSettings

class DesktopCrashTrackerTest {
    @Test
    fun handlerWritesCrashAndRestoresPreviousHandler() {
        val dir = Files.createTempDirectory("crash-handler")
        val tracker = DesktopCrashTracker(DesktopDeviceSettings(dir.resolve("settings")), dir)
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var delegated = false
        val prior = Thread.UncaughtExceptionHandler { _, _ -> delegated = true }
        Thread.setDefaultUncaughtExceptionHandler(prior)
        try {
            tracker.install()
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), IllegalStateException("known crash"))
            assertTrue(delegated)
            assertTrue(Files.readString(dir.resolve("last_crash.txt")).contains("known crash"))
            tracker.close()
            assertSame(prior, Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            tracker.close()
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun twoConsecutiveStartupCrashesOfferRecovery() {
        val dir = Files.createTempDirectory("crash-tracker")
        val settings = DesktopDeviceSettings(dir.resolve("settings"))
        DesktopCrashTracker(settings, dir, { 1000L }).onLaunch()
        DesktopCrashTracker(settings, dir, { 1000L }).onLaunch()
        val third = DesktopCrashTracker(settings, dir, { 1000L })
        third.onLaunch()
        assertTrue(third.shouldOfferRecovery())
        assertTrue(third.shouldOfferRecovery())
    }

    @Test
    fun cleanRunResetsLatch() {
        val dir = Files.createTempDirectory("crash-tracker-clean")
        val settings = DesktopDeviceSettings(dir.resolve("settings"))
        DesktopCrashTracker(settings, dir, { 1000L }).onLaunch()
        val second = DesktopCrashTracker(settings, dir, { 1000L })
        second.onLaunch()
        second.onCleanExit()
        val third = DesktopCrashTracker(settings, dir, { 1000L })
        third.onLaunch()
        assertFalse(third.shouldOfferRecovery())
    }
}
