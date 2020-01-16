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

import android.content.Context
import android.os.Bundle
import android.preference.ListPreference
import android.preference.PreferenceActivity
import android.util.Log
import net.bible.android.activity.R
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.service.device.ScreenSettings.autoModeAvailable
import net.bible.service.device.ScreenSettings.systemModeAvailable

/** show settings
 *
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class SettingsActivity : PreferenceActivity() {
	override fun onCreate(savedInstanceState: Bundle) {
		// change theme according to light sensor
		//UiUtils.INSTANCE.applyTheme(this, false, true);
		super.onCreate(savedInstanceState)
        // allow partial integration with And Bible framework - without this TTS stops
		// putting this before the below ensures any error dialog will be displayed in front of the settings screen and not the previous screen
		// see onStop for paired iAmNoLongerCurrent method call
        CurrentActivityHolder.getInstance().currentActivity = this
        try {
            addPreferencesFromResource(R.xml.settings)
            //If no light sensor exists switch to old boolean check box
			// see here for method: http://stackoverflow.com/questions/4081533/how-to-remove-android-preferences-from-the-screen
            val pref = preferenceScreen.findPreference("night_mode_pref2") as ListPreference
            if (!autoModeAvailable) {
                pref.setEntries(R.array.prefs_night_mode_descriptions_noauto)
                pref.setEntryValues(R.array.prefs_night_mode_values_noauto)
            }
            if (systemModeAvailable) {
                if (autoModeAvailable) {
                    pref.setEntries(R.array.prefs_night_mode_descriptions_noyes)
                    pref.setEntryValues(R.array.prefs_night_mode_values_noyes)
                } else {
                    preferenceScreen.removePreference(pref)
                }
            }
            // if locale is overridden then have to force title to be translated here
            LocaleHelper.translateTitle(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing preference screen", e)
            Dialogs.getInstance().showErrorMsg(R.string.error_occurred, e)
        }
    }

    /**
     * Override locale.  If user has selected a different ui language to the devices default language
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    override fun onStop() {
        super.onStop()
        Log.i(localClassName, "onStop")
        // call this onStop, although it is not guaranteed to be called, to ensure an overlap between dereg and reg of current activity, otherwise AppToBackground is fired mistakenly
        CurrentActivityHolder.getInstance().iAmNoLongerCurrent(this)
    }

    companion object {
        private const val LOCALE_PREF = "locale_pref"
        private const val TAG = "SettingsActivity"
    }
}
