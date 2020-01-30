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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import net.bible.android.activity.R
import net.bible.android.control.page.window.Window
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.activity.page.MainBibleActivity
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.ColorPreference
import net.bible.android.view.activity.page.FontSizePreference
import net.bible.android.view.activity.page.MarginSizePreference
import net.bible.android.view.activity.page.MorphologyPreference
import net.bible.android.view.activity.page.StrongsPreference
import net.bible.android.view.activity.settings.TextDisplaySettingsActivity.Companion.FROM_COLORS
import net.bible.android.view.util.BookmarkColorPreferenceDialog
import net.bible.android.view.util.BookmarkColourPreference
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.device.ScreenSettings.autoModeAvailable
import net.bible.service.device.ScreenSettings.systemModeAvailable
import net.bible.android.view.activity.page.Preference as ItemPreference

import javax.inject.Inject

class SettingsActivity: ActivityBase() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.settings_activity)
		super.buildActivityComponent().inject(this)

		CurrentActivityHolder.getInstance().currentActivity = this
		supportFragmentManager
			.beginTransaction()
			.replace(R.id.settings_container, SettingsFragment())
			.commit()
		LocaleHelper.translateTitle(this)
	}

	override fun attachBaseContext(newBase: Context) {
		super.attachBaseContext(LocaleHelper.onAttach(newBase))
	}

	override fun onStop() {
		super.onStop()
		Log.i(localClassName, "onStop")
		// call this onStop, although it is not guaranteed to be called, to ensure an overlap between dereg and reg of current activity, otherwise AppToBackground is fired mistakenly
		CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this)
	}
}

class SettingsFragment : PreferenceFragmentCompat() {
	override fun onDisplayPreferenceDialog(preference: Preference?) {
		if(fragmentManager!!.findFragmentByTag("customTag") != null)
			return
		if(preference is BookmarkColourPreference) {
			val f = BookmarkColorPreferenceDialog.newInstance(preference.key, preference.findIndexOfValue(preference.value))
			f.setTargetFragment(this, 0)
			f.show(fragmentManager!!, "customTag")
		} else {
			super.onDisplayPreferenceDialog(preference)
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        try {
            setPreferencesFromResource(R.xml.settings, rootKey)

			//If no light sensor exists switch to old boolean check box
			// see here for method: http://stackoverflow.com/questions/4081533/how-to-remove-android-preferences-from-the-screen
            val nightModePref = preferenceScreen.findPreference<ListPreference>("night_mode_pref2") as ListPreference
			if (!autoModeAvailable) {
                nightModePref.setEntries(R.array.prefs_night_mode_descriptions_noauto)
                nightModePref.setEntryValues(R.array.prefs_night_mode_values_noauto)
				nightModePref.isVisible = false
            }
            if (systemModeAvailable) {
                if (autoModeAvailable) {
                    nightModePref.setEntries(R.array.prefs_night_mode_descriptions_noyes)
                    nightModePref.setEntryValues(R.array.prefs_night_mode_values_noyes)
                } else {
                    preferenceScreen.removePreference(nightModePref)
                }
            }
            // if locale is overridden then have to force title to be translated here
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing preference screen", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
    }

    /**
     * Override locale.  If user has selected a different ui language to the devices default language
     */

    companion object {
        private const val TAG = "SettingsActivity"
    }
}

class ColorSettingsDataStore(val activity: ColorSettingsActivity, val window: Window?, val windowRepository: WindowRepository): PreferenceDataStore() {
    override fun putInt(key: String?, value: Int) {
        activity.setDirty()
        val settings = window?.pageManager?.actualTextDisplaySettings ?: windowRepository.textDisplaySettings
        val colors = settings.colors?.copy() ?: WorkspaceEntities.TextDisplaySettings.default.colors!!

        when(key) {
            "text_color_day" -> colors.dayTextColor = value
            "text_color_night" -> colors.nightTextColor = value
            "background_color_day" -> colors.dayBackground = value
            "background_color_night" -> colors.nightBackground = value
            "noise_day" -> colors.dayNoise = value
            "noise_night" -> colors.nightNoise = value
        }
        if(window != null) {
            window.pageManager.textDisplaySettings.colors = colors
        } else {
            windowRepository.textDisplaySettings.colors = colors
            windowRepository.updateWindowTextDisplaySettingsValues(WorkspaceEntities.TextDisplaySettings.Types.COLORS, colors)
        }

    }

    override fun getInt(key: String?, defValue: Int): Int {
        val colors =
            if(window != null) {
                window.pageManager.actualTextDisplaySettings.colors!!
            } else windowRepository.textDisplaySettings.colors ?: WorkspaceEntities.TextDisplaySettings.default.colors!!

        return when(key) {
            "text_color_day" -> colors.dayTextColor?: defValue
            "text_color_night" -> colors.nightTextColor?: defValue
            "background_color_day" -> colors.dayBackground?: defValue
            "background_color_night" -> colors.nightBackground?: defValue
            "noise_day" -> colors.dayNoise?: defValue
            "noise_night" -> colors.nightNoise?: defValue
            else -> defValue
        }
    }
}

@ActivityScope
class ColorSettingsActivity: ActivityBase() {
    var window: Window? = null
    private var dirty = false

    @Inject lateinit var windowControl: WindowControl
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        super.buildActivityComponent().inject(this)
        dirty = false

        CurrentActivityHolder.getInstance().currentActivity = this

        val windowId = intent.extras?.getLong("windowId")
        window = windowControl.windowRepository.getWindow(windowId)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, ColorSettingsFragment(this, window, windowControl.windowRepository))
            .commit()

        setResult()
    }

