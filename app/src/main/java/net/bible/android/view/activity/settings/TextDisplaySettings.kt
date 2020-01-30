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
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.base.CurrentActivityHolder
import net.bible.android.view.activity.page.ColorPreference
import net.bible.android.view.activity.page.FontSizePreference
import net.bible.android.view.activity.page.MarginSizePreference
import net.bible.android.view.activity.page.MorphologyPreference
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.StrongsPreference
import net.bible.android.view.util.locale.LocaleHelper
import net.bible.android.view.util.widget.MarginSizeWidget
import net.bible.android.view.util.widget.TextSizeWidget
import javax.inject.Inject

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
        Types.BOOKMARKS -> net.bible.android.view.activity.page.Preference(window, Types.BOOKMARKS)
        Types.REDLETTERS -> net.bible.android.view.activity.page.Preference(window, Types.REDLETTERS)
        Types.SECTIONTITLES -> net.bible.android.view.activity.page.Preference(window, Types.SECTIONTITLES)
        Types.VERSENUMBERS -> net.bible.android.view.activity.page.Preference(window, Types.VERSENUMBERS)
        Types.VERSEPERLINE -> net.bible.android.view.activity.page.Preference(window, Types.VERSEPERLINE)
        Types.FOOTNOTES -> net.bible.android.view.activity.page.Preference(window, Types.FOOTNOTES)
        Types.MYNOTES -> net.bible.android.view.activity.page.Preference(window, Types.MYNOTES)

        Types.STRONGS -> StrongsPreference(window)
        Types.MORPH -> MorphologyPreference(window)
        Types.FONTSIZE -> FontSizePreference(window)
        Types.MARGINSIZE -> MarginSizePreference(window)
        Types.COLORS -> ColorPreference(window)
    }

class TextDisplaySettingsFragment(val activity: TextDisplaySettingsActivity, val window: Window?, val windowRepository: WindowRepository) : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = TextDisplaySettingsDataStore(activity, window, windowRepository)
        setPreferencesFromResource(R.xml.text_display_settings, rootKey)
        updateItems()
    }

    private fun updateItems() {
        for(p in getPreferenceList()) {
            updateItem(p)
        }
    }

    private fun updateItem(p: Preference) {
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
        val prefItem = getPrefItem(window, type)
        val resetFunc = {
            prefItem.setNonSpecific()
            updateItem(preference)
        }
        when (type) {
            Types.COLORS -> {
                val intent = Intent(activity, ColorSettingsActivity::class.java)
                intent.putExtra("windowId", window?.id ?: 0)
                startActivityForResult(intent, TextDisplaySettingsActivity.FROM_COLORS)
                return true
            }
            Types.MARGINSIZE -> {
                MarginSizeWidget.changeMarginSize(context!!, prefItem.value as WorkspaceEntities.MarginSize,
                    if(window != null) resetFunc else null) {
                    prefItem.value = it
                    activity.setDirty()
                    updateItem(preference)
                }
            }
            Types.FONTSIZE -> {
                TextSizeWidget.changeTextSize(context!!, prefItem.value as Int,
                    if(window != null) resetFunc else null) {
                    prefItem.value = it
                    activity.setDirty()
                    updateItem(preference)
                }
            }
            else -> {
                returnValue = super.onPreferenceTreeClick(preference)
                updateItems()
            }
        }
        return returnValue
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            TextDisplaySettingsActivity.FROM_COLORS -> {
                if(data?.extras?.getBoolean("edited") == true) {
                    activity.setDirty()
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}


@ActivityScope
class TextDisplaySettingsActivity: ActivityBase() {
    var window: Window? = null
    private var dirty = false
    private var requiresReload = false

    @Inject
    lateinit var windowControl: WindowControl
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
            title = getString(R.string.window_text_display_settings_title)
        } else {
            title = getString(R.string.workspace_text_display_settings_title)
        }
        resultIntent.putExtra("windowId", window?.id ?: 0L)
        resultIntent.putExtra("requiresReload", requiresReload)
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
    companion object{
        const val FROM_COLORS = 1
    }
}
