package net.bible.sharedui.ios.discrete

import net.bible.sharedcore.platform.IosDeviceSettings
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertEquals

class IosAppIconPreferenceTest {
    @Test fun livePreferenceWritesReconcileWithoutRestartAndStopAfterDisposal() {
        val suite = "org.andbible.test.${NSUUID().UUIDString}"
        val defaults = NSUserDefaults(suiteName = suite)
        val settings = IosDeviceSettings(defaults)
        var current: String? = null
        val calls = mutableListOf<String?>()
        val request = AppIconRequest(
            supportsAlternateIcons = { true },
            currentName = { current },
            setIcon = { name, complete -> calls += name; current = name; complete(true) },
            schedule = { _, _ -> error("Successful changes must not retry") },
        )
        val cancel = settings.observeBoolean("discrete_mode", false, request::apply)
        try {
            // A different writer is what the future H-owned Settings service will use.
            val writer = IosDeviceSettings(defaults)
            writer.putBoolean("discrete_mode", false)
            writer.putBoolean("discrete_mode", true)
            writer.putBoolean("discrete_mode", false)
            assertEquals(listOf("CalculatorIcon", null), calls)
            cancel()
            writer.putBoolean("discrete_mode", true)
            assertEquals(listOf("CalculatorIcon", null), calls)
        } finally {
            cancel()
            defaults.removePersistentDomainForName(suite)
        }
    }
}
