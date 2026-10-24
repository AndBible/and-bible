package net.bible.android.control.speak

import net.bible.sharedcore.platform.AppSettings
import org.junit.Assert.*
import org.junit.Test

class AdvancedSpeakSettingsTest {
    private val settings = MapAppSettings()
    private val adv = AdvancedSpeakSettings(settings)

    @Test fun defaultsMatchThePreviousObject() {
        assertFalse(adv.autoBookmark)
        assertTrue(adv.synchronize)
        assertFalse(adv.replaceDivineName)
        assertFalse(adv.restoreSettingsFromBookmarks)
    }

    @Test fun writesUseTheOldKeysAndAreReadByAnotherInstance() {
        adv.autoBookmark = true
        adv.synchronize = false
        adv.replaceDivineName = true
        adv.restoreSettingsFromBookmarks = true
        assertTrue(settings.getBoolean("speak_autoBookmark", false))
        assertFalse(settings.getBoolean("speak_synchronize", true))
        assertTrue(settings.getBoolean("speak_replaceDivineName", false))
        assertTrue(settings.getBoolean("speak_restoreSettingsFromBookmarks", false))
        val other = AdvancedSpeakSettings(settings)
        assertTrue(other.autoBookmark)
        assertFalse(other.synchronize)
        assertTrue(other.replaceDivineName)
        assertTrue(other.restoreSettingsFromBookmarks)
    }

    @Test fun resetRestoresDefaults() {
        adv.autoBookmark = true
        adv.synchronize = false
        adv.replaceDivineName = true
        adv.restoreSettingsFromBookmarks = true
        adv.reset()
        defaultsMatchThePreviousObject()
    }

    private class MapAppSettings : AppSettings {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun getDouble(key: String, default: Double) = values[key] as? Double ?: default
        override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
        override fun setString(key: String, value: String?) { values[key] = value }
        override fun setLong(key: String, value: Long?) { values[key] = value }
        override fun setInt(key: String, value: Int?) { values[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { values[key] = value }
        override fun setDouble(key: String, value: Double?) { values[key] = value }
        override fun setFloat(key: String, value: Float?) { values[key] = value }
    }
}
