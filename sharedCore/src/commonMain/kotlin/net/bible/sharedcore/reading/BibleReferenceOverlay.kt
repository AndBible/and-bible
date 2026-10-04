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

package net.bible.sharedcore.reading

/**
 * Pure visibility gate for the fullscreen bible-reference overlay — the Compose port of classic
 * `SplitBibleArea.updateBibleReferenceOverlay` (`screen/SplitBibleArea.kt:646-671`):
 * `!hide_bible_reference_overlay && fullScreen && activeWindow.pageManager.isBibleShown && show`,
 * where `show` is the same auto-hide flag that drives the floating window buttons.
 */
fun bibleReferenceOverlayVisible(
    fullScreen: Boolean,
    activeIsBibleShown: Boolean,
    buttonsShown: Boolean,
    hideSetting: Boolean,
): Boolean = !hideSetting && fullScreen && activeIsBibleShown && buttonsShown
