package net.bible.sharedcore.settings

import net.bible.sharedcore.platform.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowButtonsPreferencesTest {
    private class MapSettings : AppSettings {
        val m = mutableMapOf<String, Any?>()
        override fun getString(key: String, default: String?) = (m[key] as String?) ?: default
        override fun getLong(key: String, default: Long) = (m[key] as Long?) ?: default
        override fun getInt(key: String, default: Int) = (m[key] as Int?) ?: default
        override fun getBoolean(key: String, default: Boolean) = (m[key] as Boolean?) ?: default
        override fun getDouble(key: String, default: Double) = (m[key] as Double?) ?: default
        override fun getFloat(key: String, default: Float) = (m[key] as Float?) ?: default
        override fun setString(key: String, value: String?) { m[key] = value }
        override fun setLong(key: String, value: Long?) { m[key] = value }
        override fun setInt(key: String, value: Int?) { m[key] = value }
        override fun setBoolean(key: String, value: Boolean?) { m[key] = value }
        override fun setDouble(key: String, value: Double?) { m[key] = value }
        override fun setFloat(key: String, value: Float?) { m[key] = value }
    }

    @Test fun desktopAndMacDefaultToTextUnderTheButtons() {
        assertTrue(textUnderWindowButtonsDefaultFor(isDesktop = true, isIosAppOnMac = false))
        assertTrue(textUnderWindowButtonsDefaultFor(isDesktop = false, isIosAppOnMac = true))
    }

    @Test fun androidIphoneAndIpadKeepTheReservedStripByDefault() {
        assertFalse(textUnderWindowButtonsDefaultFor(isDesktop = false, isIosAppOnMac = false))
    }

    @Test fun unsetValueFollowsThePlatformDefault() {
        assertEquals(textUnderWindowButtonsDefault, MapSettings().textUnderWindowButtons())
    }

    @Test fun savedChoiceOverridesThePlatformDefault() {
        val settings = MapSettings()
        settings.setBoolean(TEXT_UNDER_WINDOW_BUTTONS_KEY, !textUnderWindowButtonsDefault)
        assertEquals(!textUnderWindowButtonsDefault, settings.textUnderWindowButtons())
    }
}
