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
import net.bible.android.view.util.widget.MarginSizeWidget
import net.bible.android.view.util.widget.TextSizeWidget
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.jetbrains.anko.configuration

interface OptionsMenuItemInterface {
    var value: Any
    val visible: Boolean
    val enabled: Boolean
    val inherited: Boolean
    fun handle()
    val title: String?
}

abstract class GeneralMenuItemPreference(
    protected val onlyBibles: Boolean = false,

    val subMenu: Boolean = false
) : OptionsMenuItemInterface {
    override val inherited: Boolean = false
    override val visible: Boolean
        get() = !mainBibleActivity.isMyNotes && if (onlyBibles) mainBibleActivity.documentControl.isBibleBook else true

    override val enabled: Boolean
        get() = true

    override fun handle() {}
    override val title: String? = null
}

abstract class MenuItemPreference(
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
) : GeneralMenuItemPreference(onlyBibles, subMenu), OptionsMenuItemInterface {
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

abstract class StringValuedMenuItemPreference(name: String, default: Boolean,
                                              trueValue: String = "true", falseValue: String = "false") :
    MenuItemPreference(name, default, isBoolean = false, trueValue = trueValue, falseValue = falseValue)


open class WindowMenuItemPreference(val window: Window, var type: TextDisplaySettings.Types) :
    GeneralMenuItemPreference(true) {
    protected val textSettings = window.pageManager.textDisplaySettings
    protected val actualTextSettings = window.pageManager.actualTextDisplaySettings
    protected val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings
    protected val default = TextDisplaySettings.default

    override fun handle() = window.updateText()
    override var value get() = actualTextSettings.getValue(type)!!
        set(value) {
            if(wsTextSettings.getValue(type)?: default.getValue(type) == value)
                textSettings.setValue(type, null)
            else
                textSettings.setValue(type, value)

        }
    override val inherited: Boolean get () = textSettings.getValue(type) == null
    override val visible: Boolean
        get() = if (onlyBibles) window.pageManager.isBibleShown else true
}

open class WindowIntegerMenuItemPreference(window: Window, type: TextDisplaySettings.Types): WindowMenuItemPreference(window, type) {
    protected val intValue: Int? get() = actualTextSettings.getValue(type) as Int?

    fun setValue(value: Int) {
        this.value = value
        window.bibleView?.applyPreferenceSettings()
    }
    fun resetValue() {
        textSettings.setValue(type, null)
        window.bibleView?.applyPreferenceSettings()
    }
}


class TiltToScrollMenuItemPreference :
    GeneralMenuItemPreference() {
    private val wsBehaviorSettings = mainBibleActivity.windowRepository.windowBehaviorSettings
    override fun handle() = mainBibleActivity.preferenceSettingsChanged()
    override var value: Any
        get() = wsBehaviorSettings.enableTiltToScroll
        set(value) {
            wsBehaviorSettings.enableTiltToScroll = value == true
        }
    override val visible: Boolean get() = super.visible && PageTiltScrollControl.isTiltSensingPossible
}

class CommandItem(
    val command: () -> Unit,
    override val enabled: Boolean = true,
    override var value: Any = true,
    override val visible: Boolean = true,
    override val inherited: Boolean = false
) : OptionsMenuItemInterface {
    override fun handle() {
        command.invoke()
    }
    override val title: String? = null
}

open class SubMenuMenuItemPreference(onlyBibles: Boolean) :
    MenuItemPreference("none", onlyBibles = onlyBibles, subMenu = true)

class NightModeMenuItemPreference : StringValuedMenuItemPreference("night_mode_pref2", false) {
    override fun handle() { mainBibleActivity.refreshIfNightModeChange() }
    override val visible: Boolean get() = super.visible && !automatic && !ScreenSettings.systemModeAvailable
    override val automatic get() = super.automatic && ScreenSettings.autoModeAvailable

}

class WindowStrongsMenuItemPreference (window: Window) : WindowMenuItemPreference(window, TextDisplaySettings.Types.STRONGS) {
    override val enabled: Boolean get() = window.pageManager.hasStrongs
}

class WorkspaceStrongsMenuItemPreference: WorkspaceMenuItemPreference(TextDisplaySettings.Types.STRONGS)

class WindowMorphologyMenuItemPreference(window: Window): WindowMenuItemPreference(window, TextDisplaySettings.Types.MORPH) {
    override val enabled: Boolean
        get() = WindowStrongsMenuItemPreference(window).value == true

    override var value: Any
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class WindowFontSizePreference(window: Window): WindowIntegerMenuItemPreference(window, TextDisplaySettings.Types.FONTSIZE) {
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_text_size_menuitem, intValue)
    override fun handle() {
        TextSizeWidget.changeTextSize(mainBibleActivity, intValue!!, {resetValue()}, {setValue(it)})
    }
}

class WindowMarginSizePreference(window: Window): WindowIntegerMenuItemPreference(window, TextDisplaySettings.Types.MARGINSIZE) {
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_margin_size_menuitem, intValue)
    override fun handle() {
        MarginSizeWidget.changeTextSize(mainBibleActivity, intValue!!, {
            resetValue()
            window.bibleView?.updateTextDisplaySettings()
        }, {
            setValue(it)
            window.bibleView?.updateTextDisplaySettings()
        })
    }
}

open class WorkspaceMenuItemPreference(var type: TextDisplaySettings.Types):
    GeneralMenuItemPreference() {
    private val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings

    val def = WorkspaceEntities.TextDisplaySettings.default

    override var value get() = (wsTextSettings.getValue(type)?: def.getValue(type)!!)
        set(value) {
            wsTextSettings.setValue(type, value)
            mainBibleActivity.windowRepository.updateWindowTextDisplaySettingsValues(type, value)
        }

    override fun handle() = mainBibleActivity.windowControl.windowSync.reloadAllWindows(true)
    override val inherited = false
}

open class WorkspaceIntegerMenuItemPreference(type: TextDisplaySettings.Types): WorkspaceMenuItemPreference(type) {
    protected val intValue = (mainBibleActivity.windowRepository.textDisplaySettings.getValue(type)?: TextDisplaySettings.default.getValue(type)!!) as Int

    fun setValue(value: Int) {
        this.value = value
        mainBibleActivity.windowRepository.updateWindowTextDisplaySettingsValues(type, value)
        mainBibleActivity.preferenceSettingsChanged()
    }
    override var value: Any = true
}

class WorkspaceFontSizePreference: WorkspaceIntegerMenuItemPreference(TextDisplaySettings.Types.FONTSIZE) {
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_text_size_menuitem, intValue)
    override fun handle() {
        TextSizeWidget.changeTextSize(mainBibleActivity, intValue) {setValue(it)}
    }
}

class WorkspaceMarginSizePreference: WorkspaceIntegerMenuItemPreference(TextDisplaySettings.Types.MARGINSIZE) {
    override val title: String get() = mainBibleActivity.getString(R.string.prefs_margin_size_menuitem, intValue)
    override fun handle() {
        MarginSizeWidget.changeTextSize(mainBibleActivity, intValue) {setValue(it)}
    }
}

class WorkspaceMorphologyMenuItemPreference: WorkspaceMenuItemPreference(TextDisplaySettings.Types.MORPH) {
    override val enabled: Boolean
        get() = WorkspaceStrongsMenuItemPreference().value == true

    override var value: Any
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class SplitModeMenuItemPreference :
    GeneralMenuItemPreference() {
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

class WorkspacesSubmenu(override val title: String?): SubMenuMenuItemPreference(false)
