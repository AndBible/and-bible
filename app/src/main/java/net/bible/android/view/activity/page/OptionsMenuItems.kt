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
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.android.view.util.widget.TextSizeWidget
import net.bible.service.common.CommonUtils
import net.bible.service.device.ScreenSettings
import org.jetbrains.anko.configuration
import org.w3c.dom.Text

interface OptionsMenuItemInterface {
    var value: Boolean
    val visible: Boolean
    val enabled: Boolean
    val inherited: Boolean
    fun handle()
    fun getTitle(title: CharSequence?): CharSequence? = title
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

    override var value: Boolean
        get() = if (isBoolean) {
            preferences.getBoolean(preferenceName, default)
        } else {
            preferences.getString(preferenceName, defaultString) == trueValue
        }
        set(value) = if (isBoolean) {
            preferences.edit().putBoolean(preferenceName, value).apply()
        } else {
            preferences.edit().putString(preferenceName, if (value) trueValue else falseValue).apply()
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


open class WorkspaceTextContentMenuItemPreference(var type: TextDisplaySettings.Id) :
    GeneralMenuItemPreference() {
    private val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings

    val def = WorkspaceEntities.TextDisplaySettings.default

    override var value: Boolean get() = wsTextSettings.getBooleanValue(type)?: def.getBooleanValue(type)!!
        set(value) {
            wsTextSettings.setBooleanValue(type, value)
            mainBibleActivity.windowRepository.updateWindowTextDisplaySettings(type, value)
        }

    override fun handle() = mainBibleActivity.windowControl.windowSync.reloadAllWindows(true)
    override val inherited = false
}

open class WindowTextContentMenuItemPreference(val window: Window, var type: TextDisplaySettings.Id) :
    GeneralMenuItemPreference(true) {
    private val textSettings = window.pageManager.textDisplaySettings
    private val actualTextSettings = window.pageManager.actualTextDisplaySettings
    private val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings
    private val default = TextDisplaySettings.default

    override fun handle() = window.updateText()
    override var value: Boolean get() = actualTextSettings.getBooleanValue(type)!!
        set(value) {
            if(wsTextSettings.getBooleanValue(type)?: default.getBooleanValue(type) == value)
                textSettings.setBooleanValue(type, null)
            else
                textSettings.setBooleanValue(type, value)

        }
    override val inherited: Boolean get () = textSettings.getBooleanValue(type) == null
    override val visible: Boolean
        get() = if (onlyBibles) window.pageManager.isBibleShown else true
}


class TiltToScrollMenuItemPreference :
    GeneralMenuItemPreference() {
    private val wsBehaviorSettings = mainBibleActivity.windowRepository.windowBehaviorSettings
    override fun handle() = mainBibleActivity.preferenceSettingsChanged()
    override var value: Boolean
        get() = wsBehaviorSettings.enableTiltToScroll
        set(value) {
            wsBehaviorSettings.enableTiltToScroll = value
        }
    override val visible: Boolean get() = super.visible && PageTiltScrollControl.isTiltSensingPossible
}

class CommandItem(
    val command: () -> Unit,
    override val enabled: Boolean = true,
    override var value: Boolean = true,
    override val visible: Boolean = true,
    override val inherited: Boolean = false
) : OptionsMenuItemInterface {
    override fun handle() {
        command.invoke()
    }
}

open class SubMenuMenuItemPreference(onlyBibles: Boolean) :
    MenuItemPreference("none", onlyBibles = onlyBibles, subMenu = true)

class NightModeMenuItemPreference : StringValuedMenuItemPreference("night_mode_pref2", false) {
    override fun handle() { mainBibleActivity.refreshIfNightModeChange() }
    override val visible: Boolean get() = super.visible && !automatic && !ScreenSettings.systemModeAvailable
    override val automatic get() = super.automatic && ScreenSettings.autoModeAvailable

}

class WindowStrongsMenuItemPreference (window: Window) : WindowTextContentMenuItemPreference(window, TextDisplaySettings.Id.STRONGS) {
    override val enabled: Boolean get() = window.pageManager.hasStrongs
}

class WorkspaceStrongsMenuItemPreference: WorkspaceTextContentMenuItemPreference(TextDisplaySettings.Id.STRONGS)

class WindowMorphologyMenuItemPreference(window: Window): WindowTextContentMenuItemPreference(window, TextDisplaySettings.Id.MORPH) {
    override val enabled: Boolean
        get() = WindowStrongsMenuItemPreference(window).value

    override var value: Boolean
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }
}

class WindowFontSizeItem(val window: Window): GeneralMenuItemPreference() {
    private val wsTextSettings = mainBibleActivity.windowRepository.textDisplaySettings
    private val winTextSettings = window.pageManager.textDisplaySettings
    private val default = TextDisplaySettings.default
    private val actualTextSettings = window.pageManager.actualTextDisplaySettings
    override fun getTitle(title: CharSequence?): CharSequence = mainBibleActivity.getString(R.string.prefs_text_size_menuitem, actualTextSettings.fontSize!!)
    override fun handle() {
        TextSizeWidget.changeTextSize(mainBibleActivity, actualTextSettings.fontSize!!, {
            winTextSettings.fontSize = null
            window.bibleView?.applyPreferenceSettings()
        }) {
            if(it == wsTextSettings.fontSize?: default.fontSize) {
                winTextSettings.fontSize = null
            } else {
                winTextSettings.fontSize = it
            }
            window.bibleView?.applyPreferenceSettings()
        }
    }

    override var value = true
    override val inherited = window.pageManager.textDisplaySettings.fontSize == null
}

class WorkspaceFontSizeItem: GeneralMenuItemPreference() {
    override fun getTitle(title: CharSequence?): CharSequence = mainBibleActivity.getString(R.string.prefs_text_size_menuitem, fontSize)
    private val fontSize = mainBibleActivity.windowRepository.textDisplaySettings.fontSize?: TextDisplaySettings.default.fontSize!!
    override fun handle() {
        TextSizeWidget.changeTextSize(mainBibleActivity, fontSize) {
            mainBibleActivity.windowRepository.textDisplaySettings.fontSize = it
            mainBibleActivity.preferenceSettingsChanged()
            mainBibleActivity.windowRepository.updateWindowFontSizes(it)
        }
    }
    override var value = true
}

class WorkspaceMorphologyMenuItemPreference: WorkspaceTextContentMenuItemPreference(TextDisplaySettings.Id.MORPH) {
    override val enabled: Boolean
        get() = WorkspaceStrongsMenuItemPreference().value

    override var value: Boolean
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

    override var value: Boolean
        get() = wsBehaviorSettings.enableReverseSplitMode
        set(value) {
            wsBehaviorSettings.enableReverseSplitMode = value
        }

    override val visible: Boolean get() = super.visible && mainBibleActivity.windowControl.isMultiWindow
}

class WorkspacesSubmenu: SubMenuMenuItemPreference(false) {
    override fun getTitle(title: CharSequence?): CharSequence? {
        return "$title (${SharedActivityState.currentWorkspaceName})"
    }
}
