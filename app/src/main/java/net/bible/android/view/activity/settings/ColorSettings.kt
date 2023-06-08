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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.base.ActivityBase

class ColorSettingsDataStore(val activity: ColorSettingsActivity): PreferenceDataStore() {
    val colors get() = activity.colors

    override fun putInt(key: String?, value: Int) {
        when(key) {
            "text_color_day" -> colors.dayTextColor = value
            "text_color_night" -> colors.nightTextColor = value
            "background_color_day" -> colors.dayBackground = value
            "background_color_night" -> colors.nightBackground = value
            "noise_day" -> colors.dayNoise = value
            "noise_night" -> colors.nightNoise = value
            "workspace_color" -> colors.workspaceColor = value
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
            "workspace_color" -> colors.workspaceColor?: defValue
            else -> defValue
        }
    }
}

class ColorSettingsActivity: ActivityBase() {
    private lateinit var binding: SettingsDialogBinding

    private lateinit var settingsBundle: SettingsBundle

    internal lateinit var colors: WorkspaceEntities.Colors
    internal var workspaceSettings: WorkspaceEntities.WorkspaceSettings? = null
    private var dirty = false
    private var reset = false

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.text_options_opts, menu)
        menu.findItem(R.id.help).isVisible = false
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

    fun cancel() {
        dirty = false
        setResult()
        finish()
    }

    fun reset() {
        AlertDialog.Builder(this)
            .setPositiveButton(R.string.yes) {_, _ ->
                reset = true
                setResult()
                finish()
            }
            .setNegativeButton(R.string.no,null)
            .setMessage(getString(R.string.reset_are_you_sure))
            .create()
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsBundle = SettingsBundle.fromJson(intent.extras?.getString("settingsBundle")!!)
        colors = settingsBundle.actualSettings.colors!!
        colors.workspaceColor = settingsBundle.workspaceSettings.colors?.workspaceColor

        super.onCreate(savedInstanceState)

        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)
        dirty = false
        reset = false

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, ColorSettingsFragment(isWindow = settingsBundle.windowId != null))
            .commit()

        if(settingsBundle.windowId != null) {
            title = getString(R.string.window_color_settings_title)
        } else {
            title = getString(R.string.workspace_color_settings_title)
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
        resultIntent.putExtra("windowId", settingsBundle.windowId?.toString())
        resultIntent.putExtra("colors", colors.toJson())
        resultIntent.putExtra("workspaceColor", workspaceSettings?.workspaceColor)

        setResult(Activity.RESULT_OK, resultIntent)
    }
}


class ColorSettingsFragment(val isWindow: Boolean = false): PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as ColorSettingsActivity

        preferenceManager.preferenceDataStore = ColorSettingsDataStore(activity)
        setPreferencesFromResource(R.xml.color_settings, rootKey)
        if(isWindow) {
            findPreference<Preference>("workspace_color")?.isVisible = false
        }
    }
}
