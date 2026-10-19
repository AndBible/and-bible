package net.bible.sharedcore.platform

import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

class IosDeviceSettingsObservationTest {
    @Test fun observesWritesAcrossInstancesAndUnsubscribes() {
        val suite = "org.andbible.test.${NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suite)
        val values = mutableListOf<Boolean>()
        val cancel = IosDeviceSettings(defaults).observeBoolean("discrete_mode", false) { values += it }
        try {
            val writer = IosDeviceSettings(defaults)
            writer.putBoolean("other", true)
            writer.putBoolean("discrete_mode", true)
            writer.putBoolean("discrete_mode", true)
            writer.putBoolean("discrete_mode", false)
            assertEquals(listOf(false, true, false), values)
            cancel()
            writer.putBoolean("discrete_mode", true)
            assertEquals(listOf(false, true, false), values)
        } finally {
            cancel()
            defaults.removePersistentDomainForName(suite)
        }
    }
}
