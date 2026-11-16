/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import android.text.InputType
import android.webkit.URLUtil
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.launch
import net.bible.android.activity.R
import net.bible.android.view.activity.base.Dialogs
import net.bible.android.view.util.Hourglass
import net.bible.service.cloudsync.phos.PhosSync
import net.bible.service.cloudsync.phos.PhosSyncSettings
import net.bible.service.common.CommonUtils
import java.text.DateFormat
import java.util.Date

/** Keeps the Phos sync preferences in the app's plain shared preferences, like the cloud sync credentials. */
private class PhosPrefsDataStore : PreferenceDataStore() {
    private val prefs get() = CommonUtils.realSharedPreferences
    override fun putString(key: String, value: String?) {
        prefs.edit { if (value == null) remove(key) else putString(key, value.trim()) }
    }
    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)
}

/** The "Phos Nextcloud" category of the sync settings. */
object PhosSyncPreferences {
    private val keys = listOf(PhosSyncSettings.ENABLED, PhosSyncSettings.SERVER, PhosSyncSettings.USER, PhosSyncSettings.PASSWORD,
        PhosSyncSettings.BOOKMARKS, PhosSyncSettings.WORKSPACES, PhosSyncSettings.READING_PLANS)

    fun setup(fragment: PreferenceFragmentCompat) {
        val screen = fragment.preferenceScreen
        val store = PhosPrefsDataStore()
        for (k in keys) screen.findPreference<Preference>(k)?.preferenceDataStore = store
        // the data store was attached after inflation: show what is stored
        for (k in listOf(PhosSyncSettings.ENABLED, PhosSyncSettings.BOOKMARKS, PhosSyncSettings.WORKSPACES, PhosSyncSettings.READING_PLANS)) {
            screen.findPreference<SwitchPreferenceCompat>(k)?.let { it.isChecked = store.getBoolean(k, k != PhosSyncSettings.ENABLED) }
        }
        for (k in listOf(PhosSyncSettings.SERVER, PhosSyncSettings.USER, PhosSyncSettings.PASSWORD)) {
            screen.findPreference<EditTextPreference>(k)?.let { it.text = store.getString(k, null) }
        }
        screen.findPreference<EditTextPreference>(PhosSyncSettings.SERVER)?.apply {
            if (text.isNullOrEmpty()) text = CommonUtils.realSharedPreferences.getString("cloud_sync_server_url", null)
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            setOnPreferenceChangeListener { _, v ->
                val url = (v as String).trim()
                val ok = url.isEmpty() || (URLUtil.isValidUrl(url) && (url.startsWith("https://") || url.startsWith("http://")) && !url.contains(" "))
                if (!ok) Dialogs.showErrorMsg(R.string.invalid_url_message)
                ok
            }
        }
        screen.findPreference<EditTextPreference>(PhosSyncSettings.USER)?.apply {
            if (text.isNullOrEmpty()) text = CommonUtils.realSharedPreferences.getString("cloud_sync_username", null)
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        }
        screen.findPreference<EditTextPreference>(PhosSyncSettings.PASSWORD)?.setOnBindEditTextListener {
            it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val now = screen.findPreference<Preference>("phos_sync_now")
        fun showLast() {
            now?.summary = if (PhosSyncSettings.lastRun == 0L && PhosSyncSettings.lastResult.isEmpty()) {
                fragment.getString(R.string.phos_sync_never)
            } else {
                fragment.getString(R.string.phos_sync_last,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(PhosSyncSettings.lastRun)),
                    PhosSyncSettings.lastResult)
            }
        }
        showLast()
        now?.setOnPreferenceClickListener {
            fragment.lifecycleScope.launch {
                val hourglass = Hourglass(fragment.requireContext())
                hourglass.show(R.string.synchronizing)
                PhosSync.synchronize(force = true)
                hourglass.dismiss()
                showLast()
            }
            true
        }
        screen.findPreference<Preference>("phos_sync_reset")?.setOnPreferenceClickListener {
            PhosSync.reset()
            showLast()
            true
        }
    }
}
