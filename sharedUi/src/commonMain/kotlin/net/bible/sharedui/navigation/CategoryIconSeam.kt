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
package net.bible.sharedui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import net.bible.sharedcore.navigation.DocCategory

/**
 * Host seam supplying the leading category icon for a document row as a [Painter].
 *
 * Kept portable in commonMain (NO Android `R.drawable`) so [DocumentRow] compiles on every target.
 * The Android host provides the classic bespoke vector drawables (`ic_bible_24dp`, `ic_commentary`,
 * …) via `painterResource` — see `:app`'s `ProvideAppLocals`; iOS can supply its own art. Defaults
 * to `error(...)` (like `LocalStrings`) so a composable rendered without a provider fails loudly at
 * render time rather than drawing a blank/wrong icon.
 */
val LocalCategoryIcon = staticCompositionLocalOf<@Composable (DocCategory) -> Painter> {
    error("No LocalCategoryIcon provided — wrap content in ProvideAppLocals (or the golden harness).")
}
