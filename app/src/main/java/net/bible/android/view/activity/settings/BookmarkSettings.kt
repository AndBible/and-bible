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
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import kotlinx.android.synthetic.main.settings_dialog.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.bookmark.BookmarkControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.DaggerActivityComponent
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.bookmark.BookmarkLabelSelector
import net.bible.service.common.CommonUtils.json
import javax.inject.Inject

class BookmarkSettingsDataStore(val activity: BookmarkSettingsActivity): PreferenceDataStore() {
    val bookmarks get() = activity.bookmarks

    override fun putBoolean(key: String?, value: Boolean) {
        when(key) {
            "show_all" -> bookmarks.showAll = value
        }
        activity.setDirty()
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when(key) {
            "show_all" -> bookmarks.showAll ?: defValue
            else -> defValue
        }
    }
}

@ActivityScope
class BookmarkSettingsActivity: ActivityBase() {
    private lateinit var settingsBundle: SettingsBundle
    internal lateinit var bookmarks: WorkspaceEntities.BookmarkDisplaySettings
    private var dirty = false
    private var reset = false

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsBundle = SettingsBundle.fromJson(intent.extras?.getString("settingsBundle")!!)
        bookmarks = settingsBundle.actualSettings.bookmarks?: WorkspaceEntities.TextDisplaySettings.default.bookmarks!!

        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_dialog)
        super.buildActivityComponent().inject(this)
        dirty = false
        reset = false

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, BookmarkSettingsFragment())
            .commit()

        if(settingsBundle.windowId != null) {
            title = getString(R.string.window_bookmark_settings_title)
        } else {
            title = getString(R.string.workspace_bookmark_settings_title)
        }
        okButton.setOnClickListener {finish()}
        cancelButton.setOnClickListener {
            dirty = false
            setResult()
            finish()
        }
        resetButton.setOnClickListener {
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

        setResult()
    }

    fun setDirty() {
        dirty = true
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, BookmarkSettingsActivity::class.java)

        resultIntent.putExtra("edited", dirty)
        resultIntent.putExtra("reset", reset)
        resultIntent.putExtra("windowId", settingsBundle.windowId)
        resultIntent.putExtra("bookmarks", json.encodeToString(serializer(), bookmarks))

        setResult(Activity.RESULT_OK, resultIntent)
    }
}

class BookmarkSettingsFragment: PreferenceFragmentCompat() {
    @Inject lateinit var bookmarkControl: BookmarkControl
    init {
        DaggerActivityComponent.builder()
            .applicationComponent(BibleApplication.application.applicationComponent)
            .build().inject(this)
    }

    private fun updateShowLabelsEnabled() {
        val enableShowLabels = !preferenceScreen.findPreference<SwitchPreference>("show_all")!!.isChecked
        preferenceScreen.findPreference<Preference>("show_labels")!!.isEnabled = enableShowLabels
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when(preference.key) {
            "assign_labels" -> GlobalScope.launch(Dispatchers.IO) {
                val activity = activity as BookmarkSettingsActivity
                val labels = activity.bookmarks.assignLabels?.toLongArray() ?: emptyArray<Long>()
                val intent = Intent(activity, BookmarkLabelSelector::class.java)
                intent.putExtra(BookmarkControl.LABEL_IDS_EXTRA, labels)
                intent.putExtra("title", getString(R.string.bookmark_settings_assign_labels_title))
                val result = activity.awaitIntent(intent)
                val assignLabels = result?.resultData?.extras?.getLongArray(BookmarkControl.LABEL_IDS_EXTRA)?.toList()
                if(assignLabels != null) {
                    activity.bookmarks.assignLabels = assignLabels
                    activity.setDirty()
                }
            }
            "show_labels" -> GlobalScope.launch(Dispatchers.IO) {
                val activity = activity as BookmarkSettingsActivity
                val labels = activity.bookmarks.showLabels?.toLongArray() ?: emptyArray<Long>()
                val intent = Intent(activity, BookmarkLabelSelector::class.java)
                intent.putExtra(BookmarkControl.LABEL_IDS_EXTRA, labels)
                intent.putExtra("title", getString(R.string.bookmark_settings_show_labels_title))
                intent.putExtra("showUnassigned", true)
                val result = activity.awaitIntent(intent)
                val showLabels = result?.resultData?.extras?.getLongArray(BookmarkControl.LABEL_IDS_EXTRA)?.toList()
                if(showLabels != null) {
                    activity.bookmarks.showLabels = showLabels
                    activity.setDirty()
                }
            }
        }
        updateShowLabelsEnabled()
        return super.onPreferenceTreeClick(preference)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as BookmarkSettingsActivity

        preferenceManager.preferenceDataStore = BookmarkSettingsDataStore(activity)
        setPreferencesFromResource(R.xml.bookmark_settings, rootKey)
        updateShowLabelsEnabled()
    }
}
