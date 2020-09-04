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
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import kotlinx.android.synthetic.main.settings_dialog.*
import kotlinx.serialization.Serializable
import net.bible.android.activity.R
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings.Types
import net.bible.android.database.WorkspaceEntities
import net.bible.android.view.activity.page.Preference as ItemPreference
import net.bible.android.database.json
import net.bible.android.view.activity.ActivityScope
import net.bible.android.view.activity.base.ActivityBase
import net.bible.android.view.activity.page.ColorPreference
import net.bible.android.view.activity.page.CommandPreference
import net.bible.android.view.activity.page.FontPreference
import net.bible.android.view.activity.page.LineSpacingPreference
import net.bible.android.view.activity.page.MainBibleActivity.Companion.COLORS_CHANGED
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.activity.page.MarginSizePreference
import net.bible.android.view.activity.page.MorphologyPreference
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.page.StrongsPreference
import java.lang.IllegalArgumentException
import java.lang.RuntimeException


class TextDisplaySettingsDataStore(
    private val activity: TextDisplaySettingsActivity,
    private val settingsBundle: SettingsBundle
): PreferenceDataStore() {
    override fun putBoolean(key: String, value: Boolean) {
        val type = Types.valueOf(key)
        val prefItem = getPrefItem(settingsBundle, type)
        val oldValue = prefItem.value
        prefItem.value = value
        if(oldValue != value) {
            activity.setDirty(type, prefItem.requiresReload)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val type = Types.valueOf(key)
        val settings = TextDisplaySettings.actual(settingsBundle.pageManagerSettings, settingsBundle.workspaceSettings)

        return (settings.getValue(type) ?: TextDisplaySettings.default.getValue(type)) as Boolean
    }
}

fun getPrefItem(settings: SettingsBundle, key: String): OptionsMenuItemInterface {
    try {
        val type = Types.valueOf(key)
        return getPrefItem(settings, type)
    }
    catch (e: IllegalArgumentException) {
        return when(key) {
            "apply_to_all_workspaces" -> CommandPreference()
            else -> throw RuntimeException("Unsupported item key")
        }
    }
}

fun getPrefItem(settings: SettingsBundle, type: Types): OptionsMenuItemInterface =
    when(type) {
        Types.BOOKMARKS -> ItemPreference(settings, Types.BOOKMARKS)
        Types.REDLETTERS -> ItemPreference(settings, Types.REDLETTERS)
        Types.SECTIONTITLES -> ItemPreference(settings, Types.SECTIONTITLES)
        Types.VERSENUMBERS -> ItemPreference(settings, Types.VERSENUMBERS)
        Types.VERSEPERLINE -> ItemPreference(settings, Types.VERSEPERLINE)
        Types.FOOTNOTES -> ItemPreference(settings, Types.FOOTNOTES)
        Types.MYNOTES -> ItemPreference(settings, Types.MYNOTES)

        Types.STRONGS -> StrongsPreference(settings)
        Types.MORPH -> MorphologyPreference(settings)
        Types.FONT -> FontPreference(settings)
        Types.MARGINSIZE -> MarginSizePreference(settings)
        Types.COLORS -> ColorPreference(settings)
        Types.JUSTIFY -> ItemPreference(settings, Types.JUSTIFY, requiresReload = false)
        Types.HYPHENATION -> ItemPreference(settings, Types.HYPHENATION, requiresReload = false)
        Types.LINE_SPACING -> LineSpacingPreference(settings)
    }

class TextDisplaySettingsFragment: PreferenceFragmentCompat() {
    private val settingsBundle get() = (activity as TextDisplaySettingsActivity).settingsBundle
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val activity = activity as TextDisplaySettingsActivity
        preferenceManager.preferenceDataStore = TextDisplaySettingsDataStore(activity, settingsBundle)
        setPreferencesFromResource(R.xml.text_display_settings, rootKey)
        updateItems()
    }

    internal fun updateItems() {
        for(p in getPreferenceList()) {
            updateItem(p)
        }
    }

    private val windowId get() = settingsBundle.windowId

    private fun updateItem(p: Preference) {
        val itmOptions = getPrefItem(settingsBundle, p.key)
        if(windowId != null) {
            if (itmOptions.inherited) {
                p.setIcon(R.drawable.ic_sync_white_24dp)
            } else {
                p.setIcon(R.drawable.ic_sync_disabled_green_24dp)
            }
        }
        if(itmOptions is MorphologyPreference) {
            p.isEnabled = StrongsPreference(settingsBundle).value as Boolean
        }
        if(itmOptions.title != null) {
            p.title = itmOptions.title
        }
        p.isVisible = itmOptions.visible
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

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        var returnValue = true
        val prefItem = getPrefItem(settingsBundle, preference.key)
        val type = try {Types.valueOf(preference.key)} catch (e: IllegalArgumentException) { null }
        val activity = activity as TextDisplaySettingsActivity
        val resetFunc = {
            if(prefItem is ItemPreference) {
                prefItem.setNonSpecific()
                activity.setDirty(prefItem.type, prefItem.requiresReload)
            }
            updateItem(preference)
        }
        val handled = prefItem.openDialog(activity, {
            updateItem(preference)
            if(type != null)
                activity.setDirty(type, prefItem.requiresReload)
            else if(prefItem.requiresReload) {
                for(t in Types.values()) {
                    activity.setDirty(t, true)
                }
            }
        }, resetFunc)

        if(!handled) {
            returnValue = super.onPreferenceTreeClick(preference)
            updateItems()
        }

        return returnValue
    }
}

