package net.bible.sharedui.ios.ports

import kotlin.test.*
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedui.strings.keyed.KeyedCoreStrings
import net.bible.sharedui.strings.keyed.StringTable
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import net.bible.sharedui.host.ports.HostSettingsPlatform

class IosSettingsPlatformTest {
    @Test fun resetClearsTheSameKeysOnBothPlatforms() {
        val domain = "settings-reset-" + NSUUID().UUIDString
        val defaults = NSUserDefaults(suiteName = domain)
        try {
            val local = IosDeviceSettings(defaults)
            val db = MemorySettings()
            val strings = KeyedCoreStrings(StringTable.of("en", { it }, { key, _ -> key }, { emptyList() }))
            val platform = HostSettingsPlatform(strings, db, local)
            for (key in listOf("locale_pref", "calculator_pin", "show_calculator", "discrete_mode", "night_mode_pref3")) local.putString(key, "stored")
            db.setBoolean("navigate_to_verse_pref", true)
            db.setString("night_mode_pref3", "manual")
            db.setString("eink_mode", "retained")
            platform.resetAllSettings()
            // RST-1: reset also clears the device-local night mode (SettingsReset.DEVICE_LOCAL_RESET_KEYS).
            for (key in listOf("locale_pref", "calculator_pin", "show_calculator", "discrete_mode", "night_mode_pref3")) assertNull(local.getString(key), key)
            assertFalse(db.getBoolean("navigate_to_verse_pref", false))
            assertNull(db.getString("night_mode_pref3"))
            assertEquals("retained", db.getString("eink_mode"))
            assertFalse(platform.openLinksVisible)
            assertFalse(platform.sdcardPermissionVisible)
        } finally {
            defaults.removePersistentDomainForName(domain)
        }
    }
    private class MemorySettings : AppSettings {
        private val values = mutableMapOf<String, Any?>()
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
}
