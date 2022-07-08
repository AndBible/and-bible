/*
 * Copyright (c) 2020 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
 *
 * This file is part of And Bible (http://github.com/AndBible/and-bible).
 *
 * And Bible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * And Bible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with And Bible.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package net.bible.android.view.activity.settings

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import net.bible.android.activity.R
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.makeLarger
import net.bible.service.common.getPreferenceList
import net.bible.service.device.ScreenSettings.autoModeAvailable
import net.bible.service.device.ScreenSettings.systemModeAvailable
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType

class PreferenceStore: PreferenceDataStore() {
    private val prefs = CommonUtils.settings
    override fun putInt(key: String, value: Int) {
        prefs.setInt(key, value)
    }

    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)

    override fun putBoolean(key: String, value: Boolean) = prefs.setBoolean(key, value)

    private fun useRealShared(key: String): Boolean = key == "locale_pref" || key.startsWith("night_mode")

    override fun putString(key: String, value: String?) =
        if(useRealShared(key)) CommonUtils.realSharedPreferences.edit().putString(key, value).apply()
        else prefs.setString(key, value)

    override fun getString(key: String, defValue: String?): String? =
        if (useRealShared(key)) CommonUtils.realSharedPreferences.getString(key, defValue)
        else prefs.getString(key, defValue)

    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)

    override fun putLong(key: String, value: Long) = prefs.setLong(key, value)

    override fun getFloat(key: String, defValue: Float): Float = prefs.getFloat(key, defValue)

    override fun putFloat(key: String, value: Float) = prefs.setFloat(key, value)

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        throw RuntimeException("Not supported")

    override fun putStringSet(key: String?, values: MutableSet<String>?): Unit = throw RuntimeException("Not supported")
}


class SettingsActivity: ActivityBase() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.settings_activity)
		super.buildActivityComponent().inject(this)

		supportFragmentManager
			.beginTransaction()
			.replace(R.id.settings_container, SettingsFragment())
			.commit()
	}

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.app_prefs_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        var isHandled = true
        when(item.itemId) {
            R.id.reset -> reset()
            android.R.id.home -> onBackPressed()
            else -> isHandled = false
        }
        if (!isHandled) {
            isHandled = super.onOptionsItemSelected(item)
        }
        return isHandled
    }

    private fun reset() {
        AlertDialog.Builder(this)
            .setMessage(R.string.reset_app_prefs).setCancelable(true)
            .setPositiveButton(R.string.yes
            ) { _, _ ->
                val editor = CommonUtils.settings
                val keys = listOf(
                    "strongs_greek_dictionary",
                    "strongs_hebrew_dictionary",
                    "robinson_greek_morphology",
                    "navigate_to_verse_pref",
                    "open_links_in_special_window_pref",
                    "screen_keep_on_pref",
                    "auto_fullscreen_pref",
                    "full_screen_hide_buttons_pref",
                    "hide_window_buttons",
                    "hide_bible_reference_overlay",
                    "show_active_window_indicator",
                    "toolbar_button_actions",
                    "disable_two_step_bookmarking",
                    "double_tap_to_fullscreen",
                    "night_mode_pref3",
                    "request_sdcard_permission_pref",
                    "show_errorbox"
                )
                for(key in keys) {
                    editor.removeString(key)
                    editor.removeBoolean(key)
                    editor.removeLong(key)
                    editor.removeDouble(key)
                }
                CommonUtils.realSharedPreferences.edit().remove("locale_pref").apply()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
	override fun onDisplayPreferenceDialog(preference: Preference) {
		if(parentFragmentManager.findFragmentByTag("customTag") != null)
			return

        super.onDisplayPreferenceDialog(preference)
	}

    private fun setupDictionary(pref: ListPreference, type: FeatureType): Boolean {
        val dicts = Books.installed().books.filter { it.hasFeature(type) }

        return if(dicts.isEmpty()) {
            pref.isVisible = false
            false
        } else {
            val names = dicts.map { it.name }.toTypedArray()
            val initials = dicts.map { it.initials }.toTypedArray()
            pref.entries = names
            pref.entryValues = initials
            true
        }
    }

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.settings, rootKey)

        //If no light sensor exists switch to old boolean check box
        // see here for method: http://stackoverflow.com/questions/4081533/how-to-remove-android-preferences-from-the-screen
        val nightModePref = preferenceScreen.findPreference<ListPreference>("night_mode_pref3") as ListPreference
        if (systemModeAvailable) {
            if (autoModeAvailable) {
                nightModePref.setEntries(R.array.prefs_night_mode_descriptions_system_auto_manual)
                nightModePref.setEntryValues(R.array.prefs_night_mode_values_system_auto_manual)
                nightModePref.setDefaultValue(R.string.prefs_night_mode_manual)
            } else {
                nightModePref.setEntries(R.array.prefs_night_mode_descriptions_system_manual)
                nightModePref.setEntryValues(R.array.prefs_night_mode_values_system_manual)
                nightModePref.setDefaultValue(R.string.prefs_night_mode_manual)
            }
        } else {
            if (!autoModeAvailable) {
                nightModePref.isVisible = false
            }
        }
        val showErrorBox = preferenceScreen.findPreference<ListPreference>("show_errorbox") as Preference
        showErrorBox.isVisible = CommonUtils.isBeta
        val greekStrongs = preferenceScreen.findPreference<ListPreference>("strongs_greek_dictionary") as ListPreference
        val showGreek = setupDictionary(greekStrongs, FeatureType.GREEK_DEFINITIONS)
        val hebrewStrongs = preferenceScreen.findPreference<ListPreference>("strongs_hebrew_dictionary") as ListPreference
        val showHebrew = setupDictionary(hebrewStrongs, FeatureType.HEBREW_DEFINITIONS)
        val greekMorph = preferenceScreen.findPreference<ListPreference>("robinson_greek_morphology") as ListPreference
        val showGreekMorph = setupDictionary(greekMorph, FeatureType.GREEK_PARSE)
        val dictCategory = preferenceScreen.findPreference<PreferenceCategory>("dictionaries_category") as PreferenceCategory
        dictCategory.isVisible = showGreek || showHebrew || showGreekMorph

        preferenceScreen.findPreference<ListPreference>("toolbar_button_actions")?.apply {
                if (value.isNullOrBlank())
                    value = "default"
            }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pref = preferenceScreen.findPreference<ListPreference>("request_sdcard_permission_pref") as Preference
            pref.isVisible = false
        }

        for(p in getPreferenceList()) {
            val icon = p.icon
            if(icon != null) {
                p.icon = makeLarger(icon, 1.5f)
            }
        }


    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}


