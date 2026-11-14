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

import androidx.compose.material3.ColorScheme

/**
 * The platform's dynamic system colour scheme (Android 12+ "Material You", derived from the
 * wallpaper), or null where the platform has none (older Android, iOS, desktop).
 *
 * Set once by the host at startup. It is only consulted when the user turned on
 * "Use system colors" ([net.bible.sharedcore.platform.resolveUseSystemColors]); see [abColorScheme]
 * for where it ranks against the workspace colour and the display modes. Called on every scheme
 * derivation, so a provider that reads the current system palette follows wallpaper changes.
 */
@OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
@kotlin.native.HiddenFromObjC
var systemColorSchemeProvider: ((dark: Boolean) -> ColorScheme)? = null
