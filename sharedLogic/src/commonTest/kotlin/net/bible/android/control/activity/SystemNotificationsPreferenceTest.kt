package net.bible.android.control.activity

import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemNotificationsPreferenceTest {
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

    @Test fun defaultFollowsConstructorAndIsNotPersisted() {
        val s = MemorySettings()
        assertTrue(SystemNotificationsPreference(s, defaultEnabled = true).enabled.value)
        assertNull(s.values["Boolean:system_notifications"], "reading must not persist the default")
        assertFalse(SystemNotificationsPreference(MemorySettings(), defaultEnabled = false).enabled.value)
    }

    @Test fun storedValueWins() {
        val s = MemorySettings().apply { setBoolean(SystemNotificationsPreference.KEY, false) }
        assertFalse(SystemNotificationsPreference(s, true).enabled.value)
        s.setBoolean(SystemNotificationsPreference.KEY, true)
        assertTrue(SystemNotificationsPreference(s, false).enabled.value)
    }

    @Test fun setEmitsAndPersists() {
        val s = MemorySettings()
        val p = SystemNotificationsPreference(s, true)
        p.set(false)
        assertFalse(p.enabled.value)
        assertEquals(false, s.getBoolean(SystemNotificationsPreference.KEY, true))
    }

    @Test fun refreshRereadsTheStoreButAccessDoesNot() {
        val s = MemorySettings()
        val p = SystemNotificationsPreference(s, true)
        s.setBoolean(SystemNotificationsPreference.KEY, false)
        assertTrue(p.enabled.value, "plain access must not re-read")
        p.refresh()
        assertFalse(p.enabled.value)
    }

    @Test fun independentOfActivityMenuKey() {
        val s = MemorySettings().apply { setBoolean("show_activity_in_menu", false) }
        assertTrue(SystemNotificationsPreference(s, true).enabled.value)
    }

    @Test fun observerReplaysAndDeliversRapidChangesSynchronouslyUntilCancelled() {
        val p = SystemNotificationsPreference(MemorySettings(), true)
        val seen = mutableListOf<Boolean>()
        val subscription = p.observe { seen += it }
        assertEquals(listOf(true), seen)
        p.set(false); p.set(true); p.set(true)
        assertEquals(listOf(true, false, true), seen)
        subscription.cancel(); subscription.cancel()
        p.set(false)
        assertEquals(listOf(true, false, true), seen)
    }

    @Test fun observerReentrantChangePreservesOrderForOtherObserversAndRefresh() {
        val settings = MemorySettings()
        val p = SystemNotificationsPreference(settings, true)
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()
        val a = p.observe { first += it; if (!it) p.set(true) }
        val b = p.observe { second += it }
        settings.setBoolean(SystemNotificationsPreference.KEY, false)
        p.refresh()
        assertEquals(listOf(true, false, true), first)
        assertEquals(listOf(true, false, true), second)
        assertTrue(p.enabled.value)
        a.cancel(); b.cancel()
    }

    @Test fun keyIsStable() = assertEquals("system_notifications", SystemNotificationsPreference.KEY)
}
