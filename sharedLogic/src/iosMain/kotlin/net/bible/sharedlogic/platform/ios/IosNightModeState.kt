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

/**
 * iOS [NightModeState]: the manual night-mode preference, the key Android's `ScreenSettings.nightMode` reads in its
 * manual branch (`night_mode_pref`, default false). Android's light-sensor and system-mode branches have no iOS
 * counterpart yet: following the system appearance is phase 2.
 */
class IosNightModeState(private val deviceSettings: IosDeviceSettings) : NightModeState {
    override val nightMode: Boolean get() = deviceSettings.getBoolean(NIGHT_MODE_KEY, false)

    private companion object {
        const val NIGHT_MODE_KEY = "night_mode_pref"
    }
}
