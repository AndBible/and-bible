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

package net.bible.android.view.activity.page.screen

import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.WorkspaceEntities.TextDisplaySettings

/**
 * The page background of a window as an ARGB int: Android's `bibleViewBackgroundColorFor` without its platform default.
 * Monochrome mode forces black (night) or white (day); otherwise the window's actual colour settings decide.
 * Null when nothing names a colour: the pane then keeps the transparent background it has.
 */
@kotlin.native.HiddenFromObjC
fun paneBackgroundArgb(colors: WorkspaceEntities.Colors?, nightMode: Boolean, monochrome: Boolean): Int? {
    val night = if (monochrome) TextDisplaySettings.black else colors?.nightBackground
    val day = if (monochrome) TextDisplaySettings.white else colors?.dayBackground
    return if (nightMode) night else day
}

/** The default page background at night (Android `R.color.bible_background_night`): black. */
const val READING_BACKGROUND_NIGHT: Int = -16777216 // 0xFF000000

/** The default page background by day (Android `R.color.bible_background_day`): white. */
const val READING_BACKGROUND_DAY: Int = -1 // 0xFFFFFFFF

/**
 * The page background of a window as an ARGB int, with the platform defaults: [paneBackgroundArgb], and where it names
 * no colour [READING_BACKGROUND_NIGHT] / [READING_BACKGROUND_DAY]. The single rule behind Android's
 * `bibleViewBackgroundColorFor` and the iOS WKWebView background (BibleView-JS does not paint the page itself).
 */
@kotlin.native.HiddenFromObjC
fun readingBackgroundArgb(colors: WorkspaceEntities.Colors?, nightMode: Boolean, monochrome: Boolean): Int =
    paneBackgroundArgb(colors, nightMode, monochrome)
        ?: if (nightMode) READING_BACKGROUND_NIGHT else READING_BACKGROUND_DAY
