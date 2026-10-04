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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The nav bar's BOTTOM inset as padding, for whichever bottom surface owns it (the rail, the Speak
 * bar, the agent panel). [applyNavBarInset] is decided by `ReadingViewScreen`: the surface is the
 * bottom-most one and the reading column carries no IME padding. It follows that decision, not
 * Compose's own `WindowInsets.ime`, which can be non-zero while the column applies no IME padding.
 * Shared by the shipped bars and `ReadingInsetOwnershipTest`.
 */
@Composable
fun Modifier.readingRailInsetPadding(applyNavBarInset: Boolean): Modifier =
    if (applyNavBarInset) {
        windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
    } else {
        this
    }
