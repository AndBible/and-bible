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

package net.bible.sharedui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Host seam letting a Compose surface tell the platform's system bars what colour it is drawing
 * (A/B batch 3, F1) — the same "host resolves, `:sharedUi` stays platform-free" shape as
 * [net.bible.sharedui.settings.LocalSettingsIcon] and `LocalCategoryIcon`.
 *
 * The default is a **no-op**, so iOS and the Roborazzi golden harness (whose context is not an
 * `ActivityBase`) are unaffected. `:app`'s `ProvideAppLocals` provides the real implementation.
 *
 * @param container the colour the caller paints directly under the status bar.
 * @param fillWindowBackground `true` when the caller does NOT itself paint behind the system bars
 *   and needs the host to fill the strip (the `AbScaffold` screens); `false` when it does (the
 *   reading toolbar, which extends its own background under the status-bar inset).
 */
val LocalSystemBarSync = staticCompositionLocalOf<(container: Color, fillWindowBackground: Boolean) -> Unit> {
    { _, _ -> }
}

/**
 * Calls [LocalSystemBarSync] in a [SideEffect] — i.e. after a successful composition, never during
 * it, since the implementation mutates the Android window. Safe to call on every recomposition: the
 * implementation is idempotent for an unchanged colour.
 */
@Composable
fun SyncSystemBars(container: Color, fillWindowBackground: Boolean) {
    val sync = LocalSystemBarSync.current
    SideEffect { sync(container, fillWindowBackground) }
}
