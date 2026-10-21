package net.bible.sharedui.ios.startup

import net.bible.sharedcore.settings.DeviceLocalPrefs
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
}
