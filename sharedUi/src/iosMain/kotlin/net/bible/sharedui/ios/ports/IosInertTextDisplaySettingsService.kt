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
package net.bible.sharedui.ios.ports

import net.bible.sharedcore.settings.BackgroundImageOption
import net.bible.sharedcore.settings.ColorField
import net.bible.sharedcore.settings.ColorsSnapshot
import net.bible.sharedcore.settings.InheritedFrom
import net.bible.sharedcore.settings.SettingsScope
import net.bible.sharedcore.settings.TextDisplaySettingsService
import net.bible.sharedcore.settings.TextSettingType
import net.bible.sharedcore.settings.TextSettingValue
import net.bible.sharedcore.settings.TextSettingsSnapshot

/** Pending stand-in (stream H): renders no rows, edits nothing, touches no settings storage. */
internal class IosInertTextDisplaySettingsService : TextDisplaySettingsService {
    override fun loadText(scope: SettingsScope) = TextSettingsSnapshot(
        scope = scope, screenTitle = "", workspaceName = "", rows = emptyMap(),
        showParentCategory = false, showWorkspaceLink = false, showGlobalLink = false,
    )
    override fun setValue(scope: SettingsScope, type: TextSettingType, value: TextSettingValue) {}
    override fun revert(scope: SettingsScope, type: TextSettingType) {}
    override fun reset(scope: SettingsScope) {}
    override fun loadColors(scope: SettingsScope) = ColorsSnapshot(
        title = "", dayTextColor = 0xFF000000.toInt(), dayBackground = 0xFFFFFFFF.toInt(), dayNoise = 0,
        nightTextColor = 0xFFFFFFFF.toInt(), nightBackground = 0xFF000000.toInt(), nightNoise = 0,
        workspaceColor = 0xFF444444.toInt(), workspaceColorVisible = false,
        dayBackgroundImageInitials = null, dayBackgroundImageName = "", dayBackgroundImageOpacity = 0,
        nightBackgroundImageInitials = null, nightBackgroundImageName = "", nightBackgroundImageOpacity = 0,
        inheritedFrom = InheritedFrom.NONE,
    )
    override fun loadBackgroundOptions(): List<BackgroundImageOption> = emptyList()
    override fun setColor(scope: SettingsScope, field: ColorField, argb: Int) {}
    override fun setNoise(scope: SettingsScope, night: Boolean, value: Int) {}
    override fun setWorkspaceColor(scope: SettingsScope, argb: Int) {}
    override fun clearWorkspaceColor(scope: SettingsScope) {}
    override fun setBackgroundImage(scope: SettingsScope, night: Boolean, initials: String?) {}
    override fun setBackgroundOpacity(scope: SettingsScope, night: Boolean, opacity: Int) {}
    override fun resetColors(scope: SettingsScope) {}
    override suspend fun importBackgroundImage(picker: suspend () -> String?): BackgroundImageOption? = null
    override fun deleteBackgroundImage(initials: String) {}
}
