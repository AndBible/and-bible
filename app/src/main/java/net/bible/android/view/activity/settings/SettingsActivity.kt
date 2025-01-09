/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.android.view.activity.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.BuildVariant
import net.bible.service.common.CommonUtils
import net.bible.service.common.CommonUtils.makeLarger
import net.bible.service.common.getPreferenceList
import net.bible.service.common.htmlToSpan
import net.bible.service.device.ScreenSettings.autoModeAvailable
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.FeatureType
import java.util.Locale

class PreferenceStore: PreferenceDataStore() {
    private val prefs = CommonUtils.settings
    override fun putInt(key: String, value: Int) {
        prefs.setInt(key, value)
    }

    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        if (useRealShared(key)) CommonUtils.realSharedPreferences.getBoolean(key, defValue)
        else prefs.getBoolean(key, defValue)


    override fun putBoolean(key: String, value: Boolean) =
        if(useRealShared(key)) CommonUtils.realSharedPreferences.edit().putBoolean(key, value).apply()
        else prefs.setBoolean(key, value)

    private fun useRealShared(key: String): Boolean = key == "locale_pref" || key == "calculator_pin" || key == "show_calculator" || key == "discrete_mode" || key.startsWith("night_mode")

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
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> = prefs.getStringSet(key, defValues?: emptySet()).toMutableSet()
    override fun putStringSet(key: String, values: MutableSet<String>?)  = prefs.setStringSet(key, values)
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
                    "show_errorbox",
                    "show_calculator",
                    "calculator_pin",
                    "google_drive_sync",
                    "bible_bookmark_modal_buttons",
                    "gen_bookmark_modal_buttons",
                    "monochrome_mode",
                    "disable_animations",
                    "font_size_multiplier",
                    "bible_view_swipe_mode"
                )
                for(key in keys) {
                    editor.removeString(key)
                    editor.removeBoolean(key)
                    editor.removeLong(key)
                    editor.removeDouble(key)
                }
                CommonUtils.realSharedPreferences.edit().remove("locale_pref").apply()
                CommonUtils.realSharedPreferences.edit().remove("calculator_pin").apply()
                CommonUtils.realSharedPreferences.edit().remove("show_calculator").apply()
                CommonUtils.realSharedPreferences.edit().remove("discrete_mode").apply()
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

    private fun setupDictionary(pref: MultiSelectListPreference, type: FeatureType): Boolean {
        val dicts = Books.installed().books.filter { it.hasFeature(type) }

        return if(dicts.isEmpty()) {
            pref.isVisible = false
            false
        } else {
            val names = dicts.map { it.name }.toTypedArray()
            val initials = dicts.map { it.initials }.toTypedArray()
            pref.entries = names
            pref.entryValues = initials
            pref.setDefaultValue(initials)
            true
        }
    }

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.settings, rootKey)

        //If no light sensor exists switch to old boolean check box
        // see here for method: http://stackoverflow.com/questions/4081533/how-to-remove-android-preferences-from-the-screen
        val nightModePref = preferenceScreen.findPreference<ListPreference>("night_mode_pref3") as ListPreference
        if (autoModeAvailable) {
            nightModePref.setEntries(R.array.prefs_night_mode_descriptions_system_auto_manual)
            nightModePref.setEntryValues(R.array.prefs_night_mode_values_system_auto_manual)
            nightModePref.setDefaultValue("system")
        } else {
            nightModePref.setEntries(R.array.prefs_night_mode_descriptions_system_manual)
            nightModePref.setEntryValues(R.array.prefs_night_mode_values_system_manual)
            nightModePref.setDefaultValue("system")
        }
        val showErrorBox = preferenceScreen.findPreference<ListPreference>("show_errorbox") as Preference
        showErrorBox.isVisible = CommonUtils.isBeta
        val greekStrongs = preferenceScreen.findPreference<MultiSelectListPreference>("strongs_greek_dictionary") as MultiSelectListPreference
        val showGreek = setupDictionary(greekStrongs, FeatureType.GREEK_DEFINITIONS)
        val hebrewStrongs = preferenceScreen.findPreference<MultiSelectListPreference>("strongs_hebrew_dictionary") as MultiSelectListPreference
        val showHebrew = setupDictionary(hebrewStrongs, FeatureType.HEBREW_DEFINITIONS)
        val greekMorph = preferenceScreen.findPreference<MultiSelectListPreference>("robinson_greek_morphology") as MultiSelectListPreference
        val showGreekMorph = setupDictionary(greekMorph, FeatureType.GREEK_PARSE)
        val dictCategory = preferenceScreen.findPreference<PreferenceCategory>("dictionaries_category") as PreferenceCategory
        dictCategory.isVisible = showGreek || showHebrew || showGreekMorph
        val fontSizeMultiplier = preferenceScreen.findPreference<SeekBarPreference>("font_size_multiplier") as SeekBarPreference

        fun getFontSizeMultiplierSummary(value: Int) =
            getString(R.string.pref_font_size_multiplier_summary_1) + " " +
            getString(R.string.pref_font_size_multiplier_summary_2) + " " +
            getString(R.string.current_value,
                String.format(Locale.getDefault(), "%1.1fx", value / 100F)
            )

        fontSizeMultiplier.summary = getFontSizeMultiplierSummary(CommonUtils.settings.fontSizeMultiplier)
        fontSizeMultiplier.setOnPreferenceChangeListener { pref, newValue ->
            fontSizeMultiplier.summary = getFontSizeMultiplierSummary(newValue as Int)
            true
        }

        preferenceScreen.findPreference<ListPreference>("toolbar_button_actions")?.apply {
                if (value.isNullOrBlank())
                    value = "default"
            }

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pref = preferenceScreen.findPreference<ListPreference>("request_sdcard_permission_pref") as Preference
            pref.isVisible = false
        }

        (preferenceScreen.findPreference<EditTextPreference>("calculator_pin") as EditTextPreference).run {
            setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
        }

        (preferenceScreen.findPreference<EditTextPreference>("discrete_mode") as Preference).run {
            if (BuildVariant.Appearance.isDiscrete) {
                isVisible = false
            }
        }
        (preferenceScreen.findPreference<EditTextPreference>("show_calculator") as Preference).run {
            if (BuildVariant.Appearance.isDiscrete) {
                isVisible = false
            }
            summary = listOf(
                getString(R.string.calculator_par1),
                getString(R.string.calculator_par2),
                getString(R.string.calculator_par3),
            ).joinToString(" ")
        }
        (preferenceScreen.findPreference<EditTextPreference>("discrete_help") as Preference).run {
            setOnPreferenceClickListener {
                val linkUrl = "https://github.com/AndBible/and-bible/wiki/Discrete-build"
                val linkText = "<a href=\"$linkUrl\">$linkUrl</a>"

                val dPar1 = getString(R.string.discrete_mode_info_par1)
                val dPar2 = getString(R.string.discrete_mode_info_par2)
                val dLink = getString(R.string.discrete_mode_link, linkText)

                val calcPar1 = getString(R.string.calculator_par1)
                val calcPar2 = getString(R.string.calculator_par2)
                val calcPar3 = getString(R.string.calculator_par3)

                val dText = "$dPar1<br><br>$dPar2<br><br>$dLink<br><br>"
                val calcText = "$calcPar1$calcPar2<br><br>$calcPar3<br><br>$dLink"
                val htmlMessage = if(!BuildVariant.Appearance.isDiscrete) dText else calcText

                val spanned = htmlToSpan(htmlMessage)

                val d = AlertDialog.Builder(context).apply {
                    setTitle(getString(R.string.prefs_persecution_cat))
                    setMessage(spanned)
                    setPositiveButton(R.string.okay, null)
                    setCancelable(true)
                }.create()
                d.show()
                d.findViewById<TextView>(android.R.id.message)!!.movementMethod = LinkMovementMethod.getInstance()
                true
            }
        }
        val crashAppPref = preferenceScreen.findPreference<EditTextPreference>("crash_app") as Preference
        if(!CommonUtils.isBeta) {
            crashAppPref.isVisible = false
        } else {
            crashAppPref.setOnPreferenceClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    ABEventBus.post(BibleApplication.ErrorNotificationEvent("Crashing app in 10 seconds!"))
                    delay(10000)
                    throw RuntimeException("Crash app!")
                }
                true
            }
        }
        val openLinksPref = preferenceScreen.findPreference<EditTextPreference>("open_links") as Preference
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openLinksPref.run {
                setOnPreferenceClickListener {
                    val intent =
                        Intent(
                            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    context.startActivity(intent)
                    true
                }
            }
        } else {
            openLinksPref.isVisible = false
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


