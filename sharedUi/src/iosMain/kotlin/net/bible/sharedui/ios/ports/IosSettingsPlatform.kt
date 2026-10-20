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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.ios.ports

import net.bible.sharedcore.platform.CoreStrings
import net.bible.sharedui.app.ports.SettingsPlatform
import net.bible.android.view.activity.settings.SettingsReset
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.settings.DeviceLocalPrefs
import net.bible.sharedcore.settings.SettingsPlatformFlags

/** Uses the shared reset contract, including the original device-local exclusions. */
@kotlin.native.HiddenFromObjC
class IosSettingsPlatform(
    private val strings: CoreStrings,
    private val settings: AppSettings,
    private val devicePrefs: DeviceLocalPrefs,
) : SettingsPlatform, SettingsPlatformFlags {
    override fun resetAllSettings() = SettingsReset.performReset(settings, devicePrefs)
    override val sdcardPermissionVisible = false
    override val openLinksVisible = false
    override val debugToolsVisible = false
    override val autoNightModeAvailable = false
    override val isOnyx = false
    override fun onSystemBarSettingChanged() = Unit
    override val isDiscreteBuild: Boolean = false
    override val confirmText: String get() = strings.okay
    override val dismissText: String get() = strings.cancel
}