@Serializable
data class DirtyTypesSerializer(val dirtyTypes: MutableSet<Types>) {
    fun toJson(): String {
        return json.stringify(serializer(), this)
    }
    companion object {
        fun fromJson(jsonString: String): DirtyTypesSerializer {
            return json.parse(serializer(), jsonString)
        }
    }
}

@ActivityScope
class TextDisplaySettingsActivity: ActivityBase() {
    private lateinit var fragment: TextDisplaySettingsFragment
    private var requiresReload = false
    private var reset = false
    private val dirtyTypes = mutableSetOf<Types>()

    internal lateinit var settingsBundle: SettingsBundle

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsBundle = SettingsBundle.fromJson(intent.extras?.getString("settingsBundle")!!)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_dialog)
        super.buildActivityComponent().inject(this)
        dirtyTypes.clear()
        requiresReload = false
        reset = false

        val windowId = settingsBundle.windowId
        if(windowId != null) {
            title = getString(R.string.window_text_display_settings_title, mainBibleActivity.windowControl.windowPosition(windowId) + 1)
        } else {
            title = getString(R.string.workspace_text_display_settings_title, settingsBundle.workspaceName)
        }

        val fragment = TextDisplaySettingsFragment()
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, fragment)
            .commit()
        this.fragment = fragment
        okButton.setOnClickListener {finish()}
        cancelButton.setOnClickListener {
            dirtyTypes.clear()
            setResult()
            finish()
        }
        resetButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setPositiveButton(R.string.yes) {_, _ ->
                    reset = true
                    requiresReload = true
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

    fun setDirty(type: Types, requiresReload: Boolean = false) {
        dirtyTypes.add(type)
        if(requiresReload)
            this.requiresReload = true
        setResult()
    }

    fun setResult() {
        val resultIntent = Intent(this, ColorSettingsActivity::class.java)

        resultIntent.putExtra("settingsBundle", settingsBundle.toJson())
        resultIntent.putExtra("requiresReload", requiresReload)
        resultIntent.putExtra("reset", reset)
        resultIntent.putExtra("edited", dirtyTypes.isNotEmpty())
        resultIntent.putExtra("dirtyTypes", DirtyTypesSerializer(dirtyTypes).toJson())

        setResult(Activity.RESULT_OK, resultIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            COLORS_CHANGED -> {
                val extras = data?.extras!!
                val edited = extras.getBoolean("edited")
                val reset = extras.getBoolean("reset")
                val prefItem = getPrefItem(settingsBundle, Types.COLORS)
                if(reset) {
                    prefItem.setNonSpecific()
                    setDirty(Types.COLORS)
                    fragment.updateItems()
                }
                else if(edited) {
                    val colors = WorkspaceEntities.Colors.fromJson(data.extras?.getString("colors")!!)
                    prefItem.value = colors
                    setDirty(Types.COLORS)
                    fragment.updateItems()
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }
}
