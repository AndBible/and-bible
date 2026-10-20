package net.bible.android.view.activity.settings

import java.lang.reflect.Proxy
import kotlin.test.*
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedcore.settings.*

class AppSettingsServiceImplTest {
    private val db = MemorySettings()
    private val local = MemoryLocal()
    private val flags = Flags()
    private var scans = 0
    private val dictionaries = object : DictionaryOptionsProvider {
        override val greekDictOptions get() = options.also { scans++ }
        override val hebrewDictOptions get() = options.also { scans++ }
        override val greekMorphOptions get() = options.also { scans++ }
        override val wordLookupDictOptions get() = options.also { scans++ }
    }
    private val options = listOf(DictOption("A", "Alpha"), DictOption("B", "Beta"))
    private val strings = Proxy.newProxyInstance(CoreStrings::class.java.classLoader, arrayOf(CoreStrings::class.java)) { _, method, _ ->
        when {
            method.name.endsWith("Values") || method.name.endsWith("Ids") -> listOf("A", "B")
            method.returnType == List::class.java -> listOf("Alpha", "Beta")
            else -> error("Unexpected string: ${method.name}")
        }
    } as CoreStrings
    private fun service() = AppSettingsServiceImpl(dictionaries, db, local, strings, flags)

    @Test fun deviceLocalRoutingAndDefaults() {
        val s = service()
        assertEquals("system", s.snapshot.value.nightMode)
        assertEquals("1234", s.snapshot.value.calculatorPin)
        assertFalse(s.snapshot.value.fullScreenHideButtons)
        assertEquals(100, s.snapshot.value.fontSizeMultiplier)
        s.setBool("discrete_mode", true)
        s.setString("night_mode_pref3", "manual")
        s.setString("locale_pref", "fi")
        s.setBool("eink_mode", true)
        assertTrue(local.getBoolean("discrete_mode", false))
        assertEquals("manual", local.getString("night_mode_pref3", null))
        assertEquals("fi", local.getString("locale_pref", null))
        assertFalse(db.getBoolean("discrete_mode", false))
        assertTrue(db.getBoolean("eink_mode", false))
        s.setInt("night_mode_any", 5)
        assertEquals(5, db.getInt("night_mode_any", 0))
    }

    @Test fun inverseSelectionsAndDictionaryCacheSurviveRefresh() {
        db.setStringSet("disabled_word_lookup_dictionaries", setOf("B"))
        val s = service()
        assertEquals(setOf("A"), s.snapshot.value.enabledWordLookupDicts)
        assertEquals(setOf("A", "B"), s.snapshot.value.greekDicts)
        s.setStringSet("disabled_word_lookup_dictionaries", setOf("B"))
        assertEquals(setOf("A"), db.getStringSet("disabled_word_lookup_dictionaries"))
        for (key in listOf("disable_bible_bookmark_modal_buttons", "disable_gen_bookmark_modal_buttons")) {
            s.setStringSet(key, setOf("A"))
            assertEquals(setOf("B"), db.getStringSet(key))
        }
        s.refresh()
        assertEquals(4, scans)
    }

    @Test fun flagsDefaultsChoicesAndSystemBarNotification() {
        flags.isOnyx = true
        val s = service()
        assertEquals("monochrome", s.snapshot.value.displayColorMode)
        assertFalse(s.snapshot.value.sdcardPermissionVisible)
        assertFalse(s.snapshot.value.openLinksVisible)
        assertEquals(listOf(Choice2("A", "Alpha"), Choice2("B", "Beta")), s.snapshot.value.toolbarButtonActionChoices)
        s.setBool("hide_status_bar", true)
        s.setBool("volume_keys_scroll", false)
        assertEquals(1, flags.notifications)
        s.setString("display_color_mode", "bw")
        assertEquals("bw", s.snapshot.value.displayColorMode)
    }

    @Test fun resetClearsTheSameKeysOnBothPlatforms() {
        val preserved = listOf("eink_mode", "enable_bluetooth_pref", "volume_keys_scroll", "hide_status_bar")
        val cleared = listOf(
            "strongs_greek_dictionary", "strongs_hebrew_dictionary", "robinson_greek_morphology",
            "disabled_word_lookup_dictionaries", "navigate_to_verse_pref", "open_links_in_special_window_pref",
            "screen_keep_on_pref", "auto_fullscreen_pref", "full_screen_hide_buttons_pref", "hide_window_buttons",
            "hide_bible_reference_overlay", "show_active_window_indicator", "toolbar_button_actions",
            "disable_two_step_bookmarking", "double_tap_to_fullscreen", "night_mode_pref3",
            "request_sdcard_permission_pref", "show_errorbox", "show_calculator", "calculator_pin",
            "google_drive_sync", "disable_bible_bookmark_modal_buttons", "disable_gen_bookmark_modal_buttons",
            "display_color_mode", "theme_palette_style", "disable_animations", "disable_click_to_edit",
            "font_size_multiplier", "bible_view_swipe_mode", "experimental_features", "notes_content_type",
        )
        for (key in cleared + preserved) {
            db.setString(key, "stored")
            db.setBoolean(key, true)
            db.setLong(key, 2L)
            db.setDouble(key, 3.0)
        }
        for (key in listOf("locale_pref", "calculator_pin", "show_calculator", "discrete_mode", "night_mode_pref3")) local.setString(key, "stored")
        SettingsReset.performReset(db, local)
        assertEquals(cleared, SettingsReset.RESET_KEYS)
        for (key in cleared) {
            assertNull(db.getString(key), key)
            assertFalse(db.getBoolean(key, false), key)
            assertEquals(0L, db.getLong(key, 0L), key)
            assertEquals(0.0, db.getDouble(key, 0.0), key)
        }
        for (key in listOf("locale_pref", "calculator_pin", "show_calculator", "discrete_mode")) assertNull(local.getString(key, null), key)
        assertEquals("stored", local.getString("night_mode_pref3", null))
        for (key in preserved) assertEquals("stored", db.getString(key), key)
    }

    private class Flags : SettingsPlatformFlags {
        override val sdcardPermissionVisible = false
        override val openLinksVisible = false
        override val debugToolsVisible = false
        override val autoNightModeAvailable = false
        override var isOnyx = false
        var notifications = 0
        override fun onSystemBarSettingChanged() { notifications++ }
    }
    private class MemoryLocal : DeviceLocalPrefs {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun setString(key: String, value: String) { values[key] = value }
        override fun setBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
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
