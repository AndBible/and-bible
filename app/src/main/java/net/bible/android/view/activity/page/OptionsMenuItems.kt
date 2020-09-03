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

package net.bible.android.view.activity.page

import android.app.Activity
import android.content.Intent
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.window.NumberOfWindowsChangedEvent
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.page.MainBibleActivity.Companion.COLORS_CHANGED
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.activity.settings.ColorSettingsActivity
import net.bible.android.view.util.widget.MarginSizeWidget
import net.bible.android.view.util.widget.FontWidget
import net.bible.android.view.util.widget.LineSpacingWidget
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.jetbrains.anko.configuration

interface OptionsMenuItemInterface {
    var value: Any
    val visible: Boolean
    val enabled: Boolean
    val inherited: Boolean
    val requiresReload: Boolean
    val isBoolean: Boolean
    val opensDialog: Boolean
    fun handle()
    fun openDialog(activity: Activity, onChanged: ((value: Any) -> Unit)? = null, onReset: (() -> Unit)? = null): Boolean = false
    fun setNonSpecific() {}

    val title: String?
}

abstract class GeneralPreference(
    protected val onlyBibles: Boolean = false,

    val subMenu: Boolean = false,
    override val enabled: Boolean = true
) : OptionsMenuItemInterface {
    override val inherited: Boolean = false
    override val visible: Boolean
        get() = !mainBibleActivity.isMyNotes && if (onlyBibles) mainBibleActivity.documentControl.isBibleBook else true

    override var value: Any = false
    override fun handle() {}
    override val title: String? = null
    override val requiresReload: Boolean = false
    override val opensDialog get()  = !isBoolean
}

abstract class SharedPreferencesPreference(
    private val preferenceName: String,
    private val default: Boolean = false,
    onlyBibles: Boolean = false,
    override val isBoolean: Boolean = true,
    private val isBooleanPreference: Boolean = true,

    // If we are handling non-boolean value
    private val trueValue: String = "true",
    private val falseValue: String = "false",
    private val automaticValue: String = "automatic",
    private val defaultString: String = falseValue,
    subMenu: Boolean = false
) : GeneralPreference(onlyBibles, subMenu) {
    private val preferences = CommonUtils.sharedPreferences
    override val inherited = false

    override var value: Any
        get() = if (isBooleanPreference) {
            preferences.getBoolean(preferenceName, default)
        } else {
            preferences.getString(preferenceName, defaultString) == trueValue
        }
        set(value) = if (isBooleanPreference) {
            preferences.edit().putBoolean(preferenceName, value == true).apply()
        } else {
            preferences.edit().putString(preferenceName, if (value == true) trueValue else falseValue).apply()
        }

    protected open val automatic: Boolean
        get() = if (isBooleanPreference) {
            false
        } else {
            preferences.getString(preferenceName, defaultString) == automaticValue
        }

    override fun handle() {}
}

