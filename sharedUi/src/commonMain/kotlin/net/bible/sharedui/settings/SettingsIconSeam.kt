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
package net.bible.sharedui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * Host seam supplying an optional leading icon for a [net.bible.sharedcore.settings.SettingsItem]
 * row, keyed by its `iconKey`.
 *
 * Kept portable in commonMain (NO Android `R.drawable`) so [AbSettingsScreen]/[AbSettingsContent]
 * compile on every target, mirroring [net.bible.sharedui.navigation.LocalCategoryIcon]. Unlike that
 * seam, this one is nullable-returning and defaults to a lambda that always returns `null`: most
 * settings rows have no icon (`iconKey == null`), and an unrecognised key should just render no icon
 * rather than erroring — there is no "must always be provided" requirement here. The Android host
 * provides the classic per-row drawables via `painterResource` — see `:app`'s `ProvideAppLocals` /
 * `SettingsIcons.kt`; iOS can supply its own art or leave the default (no icons).
 */
val LocalSettingsIcon = staticCompositionLocalOf<@Composable (String) -> Painter?> { { null } }
