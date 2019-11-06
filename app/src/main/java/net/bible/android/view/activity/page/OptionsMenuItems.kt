/*
 * Copyright (c) 2019 Martin Denham, Tuomas Airaksinen and the And Bible contributors.
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

import net.bible.android.control.event.ABEventBus
import net.bible.android.control.page.PageTiltScrollControl
import net.bible.android.view.activity.base.SharedActivityState
import net.bible.android.view.activity.page.MainBibleActivity.Companion.mainBibleActivity
import net.bible.service.common.CommonUtils

interface OptionsMenuItemInterface {
    var value: Boolean
    val visible: Boolean
    val enabled: Boolean
    fun handle()
    fun getTitle(title: CharSequence?): CharSequence? = title
}

abstract class MenuItemPreference(
    private val preferenceName: String,
    private val default: Boolean = false,
    private val onlyBibles: Boolean = false,
    private val isBoolean: Boolean = true,

    // If we are handling non-boolean value
    private val trueValue: String = "true",
    private val falseValue: String = "false",
    private val automaticValue: String = "automatic",
    private val defaultString: String = automaticValue,

    val subMenu: Boolean = false
) : OptionsMenuItemInterface {
    private val preferences = CommonUtils.sharedPreferences
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

    protected val automatic: Boolean
        get() = if (isBoolean) {
            false
        } else {
            preferences.getString(preferenceName, defaultString) == automaticValue
        }

    override val visible: Boolean
        get() = !mainBibleActivity.isMyNotes && if (onlyBibles) mainBibleActivity.documentControl.isBibleBook else true

    override val enabled: Boolean
        get() = true

    override fun handle() {}
}

abstract class StringValuedMenuItemPreference(name: String, default: Boolean,
                                              trueValue: String = "true", falseValue: String = "false") :
    MenuItemPreference(name, default, isBoolean = false, trueValue = trueValue, falseValue = falseValue)

open class TextContentMenuItemPreference(name: String, default: Boolean) :
    MenuItemPreference(name, default, true) {
    override fun handle() = mainBibleActivity.windowControl.windowSync.synchronizeAllScreens()
}

class AutoFullscreenMenuItemPreference :
    MenuItemPreference("auto_fullscreen_pref", true, false) {
    override fun handle() = ABEventBus.getDefault().post(MainBibleActivity.AutoFullScreenChanged(value))
}

class TiltToScrollMenuItemPreference :
    MenuItemPreference("tilt_to_scroll_pref", false, false) {
    override fun handle() = mainBibleActivity.preferenceSettingsChanged()
    override val visible: Boolean get() = super.visible && PageTiltScrollControl.isTiltSensingPossible()
}

class CommandItem(
    val command: () -> Unit,
    override val enabled: Boolean = true,
    override var value: Boolean = true,
    override val visible: Boolean = true
) : OptionsMenuItemInterface {
    override fun handle() {
        command()
    }
}

open class SubMenuMenuItemPreference(onlyBibles: Boolean) :
    MenuItemPreference("none", onlyBibles = onlyBibles, subMenu = true)

class NightModeMenuItemPreference : StringValuedMenuItemPreference("night_mode_pref2", false) {
    override fun handle() = mainBibleActivity.preferenceSettingsChanged()
    override val visible: Boolean get() = super.visible && !automatic
}

class StrongsMenuItemPreference : TextContentMenuItemPreference("show_strongs_pref", true) {
    override fun handle() = mainBibleActivity.windowControl.windowSync.synchronizeAllScreens()
}

class MorphologyMenuItemPreference : TextContentMenuItemPreference("show_morphology_pref", false) {
    override val enabled: Boolean
        get() = StrongsMenuItemPreference().value
    override var value: Boolean
        get() = if (enabled) super.value else false
        set(value) {
            super.value = value
        }

    override fun handle() = mainBibleActivity.windowControl.windowSync.synchronizeAllScreens()
}

class SplitModeMenuItemPreference :
    MenuItemPreference("reverse_split_mode_pref", false) {
    override fun handle() = mainBibleActivity.windowControl.windowSizesChanged()

    override val visible: Boolean get() = super.visible && mainBibleActivity.windowControl.isMultiWindow
}

class WorkspacesSubmenu: SubMenuMenuItemPreference(false) {
    override fun getTitle(title: CharSequence?): CharSequence? {
        return "$title (${SharedActivityState.getCurrentWorkspaceName()})"
    }
}
