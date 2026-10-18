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
package net.bible.sharedlogic.settings

import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.settings.SettingsItem

/** Localised strings the text display settings service needs; the platform formats them with [TextDisplayPrefItems.string]. */
enum class TextDisplayString {
    /** Arg: font size (Int). */
    FONT_SIZE_PT,
    /** Arg: value (Int). */
    VALUE_MM,
    /** Arg: line spacing (Float, already divided by 10). */
    LINE_SPACING_PT,
    /** Args: left, right, max width (Int). */
    MARGIN_SIZE_MM_TITLE,
    TEXT_COLORS_SUMMARY,
    HIDE_LABELS_SUMMARY,
    GLOBAL_TITLE,
    /** Arg: workspace name. */
    WORKSPACE_TITLE,
    /** Arg: one-based window position (Int). */
    WINDOW_TITLE,
    WINDOW_COLOR_TITLE,
    WORKSPACE_COLOR_TITLE,
    BACKGROUND_IMAGE_NONE,
}

/** String-array resources behind the choice rows. */
enum class TextDisplayArray { STRONGS_MODE_ENTRIES, PAGE_SCROLL_AMOUNT_ENTRIES, SCROLL_HELPER_LINE_STYLE_ENTRIES }

/** One text display setting as the platform's preference item exposes it (value with sparse-override semantics). */
interface PrefItemHandle {
    /** Never null: the service only writes concrete values; clearing an override is [setNonSpecific]. */
    var value: Any
    val enabled: Boolean
    val visible: Boolean

    /** Drawable entry name of the setting's icon, or null. */
    val iconKey: String?

    /** Clears the override at this level so the value is inherited again. */
    fun setNonSpecific()
}

/**
 * Everything [net.bible.android.view.activity.settings.TextDisplaySettingsServiceImpl] takes from the platform:
 * the preference items that hold the inheritance math, string resources, fonts, recently-used tracking and the
 * background-image import source. Android implements it over `getPrefItem`; iOS is a pending platform binding.
 */
interface TextDisplayPrefItems {
    fun item(bundle: SettingsBundle, type: TextDisplaySettings.Types): PrefItemHandle
    fun string(key: TextDisplayString, vararg args: Any): String
    fun array(key: TextDisplayArray): List<String>
    val fonts: List<SettingsItem.Choice>

    /** Records [type] as recently used (the overflow menu shortcuts). */
    fun displaySettingChanged(type: TextDisplaySettings.Types)

    /** Source the background-image picker's URI is read through. */
    fun contentSource(): ContentSource

    /** Provided background images, initials to display name. */
    fun backgroundImageNames(): Map<String, String>
}
