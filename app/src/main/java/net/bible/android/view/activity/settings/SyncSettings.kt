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

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.util.Hourglass
import net.bible.service.devicesync.DatabaseCategory
import net.bible.service.devicesync.DeviceSynchronize

class SyncSettingsActivity: ActivityBase() {
    private lateinit var binding: SettingsDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.buildActivityComponent().inject(this)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SyncSettingsFragment(this))
            .commit()

    }
}


class SyncSettingsFragment(val activity: ActivityBase): PreferenceFragmentCompat() {
    private fun setupDrivePref(pref: SwitchPreferenceCompat) {
        val category = DatabaseCategory.nameToCategory[pref.key.split("_")[1].uppercase()]!!
        pref.setOnPreferenceClickListener {
            if(category.enabled) {
                lifecycleScope.launch {
                    val hourglass = Hourglass(activity)
                    hourglass.show(R.string.synchronizing)
                    if (!DeviceSynchronize.signedIn) {
                        val success = DeviceSynchronize.signIn(activity)
                        if (!success) {
                            category.setStatus(false)
                        }
                    }
                    if (DeviceSynchronize.signedIn && category.enabled) {
                        DeviceSynchronize.synchronize()
                    }
                    hourglass.dismiss()
                }
            }
            true
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = PreferenceStore()
        setPreferencesFromResource(R.xml.sync_settings, rootKey)
        preferenceScreen.findPreference<SwitchPreferenceCompat>("gdrive_bookmarks").run { setupDrivePref(this!!) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("gdrive_readingplans").run { setupDrivePref(this!!) }
        preferenceScreen.findPreference<SwitchPreferenceCompat>("gdrive_workspaces").run { setupDrivePref(this!!) }
    }
}
