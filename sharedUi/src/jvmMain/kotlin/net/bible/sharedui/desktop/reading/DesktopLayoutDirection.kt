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
package net.bible.sharedui.desktop.reading

import net.bible.sharedui.assembly.HostStringTableHolder
import net.bible.sharedui.desktop.assembly.DesktopUiLanguage
import org.koin.mp.KoinPlatform

/** The desktop UI layout direction, from the current UI language; it decides which arrow key means "back". */
object DesktopLayoutDirection {
    /** Whether the current UI language (the bound string table, English when nothing is bound) is right to left. */
    fun isRtl(): Boolean =
        isRtl(KoinPlatform.getKoinOrNull()?.getOrNull<HostStringTableHolder>()?.current?.tag ?: "en")

    /** Whether [languageTag] is written right to left. */
    fun isRtl(languageTag: String): Boolean = DesktopUiLanguage.isRtl(languageTag)
}