    fun setDirty() {
        dirty = true
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, ColorSettingsActivity::class.java)
        val window = this.window

        if(window != null) {
            title = getString(R.string.window_color_settings_title)
        } else {
            title = getString(R.string.workspace_color_settings_title)
        }
        resultIntent.putExtra("windowId", window?.id ?: 0L)
        resultIntent.putExtra("edited", dirty)
        setResult(Activity.RESULT_OK, resultIntent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onStop() {
        super.onStop()
        Log.i(localClassName, "onStop")
        // call this onStop, although it is not guaranteed to be called, to ensure an overlap between dereg and reg of current activity, otherwise AppToBackground is fired mistakenly
        CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this)
    }
}


class ColorSettingsFragment(val activity: ColorSettingsActivity, val window: Window?, val windowRepository: WindowRepository) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = ColorSettingsDataStore(activity, window, windowRepository)
        setPreferencesFromResource(R.xml.color_settings, rootKey)
    }
}

class TextDisplaySettingsDataStore(val activity: TextDisplaySettingsActivity, val window: Window?, val windowRepository: WindowRepository): PreferenceDataStore() {
    override fun putBoolean(key: String, value: Boolean) {
        val type = Types.valueOf(key)
        val prefItem = getPrefItem(window, type)
        val oldValue = prefItem.value
        prefItem.value = value
        if(oldValue != value) {
            activity.setDirty(prefItem.requiresReload)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val type = Types.valueOf(key)
        val settings =
            if(window != null) window.pageManager.actualTextDisplaySettings
            else(windowRepository.textDisplaySettings)

        return (settings.getValue(type) ?: WorkspaceEntities.TextDisplaySettings.default.getValue(type)) as Boolean
    }
}

fun getPrefItem(window: Window?, type: Types): OptionsMenuItemInterface =
    when(type) {
        Types.BOOKMARKS -> ItemPreference(window, Types.BOOKMARKS)
        Types.REDLETTERS -> ItemPreference(window, Types.REDLETTERS)
        Types.SECTIONTITLES -> ItemPreference(window, Types.SECTIONTITLES)
        Types.VERSENUMBERS -> ItemPreference(window, Types.VERSENUMBERS)
        Types.VERSEPERLINE -> ItemPreference(window, Types.VERSEPERLINE)
        Types.FOOTNOTES -> ItemPreference(window, Types.FOOTNOTES)
        Types.MYNOTES -> ItemPreference(window, Types.MYNOTES)

        Types.STRONGS -> StrongsPreference(window)
        Types.MORPH -> MorphologyPreference(window)
        Types.FONTSIZE -> FontSizePreference(window)
        Types.MARGINSIZE -> MarginSizePreference(window)
        Types.COLORS -> ColorPreference(window)
    }

class TextDisplaySettingsFragment(val activity: TextDisplaySettingsActivity, val window: Window?, val windowRepository: WindowRepository) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = TextDisplaySettingsDataStore(activity, window, windowRepository)
        setPreferencesFromResource(R.xml.text_options, rootKey)
        updateIcons()
    }

    private fun updateIcons() {
        for(p in getPreferenceList()) {
            updateIcon(p)
        }
    }

    private fun updateIcon(p: Preference) {
        val type = Types.valueOf(p.key)
        val itmOptions = getPrefItem(window, type)
        if(window != null) {
            if (itmOptions.inherited) {
                p.setIcon(R.drawable.ic_sync_white_24dp)
            } else {
                p.setIcon(R.drawable.ic_sync_disabled_green_24dp)
            }
        }
        p.isEnabled = itmOptions.enabled
        p.isVisible = itmOptions.visible
        if(itmOptions.title != null) {
            p.title = itmOptions.title
        }
    }

    private fun getPreferenceList(p_: Preference? = null, list_: ArrayList<Preference>? = null): ArrayList<Preference> {
        val p = p_?: preferenceScreen
        val list = list_?: ArrayList()
        if (p is PreferenceCategory || p is PreferenceScreen) {
            val pGroup: PreferenceGroup = p as PreferenceGroup
            val pCount: Int = pGroup.preferenceCount
            for (i in 0 until pCount) {
                getPreferenceList(pGroup.getPreference(i), list) // recursive call
            }
        } else {
            list.add(p)
        }
        return list
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        val type = Types.valueOf(preference?.key!!)
        var returnValue = true
        when (type) {
            Types.COLORS -> {
                val intent = Intent(activity, ColorSettingsActivity::class.java)
                intent.putExtra("windowId", window?.id ?: 0)
                activity.startActivityForResult(intent, FROM_COLORS)
                return true
            }
            Types.MARGINSIZE -> {

            }
            Types.FONTSIZE -> {

            }
            else -> {
                returnValue = super.onPreferenceTreeClick(preference)
                updateIcons()
            }
        }
        return returnValue
    }
}


