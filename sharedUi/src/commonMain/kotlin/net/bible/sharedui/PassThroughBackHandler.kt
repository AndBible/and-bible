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

import androidx.compose.runtime.Composable

/**
 * A back handler whose [onBack] may hand the press on to whatever would have handled it without this handler:
 * the next enabled callback below it (e.g. the NavHost's pop), else the Activity's own fallback. That is what an
 * Activity's `super.onBackPressed()` did before predictive back.
 *
 * [PlatformBackHandler] cannot do this: Compose's `BackHandler` applies `enabled` in a `SideEffect`, so turning it
 * off and dispatching again in the same call would re-enter the same handler. Spec
 * `2026-10-08-api36-predictive-back-and-calculator-rotation-design.md` §3.1.
 */
@Composable
expect fun PassThroughBackHandler(enabled: Boolean, onBack: (passThrough: () -> Unit) -> Unit)
