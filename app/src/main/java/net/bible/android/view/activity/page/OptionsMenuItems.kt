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

import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.control.page.window.Window
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.jetbrains.anko.configuration

interface OptionsMenuItemInterface {
    var value: Any
    val visible: Boolean
    val enabled: Boolean
    val inherited: Boolean
    val requiresReload: Boolean
    fun handle()
    fun setNonSpecific() {}

    val title: String?
}

abstract class GeneralPreference(
    protected val onlyBibles: Boolean = false,

    val subMenu: Boolean = false
) : OptionsMenuItemInterface {
    override val inherited: Boolean = false
    override val visible: Boolean
        get() = !mainBibleActivity.isMyNotes && if (onlyBibles) mainBibleActivity.documentControl.isBibleBook else true

    override val enabled: Boolean
        get() = true

    override var value: Any = false
    override fun handle() {}
    override val title: String? = null
    override val requiresReload: Boolean = false
}

abstract class SharedPreferencesPreference(
    private val preferenceName: String,
    private val default: Boolean = false,
    onlyBibles: Boolean = false,
    private val isBoolean: Boolean = true,

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
        get() = if (isBoolean) {
            preferences.getBoolean(preferenceName, default)
        } else {
            preferences.getString(preferenceName, defaultString) == trueValue
        }
        set(value) = if (isBoolean) {
            preferences.edit().putBoolean(preferenceName, value == true).apply()
        } else {
            preferences.edit().putString(preferenceName, if (value == true) trueValue else falseValue).apply()
        }

    protected open val automatic: Boolean
        get() = if (isBoolean) {
            false
        } else {
            preferences.getString(preferenceName, defaultString) == automaticValue
        }

    override fun handle() {}
}

abstract class StringValuedPreference(name: String, default: Boolean,
                                      trueValue: String = "true", falseValue: String = "false") :
    SharedPreferencesPreference(name, default, isBoolean = false, trueValue = trueValue, falseValue = falseValue)


open class Preference(val window: Window?, var type: TextDisplaySettings.Types, onlyBibles: Boolean = true) : GeneralPreference(onlyBibles) {
    private val winSettings = window?.pageManager?.textDisplaySettings
    private val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings

    protected val actualTextSettings = window?.pageManager?.actualTextDisplaySettings
        ?: wsTextSettings

    protected val default = TextDisplaySettings.default

    override val inherited: Boolean get() = if (window == null) false else winSettings?.getValue(type) == null
    val pageManager get() = if (window == null) {
        mainBibleActivity.pageControl.currentPageManager
    } else window.pageManager

    override val visible: Boolean
        get() {
            return if (onlyBibles) pageManager.isBibleShown else true
        }

    override fun setNonSpecific() {
        winSettings?.setNonSpecific(type)
    }

    override val requiresReload get() = value is Boolean

    override var value
        get() = actualTextSettings.getValue(type)?: TextDisplaySettings.default.getValue(type)!!
        set(value) {
            if (window != null) {
                if (wsTextSettings.getValue(type) ?: default.getValue(type) == value)
                    winSettings!!.setNonSpecific(type)
                else
                    winSettings!!.setValue(type, value)
            } else {
                wsTextSettings.setValue(type, value)
            }
        }
}

class TiltToScrollPreference:
    GeneralPreference() {
    private val wsBehaviorSettings = mainBibleActivity.windowRepository.windowBehaviorSettings
    override fun handle() = mainBibleActivity.preferenceSettingsChanged()
    override val requiresReload = false
    override var value: Any
        get() = wsBehaviorSettings.enableTiltToScroll
        set(value) {
            wsBehaviorSettings.enableTiltToScroll = value == true
        }
    override val visible: Boolean get() = super.visible && PageTiltScrollControl.isTiltSensingPossible
}

class CommandPreference(
    val command: () -> Unit,
    override val enabled: Boolean = true,
    override var value: Any = true,
    override val visible: Boolean = true,
    override val inherited: Boolean = false,
    override val requiresReload: Boolean = false
) : OptionsMenuItemInterface {
    override fun handle() {
        command.invoke()
    }
    override val title: String? = null
}

open class SubMenuPreference(onlyBibles: Boolean = false) :
    GeneralPreference(onlyBibles = onlyBibles, subMenu = true)

class NightModePreference : StringValuedPreference("night_mode_pref2", false) {
    override fun handle() { mainBibleActivity.refreshIfNightModeChange() }
    override val visible: Boolean get() = super.visible && !automatic && !ScreenSettings.systemModeAvailable
    override val automatic get() = super.automatic && ScreenSettings.autoModeAvailable

}

class StrongsPreference (window: Window?) : Preference(window, TextDisplaySettings.Types.STRONGS) {
    override val enabled: Boolean get() = pageManager.hasStrongs
    override var value get() = if (enabled) super.value else false
        set(value) {
            if(value == false) {
                MorphologyPreference(window).value = false
            }
            super.value = value
        }
}

class MorphologyPreference(window: Window?): Preference(window, TextDisplaySettings.Types.MORPH) {
    override val enabled: Boolean
        get() {
            val itm = StrongsPreference(window)
            return itm.enabled && itm.value == true
        }

    override var value: Any
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class FontSizePreference(window: Window?): Preference(window, TextDisplaySettings.Types.FONTSIZE) {
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_text_size_pt_title, value as Int)
    override val visible = true
}


class ColorPreference(window: Window?): Preference(window, TextDisplaySettings.Types.COLORS) {
    override val visible = true

}


class MarginSizePreference(window: Window?): Preference(window, TextDisplaySettings.Types.MARGINSIZE) {
    private val leftVal get() = (value as WorkspaceEntities.MarginSize).marginLeft!!
    private val rightVal get() = (value  as WorkspaceEntities.MarginSize).marginRight!!
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_margin_size_mm_title, leftVal, rightVal)
    override val visible = true
}

open class WorkspacePreference(var type: TextDisplaySettings.Types):
    GeneralPreference() {
    protected val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings

    val def = WorkspaceEntities.TextDisplaySettings.default

    override var value get() = (wsTextSettings.getValue(type)?: def.getValue(type)!!)
        set(value) {
            wsTextSettings.setValue(type, value)
            mainBibleActivity.windowRepository.updateWindowTextDisplaySettingsValues(type, value)
        }

    override fun handle() = mainBibleActivity.windowControl.windowSync.reloadAllWindows(true)
    override val inherited = false
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

    override val visible: Boolean get() = super.visible && mainBibleActivity.windowControl.isMultiWindow
}

class WorkspacesSubmenu(override val title: String?): SubMenuPreference()