open class Preference(val settings: SettingsBundle,
                      var type: TextDisplaySettings.Types,
                      onlyBibles: Boolean = false,
                      override val requiresReload: Boolean = true
) : GeneralPreference(onlyBibles) {
    private val actualTextSettings get() = TextDisplaySettings.actual(settings.pageManagerSettings, settings.workspaceSettings)
    private val pageManagerSettings = settings.pageManagerSettings
    private val workspaceSettings = settings.workspaceSettings
    val window = mainBibleActivity.windowRepository.getWindow(settings.windowId)

    protected val default = TextDisplaySettings.default

    override val inherited: Boolean get() = if (window == null) false else pageManagerSettings?.getValue(type) == null
    val pageManager get() = if (window == null) {
        mainBibleActivity.pageControl.currentPageManager
    } else window.pageManager

    override val visible: Boolean
        get() {
            return if (window != null && onlyBibles) pageManager.isBibleShown else true
        }

    override val opensDialog: Boolean = !isBoolean

    override fun setNonSpecific() {
        if(window != null) {
            pageManagerSettings?.setNonSpecific(type)
        } else {
            workspaceSettings.setValue(type, TextDisplaySettings.default.getValue(type))
        }
    }

    override var value
        get() = actualTextSettings.getValue(type)?: TextDisplaySettings.default.getValue(type)!!
        set(value) {
            CommonUtils.displaySettingChanged(type)
            if (window != null) {
                if (workspaceSettings.getValue(type) ?: default.getValue(type) == value)
                    pageManagerSettings!!.setNonSpecific(type)
                else
                    pageManagerSettings!!.setValue(type, value)
            } else {
                workspaceSettings.setValue(type, value)
            }
        }

    override val isBoolean: Boolean get() = value is Boolean

    override fun handle(){
        if(!requiresReload) {
            if(window == null) {
                mainBibleActivity.windowRepository.updateVisibleWindowsTextDisplaySettings()
            } else {
                window.bibleView?.updateTextDisplaySettings()
            }
        } else {
            if (window == null)
                mainBibleActivity.windowControl.windowSync.reloadAllWindows(true)
            else window.updateText()
        }
    }

    override val title: String?
        get() {
            val id = when(type) {
                TextDisplaySettings.Types.STRONGS -> R.string.prefs_show_strongs_title
                TextDisplaySettings.Types.MORPH -> R.string.prefs_show_morphology_title
                TextDisplaySettings.Types.FOOTNOTES -> R.string.prefs_show_notes_title
                TextDisplaySettings.Types.REDLETTERS -> R.string.prefs_red_letter_title
                TextDisplaySettings.Types.SECTIONTITLES -> R.string.prefs_section_title_title
                TextDisplaySettings.Types.VERSENUMBERS -> R.string.prefs_show_verseno_title
                TextDisplaySettings.Types.VERSEPERLINE -> R.string.prefs_verse_per_line_title
                TextDisplaySettings.Types.BOOKMARKS -> R.string.prefs_show_bookmarks_title
                TextDisplaySettings.Types.MYNOTES -> R.string.prefs_show_mynotes_title
                TextDisplaySettings.Types.COLORS -> R.string.prefs_text_colors_menutitle
                TextDisplaySettings.Types.JUSTIFY -> R.string.prefs_justify_title
                TextDisplaySettings.Types.HYPHENATION -> R.string.prefs_hyphenation_title
                TextDisplaySettings.Types.FONT -> R.string.prefs_text_size_title
                TextDisplaySettings.Types.MARGINSIZE -> R.string.prefs_margin_size_title
                TextDisplaySettings.Types.LINE_SPACING -> R.string.line_spacing_title
            }
            return mainBibleActivity.getString(id)
        }
}

class TiltToScrollPreference:
    GeneralPreference() {
    private val wsBehaviorSettings = mainBibleActivity.windowRepository.windowBehaviorSettings
    override fun handle() = mainBibleActivity.invalidateOptionsMenu()
    override val requiresReload = false
    override var value: Any
        get() = wsBehaviorSettings.enableTiltToScroll
        set(value) {
            wsBehaviorSettings.enableTiltToScroll = value == true
        }
    override val visible: Boolean get() = super.visible && PageTiltScrollControl.isTiltSensingPossible
    override val isBoolean = true
}

class CommandPreference(
    private val launch: ((activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?) -> Unit)? = null,
    private val handle: (() -> Unit)? = null,
    override val enabled: Boolean = true,
    override var value: Any = Object(),
    override val visible: Boolean = true,
    override val inherited: Boolean = false,
    override val requiresReload: Boolean = false,
    override val opensDialog: Boolean = false
) : OptionsMenuItemInterface {
    override fun handle() {
        handle?.invoke()
    }
    override fun openDialog(activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        launch?.invoke(activity, onChanged, onReset)
        return true
    }

    override val title: String? = null
    override val isBoolean get() = handle != null && value is Boolean
}

