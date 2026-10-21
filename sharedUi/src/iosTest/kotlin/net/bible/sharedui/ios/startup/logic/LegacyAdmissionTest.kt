package net.bible.sharedui.ios.startup

import net.bible.sharedcore.platform.CrashPreferences
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyAdmissionTest {
    private class MapPrefs(val booleans: MutableMap<String, Boolean> = mutableMapOf()) : DeviceLocalPrefs {
        override fun getString(key: String, default: String?): String? = default
        override fun getBoolean(key: String, default: Boolean): Boolean = booleans[key] ?: default
        override fun setString(key: String, value: String) = Unit
        override fun setBoolean(key: String, value: Boolean) { booleans[key] = value }
        override fun remove(key: String) { booleans.remove(key) }
    }

    @Test fun settledReadsTheFlag() {
        val prefs = MapPrefs()
        assertFalse(legacySettled(prefs), "an unset flag means not settled")
        prefs.setBoolean(LEGACY_SETTLED_KEY, true)
        assertTrue(legacySettled(prefs))
        prefs.setBoolean(LEGACY_SETTLED_KEY, false)
        assertFalse(legacySettled(prefs))
        assertEquals("legacy_migration_settled", LEGACY_SETTLED_KEY)
    }

    @Test fun backgroundAdmissionRefusedWhileUnsettled() {
        assertFalse(backgroundAdmissionAllowed(protectedData = true, crashRecoveryPending = false, settled = false))
        assertTrue(backgroundAdmissionAllowed(protectedData = true, crashRecoveryPending = false, settled = true))
        assertFalse(backgroundAdmissionAllowed(protectedData = false, crashRecoveryPending = false, settled = true))
        assertFalse(backgroundAdmissionAllowed(protectedData = true, crashRecoveryPending = true, settled = true))
    }

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
