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

package net.bible.sharedui.search

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Clears the navigation bar and the display cutout (bottom and both sides) for the reading view's
 * search sheet content and its snackbar host. `BottomSheetScaffold` (material3 1.4.0) applies no
 * window insets of its own and the reading tree is edge-to-edge, so without this the last result and
 * the snackbar sit under a 3-button nav bar, and in landscape the sheet runs under a side one.
 * Union (max), never a sum. `@Composable` because the inset getters are.
 *
 * The measured sheet height that feeds `bottomOffsetForWebView` deliberately does NOT include this
 * padding: the inset ledger adds the nav bar itself, so counting it here would reserve it twice.
 */
@Composable
fun Modifier.readingSheetInsetPadding(): Modifier =
    windowInsetsPadding(
        WindowInsets.navigationBars.union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
    )
