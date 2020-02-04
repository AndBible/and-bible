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
import android.view.View
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import kotlinx.android.synthetic.main.settings_dialog.*
import net.bible.android.activity.R
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.util.locale.LocaleHelper

class ColorSettingsDataStore(
    val activity: ColorSettingsActivity,
    val colors: WorkspaceEntities.Colors
): PreferenceDataStore() {
    override fun putInt(key: String?, value: Int) {
        when(key) {
            "text_color_day" -> colors.dayTextColor = value
            "text_color_night" -> colors.nightTextColor = value
            "background_color_day" -> colors.dayBackground = value
            "background_color_night" -> colors.nightBackground = value
            "noise_day" -> colors.dayNoise = value
            "noise_night" -> colors.nightNoise = value
        }
        activity.setDirty()
    }

    override fun getInt(key: String?, defValue: Int): Int {
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
    private lateinit var settingsBundle: SettingsBundle
    private lateinit var colors: WorkspaceEntities.Colors
    private var dirty = false
    private var reset = false

    override val dayTheme = R.style.Theme_AppCompat_Light_Dialog_MinWidth
    override val nightTheme = R.style.Theme_AppCompat_DayNight_Dialog_MinWidth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_dialog)
        super.buildActivityComponent().inject(this)
        dirty = false
        reset = false

        CurrentActivityHolder.getInstance().currentActivity = this

        settingsBundle = SettingsBundle.fromJson(intent.extras?.getString("settingsBundle")!!)
        colors = settingsBundle.actualSettings.colors?: WorkspaceEntities.TextDisplaySettings.default.colors!!

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, ColorSettingsFragment(this, colors))
            .commit()

        if(settingsBundle.windowId != null) {
            title = getString(R.string.window_color_settings_title)
        } else {
            title = getString(R.string.workspace_color_settings_title)
            resetButton.visibility = View.INVISIBLE
        }
        okButton.setOnClickListener {finish()}
        cancelButton.setOnClickListener {
            dirty = false
            setResult()
            finish()
        }
        resetButton.setOnClickListener {
            reset = true
            setResult()
            finish()
        }


        setResult()
    }

    fun setDirty() {
        dirty = true
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, ColorSettingsActivity::class.java)

        resultIntent.putExtra("edited", dirty)
        resultIntent.putExtra("reset", reset)
        resultIntent.putExtra("windowId", settingsBundle.windowId)
        resultIntent.putExtra("colors", colors.toJson())

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


class ColorSettingsFragment(val activity: ColorSettingsActivity, val settings: WorkspaceEntities.Colors) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = ColorSettingsDataStore(activity, settings)
        setPreferencesFromResource(R.xml.color_settings, rootKey)
    }
}
