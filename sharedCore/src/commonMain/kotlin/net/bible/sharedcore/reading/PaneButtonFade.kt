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

package net.bible.sharedcore.reading

/** Classic's pane-button fade animation length (`SplitBibleArea.toggleWindowButtonVisibility`'s default `animate()` duration). */
const val PANE_BUTTON_FADE_MILLIS: Int = 300

/**
 * The alpha a per-pane window button is drawn at once the idle timer has hidden it — the exact
 * truth table of classic `SplitBibleArea.toggleWindowButtonVisibility`
 * (`SplitBibleArea.kt:550-558`, constants at `:135`/`:1070-1072`):
 *
 * - shown: always `1f` (classic `VISIBLE_ALPHA`) — the caller picks `1f` directly, this function
 *   only answers "how faded is hidden".
 * - hidden, night mode: `0.5f` (`HIDDEN_ALPHA_NIGHT`)
 * - hidden, day mode: `0.2f` (`HIDDEN_ALPHA`)
 * - hidden with animations disabled OR monochrome mode: `1f` — classic guards the whole
 *   `alpha(hiddenAlpha)` call with `if (!disableAnimations && !monochromeMode)`, so the button
 *   simply stays fully opaque in those two configurations (an e-ink screen must not dim chrome, and
 *   "no animations" users keep it visible rather than getting an abrupt opacity step).
 */
fun paneButtonHiddenAlpha(nightMode: Boolean, disableAnimations: Boolean, monochrome: Boolean): Float = when {
    disableAnimations || monochrome -> 1f
    nightMode -> 0.5f
    else -> 0.2f
}

/** Classic sets `duration = 0` when animations are disabled (`SplitBibleArea.kt:562-564`). */
fun paneButtonFadeMillis(disableAnimations: Boolean): Int =
    if (disableAnimations) 0 else PANE_BUTTON_FADE_MILLIS
