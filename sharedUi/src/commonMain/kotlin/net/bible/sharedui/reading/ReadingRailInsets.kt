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

package net.bible.sharedui.reading

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The window-buttons strip's inset padding (fix batch 2, F66/F67). Only the nav bar's BOTTOM inset,
 * only when the strip is the bottom-most surface ([applyNavBarInset] =
 * `railOwnsNavBarInset(...)`), and never the part the IME already covers (the reading column is
 * padded by `max(bars, ime)` while the keyboard is up). Side insets belong to the split, which the
 * strip sits inside. Shared by the host and `ReadingInsetOwnershipTest` so the tested padding is the
 * shipped one.
 *
 * `@Composable` because `WindowInsets.navigationBars`/`ime` are composable getters.
 */
@Composable
fun Modifier.readingRailInsetPadding(applyNavBarInset: Boolean): Modifier =
    if (applyNavBarInset) {
        windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime).only(WindowInsetsSides.Bottom))
    } else {
        this
    }
