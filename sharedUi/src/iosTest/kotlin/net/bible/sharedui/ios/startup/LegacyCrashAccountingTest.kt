package net.bible.sharedui.ios.startup

import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.IosDeviceSettings
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyCrashAccountingTest {
    @Test fun crashLaunchNotRecordedWhileUnsettled() {
        val suite = "org.andbible.legacyadmission.test.${NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suite)
        try {
            val settings = IosDeviceSettings(defaults)
            settings.putInt(CrashPreferences.COUNT, 1)
            settings.putBoolean(CrashPreferences.RUNNING, false)
            repeat(2) {
                // A fresh tracker per simulated launch, as after a process restart.
                val tracker = IosCrashTracker(settings) { 1_800_000_000_000L }
                recordLaunchIfSettled(settled = false, tracker)
                assertEquals(1, settings.getInt(CrashPreferences.COUNT, -1), "count untouched while unsettled")
                assertFalse(settings.getBoolean(CrashPreferences.RUNNING, true), "running untouched while unsettled")
            }
            // Once settled the launch is recorded: RUNNING becomes true.
            recordLaunchIfSettled(settled = true, IosCrashTracker(settings) { 1_800_000_000_000L })
            assertTrue(settings.getBoolean(CrashPreferences.RUNNING, false))
        } finally {
            defaults.removePersistentDomainForName(suite)
        }
    }
}
