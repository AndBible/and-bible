package net.bible.android.control.activity

import net.bible.sharedcore.platform.ActivityMenuDefault
import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ActivityMenuPreferenceTest {
    private class MemorySettings : AppSettings {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values["String:$key"] as? String ?: default
        override fun getLong(key: String, default: Long) = values["Long:$key"] as? Long ?: default
        override fun getInt(key: String, default: Int) = values["Int:$key"] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = values["Boolean:$key"] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = values["Double:$key"] as? Double ?: default
        override fun getFloat(key: String, default: Float) = values["Float:$key"] as? Float ?: default
        override fun setString(key: String, value: String?) { values["String:$key"] = value }
        override fun setLong(key: String, value: Long?) { values["Long:$key"] = value }
        override fun setInt(key: String, value: Int?) { values["Int:$key"] = value }
        override fun setBoolean(key: String, value: Boolean?) { values["Boolean:$key"] = value }
        override fun setDouble(key: String, value: Double?) { values["Double:$key"] = value }
        override fun setFloat(key: String, value: Float?) { values["Float:$key"] = value }
    }

    @Test fun unsetFollowsPlatformDefaultTrue() {
        val s = MemorySettings()
        assertTrue(ActivityMenuPreference(s, ActivityMenuDefault { true }).enabled.value)
        assertNull(s.values["Boolean:${ActivityMenuPreference.KEY}"], "reading must not persist the default")
    }

    @Test fun unsetFollowsPlatformDefaultFalse() {
        assertFalse(ActivityMenuPreference(MemorySettings(), ActivityMenuDefault { false }).enabled.value)
    }

    @Test fun storedValueWins() {
        val s = MemorySettings().apply { setBoolean(ActivityMenuPreference.KEY, false) }
        assertFalse(ActivityMenuPreference(s, ActivityMenuDefault { true }).enabled.value)
        s.setBoolean(ActivityMenuPreference.KEY, true)
        assertTrue(ActivityMenuPreference(s, ActivityMenuDefault { false }).enabled.value)
    }

    @Test fun setEmitsOnFlowAndPersists() {
        val s = MemorySettings()
        val p = ActivityMenuPreference(s, ActivityMenuDefault { true })
        p.set(false)
        assertFalse(p.enabled.value)
        assertEquals(false, s.getBoolean(ActivityMenuPreference.KEY, true))
        p.set(true)
        assertTrue(p.enabled.value)
    }

    @Test fun storeNotReadyAtConstructionThenReadyPicksUpStoredValue() {
        val s = MemorySettings().apply { setBoolean(ActivityMenuPreference.KEY, false) }
        var ready = false
        val gated = object : AppSettings by s {
            override fun getBoolean(key: String, default: Boolean) = if (ready) s.getBoolean(key, default) else default
        }
        val p = ActivityMenuPreference(gated, ActivityMenuDefault { true })
        assertTrue(p.enabled.value) // not ready: the default
        ready = true
        p.refresh()
        assertFalse(p.enabled.value)
    }

    @Test fun readingEnabledDoesNotReReadTheStore() {
        val s = MemorySettings()
        var reads = 0
        val counting = object : AppSettings by s {
            override fun getBoolean(key: String, default: Boolean): Boolean { reads++; return s.getBoolean(key, default) }
        }
        val p = ActivityMenuPreference(counting, ActivityMenuDefault { true })
        val afterConstruction = reads
        repeat(5) { p.enabled.value }
        assertSame(p.enabled, p.enabled)
        assertEquals(afterConstruction, reads)
    }

    @Test fun keyIsStable() = assertEquals("show_activity_in_menu", ActivityMenuPreference.KEY)
}
