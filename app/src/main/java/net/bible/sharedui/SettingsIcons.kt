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
package net.bible.sharedui

import androidx.annotation.DrawableRes

/**
 * Maps a [net.bible.sharedcore.settings.SettingsItem] `iconKey` to its classic drawable resource, if
 * any. Backs the `LocalSettingsIcon` seam ([ProvideAppLocals]) so moved Compose settings screens can
 * show the classic per-row leading icon. Unknown/unmapped keys return `null` — the row then renders
 * with no leading icon (same as an item with `iconKey == null`), never a crash or a placeholder box.
 *
 * No mappings yet (this task only builds the seam) — screens set their own `iconKey`s and add the
 * matching entries here starting with Batch B (F29).
 */
@DrawableRes
@Suppress("UNUSED_PARAMETER")
fun settingsDrawableRes(key: String): Int? = null
