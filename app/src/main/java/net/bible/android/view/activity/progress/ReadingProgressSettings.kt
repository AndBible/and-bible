/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.progress

import android.os.Bundle
import android.view.MenuItem
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import net.bible.android.activity.R
import net.bible.android.activity.databinding.SettingsDialogBinding
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.ReadingProgressSettings

class ReadingProgressSettingsDataStore : PreferenceDataStore() {
    override fun putBoolean(key: String?, value: Boolean) {
        when (key) {
            "auto_track_reading" -> ReadingProgressSettings.autoTrackReading = value
            "auto_mark_memorized" -> ReadingProgressSettings.autoMarkMemorized = value
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when (key) {
            "auto_track_reading" -> ReadingProgressSettings.autoTrackReading
            "auto_mark_memorized" -> ReadingProgressSettings.autoMarkMemorized
            else -> defValue
        }
    }
}

class ReadingProgressSettingsActivity : ActivityBase() {
    private lateinit var binding: SettingsDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsDialogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, ReadingProgressSettingsFragment())
            .commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class ReadingProgressSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = ReadingProgressSettingsDataStore()
        setPreferencesFromResource(R.xml.reading_progress_settings, rootKey)
    }
}
