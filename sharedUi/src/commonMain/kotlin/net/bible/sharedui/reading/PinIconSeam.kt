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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * Host seam supplying the "window is pinned" glyph as a [Painter] — classic's `ic_pin`
 * (`res/layout/window_button.xml:97-107`).
 *
 * Kept portable in commonMain (NO Android `R.drawable`) so [WindowButton] compiles on every
 * target; the Android host provides the bespoke vector via `painterResource` in
 * `:app`'s `ProvideAppLocals`, and iOS can supply its own art. Defaults to `error(...)` — the same
 * choice as [net.bible.sharedui.navigation.LocalCategoryIcon] — so a composable rendered without a
 * provider fails loudly at render time instead of silently drawing nothing where a state indicator
 * belongs.
 */
val LocalPinIcon = staticCompositionLocalOf<@Composable () -> Painter> {
    error("No LocalPinIcon provided — wrap content in ProvideAppLocals (or the golden harness).")
}
