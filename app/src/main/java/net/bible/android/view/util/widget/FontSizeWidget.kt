/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.util.widget

import net.bible.service.common.AndBibleAddons
import net.bible.service.common.ProvidedFont
import java.util.*

/**
 * Platform-dialog removal Task 10: this file used to also hold `FontSizeWidget`/`TopMarginWidget`/
 * `FontFamilyWidget` -- three `LinearLayout`s each with a `companion object.dialog()` that built a
 * native `AlertDialog.Builder` around itself (`FontSizePreference`/`TopMarginPreference`/
 * `FontFamilyPreference.openDialog` in `OptionsMenuItems.kt`). All three are deleted along with
 * their callers: those three text display settings are sheet-editable
 * ([net.bible.sharedcore.settings.textSettingEditorPageFor] resolves each to a `Row` page), and the
 * reading view's two menus (`OptionsMenuStateBuilder.dispatch`, `ReadingCommands
 * .handleWindowTextOptionItem`) only ever fell through to `openDialog` for them when no host was
 * mounted -- unreachable in production since slice 8 made NavHost the only reading host. `FontAdapter`
 * and `getTypeFace` are gone too: both existed only to drive those three widgets' live font preview.
 *
 * [FontDefinition]/[availableFonts] survive: `TextDisplaySettingsServiceImpl.fontFamilyEntries`
 * still uses [availableFonts] to build the sheet's FONTFAMILY choice list.
 */
class FontDefinition(val providedFont: ProvidedFont? = null, val fontFamily: String? = null){
    val realFontFamily: String get() = fontFamily?: providedFont!!.name
    val name: String get() = fontFamily?.replace("-", " ")?.capitalize(Locale.getDefault()) ?: providedFont!!.name

    override fun toString(): String {
        return name
    }
}

val availableFonts:Array<FontDefinition> get() {
    val standard = arrayOf (
        "sans-serif-thin",
        "sans-serif-light",
        "sans-serif",
        "sans-serif-medium",
        "sans-serif-black",
        "sans-serif-condensed-light",
        "sans-serif-condensed",
        "sans-serif-condensed-medium",
        "sans-serif-condensed",
        "serif",
        "monospace",
        "serif-monospace",
        "casual",
        "cursive",
        "sans-serif-smallcaps"
    )
    return AndBibleAddons.providedFonts.values.map { FontDefinition(providedFont = it) }.toTypedArray() + standard.map { FontDefinition(fontFamily = it) }
}