@ActivityScope
class TextDisplaySettingsActivity: ActivityBase() {
    var window: Window? = null
    private var dirty = false
    private var requiresReload = false

    @Inject lateinit var windowControl: WindowControl
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        super.buildActivityComponent().inject(this)
        dirty = false
        requiresReload = false

        CurrentActivityHolder.getInstance().currentActivity = this

        val windowId = intent.extras?.getLong("windowId")
        window = windowControl.windowRepository.getWindow(windowId)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, TextDisplaySettingsFragment(this, window, windowControl.windowRepository))
            .commit()

        setResult()
    }

    fun setDirty(requiresReload: Boolean = false) {
        dirty = true
        if(requiresReload)
            this.requiresReload = true
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, ColorSettingsActivity::class.java)
        val window = this.window

        if(window != null) {
            title = getString(R.string.window_color_settings_title)
        } else {
            title = getString(R.string.workspace_color_settings_title)
        }
        resultIntent.putExtra("windowId", window?.id ?: 0L)
        resultIntent.putExtra("requiresReload", requiresReload)
        resultIntent.putExtra("edited", dirty)
        setResult(Activity.RESULT_OK, resultIntent)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            FROM_COLORS -> {
                setDirty(true)
            }
        }
        return super.onActivityResult(requestCode, resultCode, data)
    }
    override fun onStop() {
        super.onStop()
        Log.i(localClassName, "onStop")
        // call this onStop, although it is not guaranteed to be called, to ensure an overlap between dereg and reg of current activity, otherwise AppToBackground is fired mistakenly
        CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this)
    }
    companion object{
        const val FROM_COLORS = 1
    }
}
