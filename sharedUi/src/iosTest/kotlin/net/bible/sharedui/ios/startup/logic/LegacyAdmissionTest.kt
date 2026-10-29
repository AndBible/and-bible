package net.bible.sharedui.ios.startup

import net.bible.sharedcore.settings.DeviceLocalPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bible.sharedui.host.startup.IosStartupGates
import net.bible.sharedui.host.startup.StartupGate
import net.bible.sharedui.host.startup.StartupGateResult
import net.bible.sharedui.host.startup.productionStartupGates

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

    @Test fun legacyGateIsNeverRequiredOnceStartupBegan() {
        assertTrue(legacyGateRequired(settled = false, startupBegan = false))
        assertFalse(legacyGateRequired(settled = true, startupBegan = false))
        // The import row cleared the settled flag in a process whose databases are open: no migration may run.
        assertFalse(legacyGateRequired(settled = false, startupBegan = true))
        assertFalse(legacyGateRequired(settled = true, startupBegan = true))
    }

    @Test fun recreatedHostAfterAnImportRequestDoesNotEnterTheGate() {
        val prefs = MapPrefs()
        val latch = StartupLatch()
        val required = { legacyGateRequired(legacySettled(prefs), latch.began) }
        prefs.setBoolean(LEGACY_SETTLED_KEY, true)
        val firstHost = IosStartupGates(productionStartupGates({ false }, required, { false })).apply { advance() }
        assertEquals(StartupGateResult.Continue, firstHost.result)
        latch.markBegan() // startup opened the databases
        prefs.remove(LEGACY_SETTLED_KEY) // requestImport() cleared the flag
        val recreated = IosStartupGates(productionStartupGates({ false }, required, { false })).apply { advance() }
        assertEquals(null, recreated.current, "the legacy gate must not run in this process")
        assertEquals(StartupGateResult.Continue, recreated.result)
    }

    @Test fun coldLaunchWithRequestedImportStillEntersTheGate() {
        val latch = StartupLatch()
        val gates = IosStartupGates(productionStartupGates({ false }, { legacyGateRequired(false, latch.began) }, { false })).apply { advance() }
        assertTrue(gates.current is StartupGate.LegacyMigration)
    }
}
