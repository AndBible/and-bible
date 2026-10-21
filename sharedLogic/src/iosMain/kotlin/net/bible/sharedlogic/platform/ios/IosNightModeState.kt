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
package net.bible.sharedlogic.platform.ios

import net.bible.sharedcore.platform.IosDeviceSettings
import net.bible.sharedlogic.platform.NightModeState
import net.bible.sharedlogic.platform.resolveNightMode
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle

/**
 * iOS [NightModeState]: the `night_mode_pref3` mode and the manual `night_mode_pref` Boolean, both in `NSUserDefaults`
 * under Android's key names, resolved by the common [resolveNightMode]. An ABSENT mode is `"system"` (spec H7: neither
 * the reader nor the settings screen writes a default on first launch), so a fresh install follows the system
 * appearance. There is no light sensor on iOS, so `"automatic"` behaves as `"system"` (the settings screen does not
 * offer it: `autoNightModeAvailable` stays false). [systemDark] is read on every access, so the answer is live.
 */
class IosNightModeState(
    private val deviceSettings: IosDeviceSettings,
    private val systemDark: () -> Boolean = ::iosSystemIsDark,
) : NightModeState {
    /** The stored mode, `"system"` when the preference was never written. */
    val mode: String get() = deviceSettings.getString(NIGHT_MODE_MODE_KEY) ?: DEFAULT_MODE

    override val nightMode: Boolean
        get() = resolveNightMode(mode, deviceSettings.getBoolean(NIGHT_MODE_KEY, false), systemDark(), autoReading = null)

    companion object {
        const val NIGHT_MODE_KEY = "night_mode_pref"
        const val NIGHT_MODE_MODE_KEY = "night_mode_pref3"
        const val DEFAULT_MODE = "system"
    }
}

/** True when the device is in dark appearance (the screen's own trait collection, unaffected by any app override). */
fun iosSystemIsDark(): Boolean =
    UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
