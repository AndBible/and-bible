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
package net.bible.android.platform

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.SettingsBundle
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings
import net.bible.android.view.activity.page.OptionsMenuItemInterface
import net.bible.android.view.activity.settings.getPrefItem
import net.bible.android.view.util.widget.availableFonts
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.sword.ContentSource
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedlogic.settings.PrefItemHandle
import net.bible.sharedlogic.settings.TextDisplayArray
import net.bible.sharedlogic.settings.TextDisplayPrefItems
import net.bible.sharedlogic.settings.TextDisplayString

/** Android [TextDisplayPrefItems]: delegates to the classic [getPrefItem] items and string resources. */
class AndroidTextDisplayPrefItems : TextDisplayPrefItems {
    private val app get() = BibleApplication.application

    private class Handle(private val item: OptionsMenuItemInterface) : PrefItemHandle {
        override var value: Any?
            get() = item.value
            set(v) { item.value = v!! }
        override val enabled get() = item.enabled
        override val visible get() = item.visible
        override val iconKey: String?
            get() = item.icon?.let { BibleApplication.application.resources.getResourceEntryName(it) }
        override fun setNonSpecific() = item.setNonSpecific()
    }

    override fun item(bundle: SettingsBundle, type: TextDisplaySettings.Types): PrefItemHandle =
        Handle(getPrefItem(bundle, type))

    override fun string(key: TextDisplayString, vararg args: Any): String =
        app.getString(
            when (key) {
                TextDisplayString.FONT_SIZE_PT -> R.string.font_size_pt
                TextDisplayString.VALUE_MM -> R.string.value_mm
                TextDisplayString.LINE_SPACING_PT -> R.string.prefs_line_spacing_pt
                TextDisplayString.MARGIN_SIZE_MM_TITLE -> R.string.prefs_margin_size_mm_title
                TextDisplayString.TEXT_COLORS_SUMMARY -> R.string.prefs_text_colors_summary
                TextDisplayString.HIDE_LABELS_SUMMARY -> R.string.bookmark_settings_hide_labels_summary
                TextDisplayString.GLOBAL_TITLE -> R.string.global_text_display_settings_title
                TextDisplayString.WORKSPACE_TITLE -> R.string.workspace_text_display_settings_title
                TextDisplayString.WINDOW_TITLE -> R.string.window_text_display_settings_title
                TextDisplayString.WINDOW_COLOR_TITLE -> R.string.window_color_settings_title
                TextDisplayString.WORKSPACE_COLOR_TITLE -> R.string.workspace_color_settings_title
                TextDisplayString.BACKGROUND_IMAGE_NONE -> R.string.background_image_none
            },
            *args,
        )

    override fun array(key: TextDisplayArray): List<String> =
        app.resources.getStringArray(
            when (key) {
                TextDisplayArray.STRONGS_MODE_ENTRIES -> R.array.strongsModeEntries
                TextDisplayArray.PAGE_SCROLL_AMOUNT_ENTRIES -> R.array.pageScrollAmountEntries
                TextDisplayArray.SCROLL_HELPER_LINE_STYLE_ENTRIES -> R.array.scrollHelperLineStyleEntries
            }
        ).toList()

    override val fonts: List<SettingsItem.Choice>
        get() = availableFonts.map { SettingsItem.Choice(it.realFontFamily, it.name) }

    override fun displaySettingChanged(type: TextDisplaySettings.Types) = CommonUtils.displaySettingChanged(type)

    override fun contentSource(): ContentSource = AndroidContentSource(app.contentResolver)

    override fun backgroundImageNames(): Map<String, String> =
        AndBibleAddons.providedBackgroundImages.mapValues { it.value.name }
}