open class SubMenuPreference(onlyBibles: Boolean = false, enabled: Boolean = true, override val visible: Boolean = true) :
    GeneralPreference(onlyBibles = onlyBibles, subMenu = true, enabled = enabled)
{
    override val isBoolean: Boolean = false
}

class NightModePreference : SharedPreferencesPreference("night_mode_pref", false) {
    override fun handle() { mainBibleActivity.refreshIfNightModeChange() }
    override val visible: Boolean get() = super.visible && ScreenSettings.manualMode
}

class StrongsPreference (settings: SettingsBundle) : Preference(settings, TextDisplaySettings.Types.STRONGS) {
    override val enabled: Boolean get() = pageManager.hasStrongs
    override var value get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class MorphologyPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.MORPH) {
    override val enabled: Boolean
        get() {
            val itm = StrongsPreference(settings)
            return itm.enabled && itm.value == true
        }

    override var value: Any
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class FontPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.FONT) {
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_text_size_title)
    override val visible = true
    override fun openDialog(activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        FontWidget.dialog(activity, value as WorkspaceEntities.Font, {
            setNonSpecific()
            onReset?.invoke()
        }) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class LineSpacingPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.LINE_SPACING) {
    private val valueInt get() = (value as Int)
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_line_spacing_pt_title, valueInt.toFloat() / 10)
    override val visible = true
    override fun openDialog(activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        LineSpacingWidget.dialog(activity, valueInt, {
            setNonSpecific()
            onReset?.invoke()
        }) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class ColorPreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.COLORS, requiresReload = false) {
    override val visible = true
    override fun openDialog(activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        val intent = Intent(activity, ColorSettingsActivity::class.java)
        intent.putExtra("settingsBundle", settings.toJson())
        activity.startActivityForResult(intent, COLORS_CHANGED)
        return true
    }
}

class MarginSizePreference(settings: SettingsBundle): Preference(settings, TextDisplaySettings.Types.MARGINSIZE, requiresReload = false) {
    private val leftVal get() = (value as WorkspaceEntities.MarginSize).marginLeft!!
    private val rightVal get() = (value  as WorkspaceEntities.MarginSize).marginRight!!
    // I added this field later (migration 15..16) so to prevent crashes because of null values, need to have this.
    private val maxWidth get() = (value  as WorkspaceEntities.MarginSize).maxWidth ?: defaultVal.maxWidth!!
    private val defaultVal = TextDisplaySettings.default.marginSize!!
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_margin_size_mm_title, leftVal, rightVal, maxWidth)
    override val visible = true
    override fun openDialog(activity: Activity, onChanged: ((value: Any) -> Unit)?, onReset: (() -> Unit)?): Boolean {
        MarginSizeWidget.dialog(activity, value as WorkspaceEntities.MarginSize,
            if(settings.windowId != null) {onReset} else null) {
            value = it
            onChanged?.invoke(it)
        }
        return true
    }
}

class SplitModePreference :
    GeneralPreference() {
    private val wsBehaviorSettings = mainBibleActivity.windowRepository.windowBehaviorSettings
    override fun handle() {
        mainBibleActivity.windowControl.windowSizesChanged()
        ABEventBus.getDefault().post(MainBibleActivity.ConfigurationChanged(mainBibleActivity.configuration))
    }

    override var value: Any
        get() = wsBehaviorSettings.enableReverseSplitMode
        set(value) {
            wsBehaviorSettings.enableReverseSplitMode = value == true
        }

    override val isBoolean = true
    override val visible: Boolean get() = super.visible && mainBibleActivity.windowControl.isMultiWindow
}

class WindowPinningPreference :
    GeneralPreference() {
    private val wsBehaviorSettings = mainBibleActivity.windowRepository.windowBehaviorSettings
    override var value: Any
        get() = !wsBehaviorSettings.autoPin
        set(value) {
            wsBehaviorSettings.autoPin = value == false
        }

    override fun handle() {
        mainBibleActivity.windowControl.autoPinChanged()
    }

    override val isBoolean = true
}

