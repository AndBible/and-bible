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

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Host seam supplying an optional inheritance badge label per row key, keyed by
 * [net.bible.sharedcore.settings.SettingsItem.key].
 *
 * Mirrors [LocalSettingsIcon]: kept portable in commonMain and defaults to a lambda that always
 * returns `null` — the default means no badge for any row. Other settings screens (AppSettings,
 * ReadingProgress, Sync, AI, …) never provide this seam, so they stay byte-identical; only a screen
 * that genuinely has value-inheritance (e.g. the per-window/workspace/global TextDisplaySettings
 * editor) provides a non-default lambda, mapping a row's key to a short "Workspace"/"Global" label
 * when that row's effective value is inherited rather than set locally.
 */
val LocalSettingsRowBadge = staticCompositionLocalOf<(String) -> String?> { { null } }
