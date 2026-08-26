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

package net.bible.sharedcore.settings

/**
 * `iconKey` lives on each row subtype rather than on the [SettingsItem] interface (a [SettingsItem.Category]
 * has no icon in classic either), so a test that wants every row's icon key needs this fan-out.
 */
internal fun SettingsItem.iconKeyOrNull(): String? = when (this) {
    is SettingsItem.Category -> null
    is SettingsItem.SwitchRow -> iconKey
    is SettingsItem.ListChoiceRow -> iconKey
    is SettingsItem.TextInputRow -> iconKey
    is SettingsItem.SliderRow -> iconKey
    is SettingsItem.MultiSelectRow -> iconKey
    is SettingsItem.NavigationRow -> iconKey
    is SettingsItem.InfoRow -> iconKey
}
